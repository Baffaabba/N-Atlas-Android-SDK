"""WS /v1/audio/live: near-real-time chunked transcription.

Mechanism (stated honestly in docs/API.md): buffer PCM16, segment
utterances with energy VAD, re-transcribe the open utterance about once a
second (partial), and transcribe it once more when ~0.8 s of silence or 30 s
is reached (final). Whisper is not a streaming model.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import Any

from starlette.websockets import WebSocket, WebSocketDisconnect, WebSocketState

from .audio import SAMPLE_RATE, pcm16_to_float
from .errors import APIError, internal_error
from .vad import Utterance, UtteranceSegmenter

log = logging.getLogger("natlas.live")

PARTIAL_INTERVAL_S = 1.0
MIN_SPEECH_S = 0.25  # shorter blips with no partial yet are dropped as noise


class LiveSession:
    def __init__(self, ws: WebSocket, asr: Any, model_id: str, key_id: str) -> None:
        self.ws = ws
        self.asr = asr
        self.model_id = model_id
        self.key_id = key_id
        self.seg = UtteranceSegmenter()
        self.queue: asyncio.Queue[tuple[str, Any]] = asyncio.Queue()
        self._odd = b""
        self._partial_sent_for_current = False
        self._last_partial_end = 0.0
        self.finals = 0
        self.partials = 0

    async def send(self, obj: dict[str, Any]) -> None:
        await self.ws.send_text(json.dumps(obj, ensure_ascii=False))

    async def _transcribe(self, u: Utterance) -> str:
        return await asyncio.to_thread(self.asr.transcribe, u.audio)

    async def _receiver(self) -> None:
        try:
            while True:
                msg = await self.ws.receive()
                if msg["type"] == "websocket.disconnect":
                    await self.queue.put(("disconnect", None))
                    return
                if msg.get("bytes") is not None:
                    await self.queue.put(("audio", msg["bytes"]))
                elif msg.get("text") is not None:
                    try:
                        data = json.loads(msg["text"])
                    except ValueError:
                        data = None
                    if isinstance(data, dict) and data.get("type") == "stop":
                        await self.queue.put(("stop", None))
                        return
                    await self.queue.put(("bad_text", msg["text"][:80]))
                    return
        except (WebSocketDisconnect, RuntimeError):
            await self.queue.put(("disconnect", None))

    def _pcm(self, data: bytes) -> Any:
        data = self._odd + data
        if len(data) % 2:
            self._odd, data = data[-1:], data[:-1]
        else:
            self._odd = b""
        return pcm16_to_float(data)

    async def _emit_final(self, u: Utterance) -> None:
        if u.speech_seconds < MIN_SPEECH_S and not self._partial_sent_for_current:
            self._reset_partial()
            return
        text = await self._transcribe(u)
        await self.send({"type": "final", "text": text, "start": u.start, "end": u.end})
        self.finals += 1
        self._reset_partial()

    def _reset_partial(self) -> None:
        self._partial_sent_for_current = False
        self._last_partial_end = 0.0

    async def run(self) -> None:
        """Returns normally after done; raises APIError on fatal problems."""
        await self.send({"type": "ready", "model": self.model_id, "sample_rate": SAMPLE_RATE})
        recv = asyncio.create_task(self._receiver())
        try:
            while True:
                items = [await self.queue.get()]
                while not self.queue.empty():
                    items.append(self.queue.get_nowait())
                stopping = False
                for kind, payload in items:
                    if kind == "disconnect":
                        return
                    if kind == "bad_text":
                        raise APIError(
                            400,
                            "invalid_request",
                            'Text frames must be {"type": "stop"}. Send audio as binary PCM16 frames.',
                        )
                    if kind == "stop":
                        stopping = True
                        continue
                    for u in self.seg.feed(self._pcm(payload)):
                        await self._emit_final(u)
                if stopping:
                    u = self.seg.flush()
                    if u is not None:
                        await self._emit_final(u)
                    await self.send({"type": "done"})
                    return
                cur = self.seg.current()
                if (
                    cur is not None
                    and self.queue.empty()
                    and cur.end - max(cur.start, self._last_partial_end) >= PARTIAL_INTERVAL_S
                    and cur.speech_seconds >= MIN_SPEECH_S
                ):
                    text = await self._transcribe(cur)
                    # the utterance may have closed meanwhile; partial still describes cur
                    await self.send({"type": "partial", "text": text, "start": cur.start, "end": cur.end})
                    self.partials += 1
                    self._partial_sent_for_current = True
                    self._last_partial_end = cur.end
        finally:
            recv.cancel()


async def close_with_error(ws: WebSocket, err: APIError) -> None:
    if ws.application_state != WebSocketState.CONNECTED:
        return
    try:
        await ws.send_text(json.dumps({"type": "error", **err.body()}))
        await ws.close(code=4000 + err.status if 400 <= err.status < 600 else 1011, reason=err.code)
    except Exception:
        pass


async def run_session(ws: WebSocket, asr: Any, model_id: str, key_id: str) -> None:
    session = LiveSession(ws, asr, model_id, key_id)
    t0 = time.monotonic()
    outcome = "done"
    try:
        await session.run()
        if ws.application_state == WebSocketState.CONNECTED:
            await ws.close(code=1000)
    except APIError as e:
        outcome = e.code
        await close_with_error(ws, e)
    except (WebSocketDisconnect, ConnectionError):
        outcome = "disconnect"
    except Exception:
        outcome = "internal_error"
        log.exception("live session failed")
        await close_with_error(ws, internal_error())
    finally:
        # metadata only: no audio, no text
        log.info(
            "live key=%s model=%s audio_s=%.1f partials=%d finals=%d wall_s=%.1f outcome=%s",
            key_id, model_id, session.seg.stream_seconds, session.partials, session.finals,
            time.monotonic() - t0, outcome,
        )
