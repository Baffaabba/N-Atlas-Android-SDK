"""Loads models in the background and hands them to request handlers.

- Preloading runs in a background thread so /health answers while weights
  load (status "loading").
- A request for a model that is not loaded yet starts loading it and waits
  up to NATLAS_LOAD_WAIT_SECONDS, then returns 503 model_loading with
  Retry-After.
- A failed load is reported in /health (status "degraded") and retried on
  the next request after a 60 s back-off.
"""

from __future__ import annotations

import asyncio
import gc
import logging
import threading
import time
from dataclasses import dataclass, field
from typing import Any, Callable

from .config import Settings
from .errors import APIError
from .registry import ASR_BY_LANGUAGE, LLM_ID

log = logging.getLogger("natlas.models")

RETRY_FAILED_AFTER = 60.0


@dataclass
class Slot:
    model_id: str
    backend_name: str
    state: str = "unloaded"  # unloaded | loading | loaded | failed
    obj: Any = None
    error: str | None = None
    failed_at: float = 0.0
    load_seconds: float | None = None
    done: threading.Event = field(default_factory=threading.Event)


class ModelManager:
    def __init__(self, settings: Settings, builder: Callable[[str, Settings], Any]) -> None:
        self.settings = settings
        self._builder = builder
        self._lock = threading.Lock()
        self.slots: dict[str, Slot] = {}
        if settings.llm_backend != "none":
            self.slots[LLM_ID] = Slot(LLM_ID, settings.llm_backend)
        if settings.asr_backend != "none":
            for lang in settings.asr_languages:
                mid = ASR_BY_LANGUAGE[lang]
                self.slots[mid] = Slot(mid, settings.asr_backend)
        self._preload_thread: threading.Thread | None = None

    # -- loading ----------------------------------------------------------
    def preload_ids(self) -> list[str]:
        p = [x.strip() for x in self.settings.preload]
        if "none" in p:
            return []
        if "all" in p:
            ids = list(self.slots)
        else:
            ids = []
            for x in p:
                mid = ASR_BY_LANGUAGE.get(x, x)
                if mid in self.slots and mid not in ids:
                    ids.append(mid)
        # ASR first: small and quick, so the server becomes useful sooner
        return sorted(ids, key=lambda m: m == LLM_ID)

    def start_preload(self) -> None:
        ids = self.preload_ids()
        if not ids:
            return
        for mid in ids:
            with self._lock:
                s = self.slots[mid]
                if s.state == "unloaded":
                    s.state = "loading"
                    s.done.clear()

        def run() -> None:
            for mid in ids:
                self._load(mid)

        self._preload_thread = threading.Thread(target=run, name="natlas-preload", daemon=True)
        self._preload_thread.start()

    def _load(self, model_id: str) -> None:
        s = self.slots[model_id]
        t0 = time.monotonic()
        try:
            obj = self._builder(model_id, self.settings)
        except Exception as e:  # keep the message, not a traceback, in /health
            log.exception("failed to load %s", model_id)
            with self._lock:
                s.state, s.obj, s.error, s.failed_at = "failed", None, f"{type(e).__name__}: {e}", time.time()
            s.done.set()
            gc.collect()
            return
        with self._lock:
            s.state, s.obj, s.error = "loaded", obj, None
            s.load_seconds = round(time.monotonic() - t0, 1)
        log.info("loaded %s in %.1f s", model_id, s.load_seconds)
        s.done.set()

    def _begin_load_if_needed(self, s: Slot) -> None:
        with self._lock:
            if s.state == "unloaded" or (s.state == "failed" and time.time() - s.failed_at > RETRY_FAILED_AFTER):
                s.state = "loading"
                s.done.clear()
                threading.Thread(target=self._load, args=(s.model_id,), name="natlas-load", daemon=True).start()

    async def get(self, model_id: str) -> Any:
        s = self.slots.get(model_id)
        if s is None:
            raise APIError(
                400,
                "invalid_request",
                f"Model {model_id} is not enabled on this server. Enabled: {', '.join(self.slots) or 'none'}.",
                param="model",
            )
        if s.state == "loaded":
            return s.obj
        self._begin_load_if_needed(s)
        if s.state == "loading":
            await asyncio.to_thread(s.done.wait, self.settings.load_wait_seconds)
        if s.state == "loaded":
            return s.obj
        if s.state == "loading":
            raise APIError(
                503, "model_loading", f"{model_id} is still loading. Retry shortly.", retry_after=20
            )
        raise APIError(
            503,
            "backend_unavailable",
            f"{model_id} failed to load on this server ({s.error}). The operator must fix it; see /health.",
            retry_after=int(RETRY_FAILED_AFTER),
        )

    # -- reporting --------------------------------------------------------
    def health(self) -> tuple[str, dict[str, Any]]:
        models: dict[str, Any] = {}
        any_loading = any_failed = False
        for mid, s in self.slots.items():
            entry: dict[str, Any] = {"loaded": s.state == "loaded", "state": s.state, "backend": s.backend_name}
            if s.obj is not None:
                entry.update(getattr(s.obj, "info", {}))
            if s.load_seconds is not None:
                entry["load_seconds"] = s.load_seconds
            if s.error:
                entry["error"] = s.error
            any_loading |= s.state == "loading"
            any_failed |= s.state == "failed"
            models[mid] = entry
        status = "degraded" if any_failed else ("loading" if any_loading else "ok")
        return status, models
