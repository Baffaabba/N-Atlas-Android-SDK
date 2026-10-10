"""API keys (hashed), per-key rate limit, and the licence's active-user counter."""

from __future__ import annotations

import hashlib
import hmac
import sqlite3
import threading
import time
from collections import deque

from .errors import APIError

ACTIVE_WINDOW_SECONDS = 30 * 24 * 3600


def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


class KeyStore:
    """Holds only SHA-256 hashes of the keys. Revoke a key by removing it from config."""

    def __init__(self, plain_keys: list[str], key_hashes: list[str]) -> None:
        self._hashes = [sha256_hex(k) for k in plain_keys] + [h.lower() for h in key_hashes]

    def verify(self, presented: str | None) -> str | None:
        """Return a short key id (hash prefix, safe to log) or None."""
        if not presented:
            return None
        h = sha256_hex(presented)
        ok = False
        for known in self._hashes:
            # compare every entry so timing does not reveal the position
            ok |= hmac.compare_digest(h, known)
        return h[:8] if ok else None


def bearer_token(authorization: str | None) -> str | None:
    if not authorization:
        return None
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer":
        return None
    return token.strip() or None


class RateLimiter:
    """Sliding 60 s window per key. 0 disables."""

    def __init__(self, per_minute: int) -> None:
        self.per_minute = per_minute
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def check(self, key_id: str) -> None:
        if self.per_minute <= 0:
            return
        now = time.monotonic()
        with self._lock:
            q = self._hits.setdefault(key_id, deque())
            while q and now - q[0] >= 60:
                q.popleft()
            if len(q) >= self.per_minute:
                retry = max(1, int(60 - (now - q[0])) + 1)
                raise APIError(
                    429,
                    "rate_limited",
                    f"Too many requests for this API key (limit {self.per_minute} a minute). Retry after {retry} s.",
                    retry_after=retry,
                )
            q.append(now)


class UserCounter:
    """Counts distinct X-NAtlas-User ids seen in a rolling 30 days.

    Stores only a SHA-256 of each id plus first/last-seen times, in SQLite so
    the count survives restarts. Thread-safe.
    """

    def __init__(self, db_path: str, cap: int) -> None:
        self.cap = cap
        self._lock = threading.Lock()
        self._db = sqlite3.connect(db_path, check_same_thread=False)
        self._db.execute(
            "CREATE TABLE IF NOT EXISTS active_users (uid TEXT PRIMARY KEY, first_seen REAL, last_seen REAL)"
        )
        self._db.commit()
        self._last_purge = 0.0

    def _purge(self, now: float) -> None:
        if now - self._last_purge > 3600:
            self._db.execute("DELETE FROM active_users WHERE last_seen < ?", (now - ACTIVE_WINDOW_SECONDS,))
            self._last_purge = now

    def active_count(self, now: float | None = None) -> int:
        now = time.time() if now is None else now
        with self._lock:
            (n,) = self._db.execute(
                "SELECT COUNT(*) FROM active_users WHERE last_seen >= ?", (now - ACTIVE_WINDOW_SECONDS,)
            ).fetchone()
            return int(n)

    def touch(self, user_id: str, now: float | None = None) -> int:
        """Record activity for user_id. Raises 403 if a new user would pass the cap.

        Returns the active count after the call.
        """
        now = time.time() if now is None else now
        uid = sha256_hex(user_id)
        cutoff = now - ACTIVE_WINDOW_SECONDS
        with self._lock:
            self._purge(now)
            row = self._db.execute("SELECT last_seen FROM active_users WHERE uid = ?", (uid,)).fetchone()
            (n,) = self._db.execute("SELECT COUNT(*) FROM active_users WHERE last_seen >= ?", (cutoff,)).fetchone()
            is_active = row is not None and row[0] >= cutoff
            if not is_active and n >= self.cap:
                raise APIError(
                    403,
                    "user_cap_reached",
                    f"This server reached the N-ATLAS licence cap of {self.cap} active users in 30 days. "
                    "Existing users keep working; new users need a commercial licence from Awarri "
                    "(datasupport@awarri.com).",
                )
            self._db.execute(
                "INSERT INTO active_users (uid, first_seen, last_seen) VALUES (?, ?, ?) "
                "ON CONFLICT(uid) DO UPDATE SET last_seen = excluded.last_seen",
                (uid, now, now),
            )
            self._db.commit()
            return int(n) + (0 if is_active else 1)
