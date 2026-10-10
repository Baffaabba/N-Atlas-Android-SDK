"""The small interface every model backend implements.

Backends are synchronous and may block; the app calls them from a thread
pool. A backend serves exactly one NCAIR1 model id.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass, field
from typing import Any, Iterator, Protocol, Union

import numpy as np


@dataclass
class ChatParams:
    temperature: float = 0.1
    top_p: float | None = None
    max_tokens: int = 512
    stop: list[str] = field(default_factory=list)
    repetition_penalty: float = 1.12


@dataclass
class StreamEnd:
    """Last item of LLMBackend.stream()."""

    finish_reason: str  # "stop" | "length"
    prompt_tokens: int
    completion_tokens: int


StreamItem = Union[str, StreamEnd]


class ContextTooLong(Exception):
    """The prompt plus max_tokens does not fit the server's context limit."""


class LLMBackend(Protocol):
    model_id: str
    info: dict[str, Any]  # shown in /health, e.g. {"backend": "transformers", "quantization": "4bit-nf4"}

    def stream(
        self, messages: list[dict[str, str]], params: ChatParams, cancel: threading.Event
    ) -> Iterator[StreamItem]:
        """Yield text pieces, then exactly one StreamEnd.

        Must raise ContextTooLong (or another error) before yielding anything
        if the request can't run. Must stop soon after `cancel` is set.
        """
        ...


class ASRBackend(Protocol):
    model_id: str
    language: str
    info: dict[str, Any]

    def transcribe(self, audio: np.ndarray, temperature: float = 0.0) -> str:
        """Transcribe at most 30 s of 16 kHz mono float32 audio."""
        ...
