"""Deterministic fake backends for tests and for running the server locally
on a laptop without models. Responses are clearly labelled "[fake]".

Magic chat inputs, for SDK error-path testing:
  "__fake_error__"            -> the stream fails after two pieces
  "__fake_long__"             -> a 200-word reply (to test max_tokens / length)
"""

from __future__ import annotations

import threading
import time
from typing import Any, Iterator

import numpy as np

from ..audio import SAMPLE_RATE
from .base import ChatParams, StreamEnd, StreamItem


class FakeStreamError(RuntimeError):
    pass


def _last_user(messages: list[dict[str, str]]) -> str:
    for m in reversed(messages):
        if m["role"] == "user":
            return m["content"]
    return ""


class FakeLLM:
    def __init__(self, model_id: str, token_delay_ms: int = 30) -> None:
        self.model_id = model_id
        self.token_delay = max(0, token_delay_ms) / 1000.0
        self.info: dict[str, Any] = {"backend": "fake", "quantization": "none"}

    def stream(self, messages: list[dict[str, str]], params: ChatParams, cancel: threading.Event) -> Iterator[StreamItem]:
        user = _last_user(messages)
        prompt_tokens = sum(len(m["content"].split()) + 4 for m in messages)
        if user.strip() == "__fake_long__":
            reply = " ".join(f"word{i}" for i in range(200))
        else:
            reply = f"[fake N-ATLaS] You said: {user}"
        words = reply.split(" ")
        pieces = [w if i == 0 else " " + w for i, w in enumerate(words)]
        n = 0
        for piece in pieces:
            if cancel.is_set():
                break
            if n >= params.max_tokens:
                yield StreamEnd("length", prompt_tokens, n)
                return
            if user.strip() == "__fake_error__" and n == 2:
                raise FakeStreamError("simulated backend failure")
            if self.token_delay:
                time.sleep(self.token_delay)
            n += 1
            yield piece
        yield StreamEnd("stop", prompt_tokens, n)


class FakeASR:
    def __init__(self, model_id: str, language: str) -> None:
        self.model_id = model_id
        self.language = language
        self.info: dict[str, Any] = {"backend": "fake"}

    def transcribe(self, audio: np.ndarray, temperature: float = 0.0) -> str:
        if audio.size == 0 or float(np.max(np.abs(audio))) < 1e-3:
            return ""
        return f"[fake {self.language}] {audio.size / SAMPLE_RATE:.1f} seconds of audio"
