"""POST /v1/chat/completions: OpenAI request/response shapes and SSE."""

from __future__ import annotations

import json
import logging
import threading
import time
import uuid
from typing import Any, AsyncIterator, Iterator

from starlette.concurrency import iterate_in_threadpool, run_in_threadpool

from .backends.base import ChatParams, ContextTooLong, StreamEnd
from .errors import APIError, internal_error
from .registry import LLM_ID

log = logging.getLogger("natlas.chat")

ROLES = ("system", "user", "assistant")


# -- request parsing --------------------------------------------------------
def _bad(msg: str, param: str | None = None) -> APIError:
    return APIError(400, "invalid_request", msg, param=param)


def _content_text(content: Any, i: int) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):  # OpenAI content parts; only text is supported
        texts = []
        for part in content:
            if isinstance(part, dict) and part.get("type") == "text" and isinstance(part.get("text"), str):
                texts.append(part["text"])
            else:
                raise _bad(f"messages[{i}].content: only text parts are supported.", "messages")
        return "".join(texts)
    raise _bad(f"messages[{i}].content must be a string.", "messages")


def _num(body: dict[str, Any], name: str, default: Any, lo: float, hi: float, integer: bool = False) -> Any:
    v = body.get(name)
    if v is None:
        return default
    if isinstance(v, bool) or not isinstance(v, (int, float)) or (integer and not float(v).is_integer()):
        raise _bad(f"'{name}' must be a {'whole ' if integer else ''}number.", name)
    if not lo <= v <= hi:
        raise _bad(f"'{name}' must be between {lo:g} and {hi:g}.", name)
    return int(v) if integer else float(v)


def parse_request(body: Any, max_tokens_cap: int) -> tuple[list[dict[str, str]], ChatParams, bool, bool]:
    """Return (messages, params, stream, include_usage)."""
    if not isinstance(body, dict):
        raise _bad("The request body must be a JSON object.")
    model = body.get("model") or LLM_ID
    if model != LLM_ID:
        raise _bad(f"Unknown chat model '{model}'. This server serves only {LLM_ID}.", "model")
    raw = body.get("messages")
    if not isinstance(raw, list) or not raw:
        raise _bad("'messages' must be a non-empty list.", "messages")
    messages: list[dict[str, str]] = []
    for i, m in enumerate(raw):
        if not isinstance(m, dict):
            raise _bad(f"messages[{i}] must be an object with 'role' and 'content'.", "messages")
        role = m.get("role")
        if role not in ROLES:
            raise _bad(f"messages[{i}].role must be one of {', '.join(ROLES)}.", "messages")
        messages.append({"role": role, "content": _content_text(m.get("content"), i)})
    if body.get("n") not in (None, 1):
        raise _bad("Only n=1 is supported.", "n")

    stop = body.get("stop")
    if stop is None:
        stops: list[str] = []
    elif isinstance(stop, str):
        stops = [stop]
    elif isinstance(stop, list) and all(isinstance(s, str) for s in stop) and len(stop) <= 4:
        stops = list(stop)
    else:
        raise _bad("'stop' must be a string or a list of up to 4 strings.", "stop")
    stops = [s for s in stops if s]

    max_tokens = body.get("max_tokens", body.get("max_completion_tokens"))
    params = ChatParams(
        temperature=_num(body, "temperature", 0.1, 0.0, 2.0),
        top_p=_num(body, "top_p", None, 0.0, 1.0),
        max_tokens=_num({"max_tokens": max_tokens}, "max_tokens", 512, 1, max_tokens_cap, integer=True),
        stop=stops,
        repetition_penalty=_num(body, "repetition_penalty", 1.12, 0.5, 2.0),
    )
    stream = body.get("stream", False)
    if not isinstance(stream, bool):
        raise _bad("'stream' must be true or false.", "stream")
    opts = body.get("stream_options") or {}
    include_usage = bool(isinstance(opts, dict) and opts.get("include_usage"))
    return messages, params, stream, include_usage


