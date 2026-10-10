from __future__ import annotations

import io
import json
import wave
from typing import Any, Callable

import numpy as np
import pytest
from fastapi.testclient import TestClient

from natlas_server.app import create_app
from natlas_server.config import Settings

KEY = "test-key-123"
AUTH = {"Authorization": f"Bearer {KEY}"}


def make_settings(tmp_path: Any, **overrides: Any) -> Settings:
    s = Settings(
        api_keys=[KEY],
        db_path=str(tmp_path / "users.sqlite3"),
        llm_backend="fake",
        asr_backend="fake",
        fake_token_delay_ms=0,
        rate_limit_per_minute=0,
        load_wait_seconds=5,
    )
    for k, v in overrides.items():
        setattr(s, k, v)
    return s


@pytest.fixture
def make_client(tmp_path: Any) -> Callable[..., TestClient]:
    clients: list[TestClient] = []

    def _make(builder: Any = None, **overrides: Any) -> TestClient:
        c = TestClient(create_app(make_settings(tmp_path, **overrides), builder=builder))
        c.__enter__()
        clients.append(c)
        return c

    yield _make
    for c in clients:
        c.__exit__(None, None, None)


@pytest.fixture
def client(make_client: Callable[..., TestClient]) -> TestClient:
    return make_client()


def tone(seconds: float, amp: float = 0.3, freq: float = 220.0, sr: int = 16000) -> np.ndarray:
    t = np.arange(int(seconds * sr)) / sr
    return (amp * np.sin(2 * np.pi * freq * t)).astype(np.float32)


def silence(seconds: float, sr: int = 16000) -> np.ndarray:
    return np.zeros(int(seconds * sr), dtype=np.float32)


def pcm16(x: np.ndarray) -> bytes:
    return (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()


def wav_bytes(x: np.ndarray, sr: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm16(x))
    return buf.getvalue()


def sse_events(text: str) -> list[Any]:
    """Parse an SSE body into a list of decoded data payloads ('[DONE]' kept as str)."""
    events = []
    assert text.endswith("\n\n"), "every SSE event ends with a blank line"
    for block in text.split("\n\n"):
        if not block:
            continue
        assert block.startswith("data: "), block
        data = block[len("data: "):]
        events.append(data if data == "[DONE]" else json.loads(data))
    return events