# -- stop sequences (applied here so every backend behaves the same) --------
class StopTrimmer:
    """Cuts output at the first stop string. Holds back text that could be
    the start of a stop string until it is decided."""

    def __init__(self, stops: list[str]) -> None:
        self.stops = stops
        self.buf = ""
        self.stopped = False
        self.hold = max((len(s) for s in stops), default=1) - 1

    def push(self, piece: str) -> str:
        if self.stopped:
            return ""
        if not self.stops:
            return piece
        self.buf += piece
        hits = [i for i in (self.buf.find(s) for s in self.stops) if i >= 0]
        if hits:
            out, self.buf, self.stopped = self.buf[: min(hits)], "", True
            return out
        if len(self.buf) > self.hold:
            cut = len(self.buf) - self.hold
            out, self.buf = self.buf[:cut], self.buf[cut:]
            return out
        return ""

    def flush(self) -> str:
        out, self.buf = ("" if self.stopped else self.buf), ""
        return out


# -- running ----------------------------------------------------------------
def _pieces(backend: Any, messages: list[dict[str, str]], params: ChatParams, cancel: threading.Event) -> Iterator[Any]:
    """Backend stream with stop trimming; ends with one StreamEnd."""
    trim = StopTrimmer(params.stop)
    for item in backend.stream(messages, params, cancel):
        if isinstance(item, StreamEnd):
            tail = trim.flush()
            if tail:
                yield tail
            if trim.stopped:
                item.finish_reason = "stop"
            yield item
            return
        out = trim.push(item)
        if out:
            yield out
        if trim.stopped:
            cancel.set()
    raise RuntimeError("backend stream ended without StreamEnd")


def _map_backend_error(e: BaseException) -> APIError:
    if isinstance(e, APIError):
        return e
    if isinstance(e, ContextTooLong):
        return APIError(400, "invalid_request", str(e), param="messages")
    log.error("chat backend error: %s", type(e).__name__)
    return internal_error()


def _usage(end: StreamEnd) -> dict[str, int]:
    return {
        "prompt_tokens": end.prompt_tokens,
        "completion_tokens": end.completion_tokens,
        "total_tokens": end.prompt_tokens + end.completion_tokens,
    }


async def complete(backend: Any, messages: list[dict[str, str]], params: ChatParams) -> dict[str, Any]:
    cancel = threading.Event()

    def run() -> tuple[str, StreamEnd]:
        text, end = [], None
        for item in _pieces(backend, messages, params, cancel):
            if isinstance(item, StreamEnd):
                end = item
            else:
                text.append(item)
        assert end is not None
        return "".join(text), end

    try:
        text, end = await run_in_threadpool(run)
    except BaseException as e:
        cancel.set()
        raise _map_backend_error(e) from e
    return {
        "id": f"chatcmpl-{uuid.uuid4().hex}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": LLM_ID,
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": text},
                "finish_reason": end.finish_reason,
            }
        ],
        "usage": _usage(end),
    }


def _sse(obj: Any) -> str:
    return f"data: {json.dumps(obj, ensure_ascii=False)}\n\n"


async def start_stream(
    backend: Any, messages: list[dict[str, str]], params: ChatParams, include_usage: bool
) -> AsyncIterator[str]:
    """Prime the backend so errors before the first token become normal HTTP
    errors, then return the SSE body iterator."""
    cancel = threading.Event()
    it = iterate_in_threadpool(_pieces(backend, messages, params, cancel))
    try:
        first = await it.__anext__()
    except StopAsyncIteration:
        raise internal_error()
    except BaseException as e:
        cancel.set()
        raise _map_backend_error(e) from e

    cid = f"chatcmpl-{uuid.uuid4().hex}"
    created = int(time.time())

    def chunk(delta: dict[str, Any], finish: str | None) -> dict[str, Any]:
        return {
            "id": cid,
            "object": "chat.completion.chunk",
            "created": created,
            "model": LLM_ID,
            "choices": [{"index": 0, "delta": delta, "finish_reason": finish}],
        }

    async def body() -> AsyncIterator[str]:
        try:
            yield _sse(chunk({"role": "assistant", "content": ""}, None))
            item = first
            while True:
                if isinstance(item, StreamEnd):
                    yield _sse(chunk({}, item.finish_reason))
                    if include_usage:
                        yield _sse({"id": cid, "object": "chat.completion.chunk", "created": created,
                                    "model": LLM_ID, "choices": [], "usage": _usage(item)})
                    break
                yield _sse(chunk({"content": item}, None))
                try:
                    item = await it.__anext__()
                except StopAsyncIteration:
                    break
            yield "data: [DONE]\n\n"
        except Exception as e:  # error after the stream started
            err = _map_backend_error(e)
            yield _sse(err.body())
            yield "data: [DONE]\n\n"
        finally:
            cancel.set()  # client went away or we finished: stop the backend

    return body()
