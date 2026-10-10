"""FastAPI app implementing docs/API.md Part 1."""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Callable

from fastapi import FastAPI, Request, WebSocket
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse, StreamingResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.formparsers import MultiPartParser

from . import __version__
from . import backends as backend_factory
from .audio import SAMPLE_RATE, decode_audio, split_on_silence
from .auth import KeyStore, RateLimiter, UserCounter, bearer_token
from .chat import complete, parse_request, start_stream
from .config import Settings
from .errors import APIError, internal_error
from .live import close_with_error, run_session
from .manager import ModelManager
from .registry import ALL_MODEL_IDS, ATTRIBUTION, LLM_ID, resolve_asr

log = logging.getLogger("natlas")

MAX_AUDIO_BYTES = 25 * 1024 * 1024
ATTRIBUTION_HEADER = "X-NAtlas-Attribution"
USER_HEADER = "x-natlas-user"

# Keep uploads in memory (up to the 25 MB limit + margin) instead of spooling
# them to a temp file, so request audio never touches disk.
MultiPartParser.spool_max_size = MAX_AUDIO_BYTES + 1024 * 1024


def _error_response(err: APIError) -> JSONResponse:
    return JSONResponse(err.body(), status_code=err.status, headers=err.headers())


def create_app(settings: Settings | None = None, builder: Callable[[str, Settings], Any] | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    settings.validate()
    keys = KeyStore(settings.api_keys, settings.api_key_hashes)
    limiter = RateLimiter(settings.rate_limit_per_minute)
    users = UserCounter(settings.db_path, settings.user_cap)
    manager = ModelManager(settings, builder or backend_factory.build)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        for name, kind in (("LLM", settings.llm_backend), ("ASR", settings.asr_backend)):
            if kind == "fake":
                log.warning("%s backend is FAKE: responses are canned test output, not N-ATLAS.", name)
        manager.start_preload()
        yield

    app = FastAPI(title="N-ATLAS SDK test server", version=__version__, lifespan=lifespan,
                  docs_url="/docs", redoc_url=None)
    app.state.settings = settings
    app.state.manager = manager
    app.state.users = users

    # -- auth helper shared by HTTP and WS ---------------------------------
    def authorize(headers: Any) -> str:
        key_id = keys.verify(bearer_token(headers.get("authorization")))
        if key_id is None:
            raise APIError(
                401,
                "invalid_api_key",
                "Missing or invalid API key. Send 'Authorization: Bearer <key>' (apiKey in NAtlasConfig).",
            )
        limiter.check(key_id)
        uid = (headers.get(USER_HEADER) or "").strip()
        if uid:
            if len(uid) > 200:
                raise APIError(400, "invalid_request", "X-NAtlas-User is longer than 200 characters.")
            users.touch(uid)
        return key_id

    app.add_middleware(MetaMiddleware)

    # -- error handlers ------------------------------------------------------
    @app.exception_handler(APIError)
    async def _api_error(request: Request, exc: APIError) -> JSONResponse:
        return _error_response(exc)

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        return _error_response(APIError(400, "invalid_request", "The request is malformed."))

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = {404: "not_found", 405: "method_not_allowed"}.get(exc.status_code, "invalid_request")
        msg = {404: "No such endpoint. See docs/API.md.", 405: "Method not allowed on this endpoint."}.get(
            exc.status_code, str(exc.detail)
        )
        err = APIError(exc.status_code, code, msg)
        return _error_response(err)

    # -- endpoints -----------------------------------------------------------
    @app.get("/health")
    async def health() -> dict[str, Any]:
        status, models = manager.health()
        return {
            "status": status,
            "version": __version__,
            "models": models,
            "users": {"active_30d": users.active_count(), "cap": users.cap},
            "attribution": ATTRIBUTION,
        }

    @app.get("/v1/models")
    async def list_models(request: Request) -> dict[str, Any]:
        authorize(request.headers)
        return {
            "object": "list",
            "data": [
                {"id": mid, "object": "model", "created": 1758240000, "owned_by": "NCAIR1"}
                for mid in ALL_MODEL_IDS
                if mid in manager.slots
            ],
        }

    @app.post("/v1/chat/completions")
    async def chat_completions(request: Request) -> Any:
        authorize(request.headers)
        try:
            body = await request.json()
        except (json.JSONDecodeError, UnicodeDecodeError):
            raise APIError(400, "invalid_request", "The request body is not valid JSON.")
        messages, params, stream, include_usage = parse_request(body, settings.llm_max_tokens_cap)
        backend = await manager.get(LLM_ID)
        if not stream:
            return await complete(backend, messages, params)
        sse = await start_stream(backend, messages, params, include_usage)
        return StreamingResponse(
            sse,
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    @app.post("/v1/audio/transcriptions")
    async def transcriptions(request: Request) -> Any:
        authorize(request.headers)
        ctype = request.headers.get("content-type", "")
        if not ctype.startswith("multipart/form-data"):
            raise APIError(400, "invalid_request", "Send multipart/form-data with a 'file' field.")
        cl = request.headers.get("content-length")
        if cl and cl.isdigit() and int(cl) > MAX_AUDIO_BYTES + 64 * 1024:
            raise APIError(413, "audio_too_large", "The audio file is over 25 MB. Split it or compress it.")
        try:
            form = await request.form(max_files=1, max_fields=16)
        except Exception:
            raise APIError(400, "invalid_request", "Could not parse the multipart form.")
        try:
            upload = form.get("file")
            if upload is None or isinstance(upload, str):
                raise APIError(400, "invalid_request", "Missing the 'file' field.", param="file")
            language, model_id = resolve_asr(
                _str(form.get("language")), _str(form.get("model"))
            )
            fmt = (_str(form.get("response_format")) or "json").lower()
            if fmt not in ("json", "verbose_json", "text"):
                raise APIError(400, "invalid_request", "response_format must be json, verbose_json or text.",
                               param="response_format")
            temp_raw = _str(form.get("temperature"))
            try:
                temperature = float(temp_raw) if temp_raw else 0.0
            except ValueError:
                raise APIError(400, "invalid_request", "temperature must be a number.", param="temperature")
            if not 0.0 <= temperature <= 1.0:
                raise APIError(400, "invalid_request", "temperature must be between 0 and 1.", param="temperature")
            data = await upload.read()
            filename = upload.filename
        finally:
            await form.close()
        if len(data) > MAX_AUDIO_BYTES:
            raise APIError(413, "audio_too_large", "The audio file is over 25 MB. Split it or compress it.")

        audio = await asyncio.to_thread(decode_audio, data, filename)
        del data
        asr = await manager.get(model_id)
        segments = []
        try:
            for i, (a, b) in enumerate(split_on_silence(audio)):
                text = await asyncio.to_thread(asr.transcribe, audio[a:b], temperature)
                segments.append({"id": i, "start": round(a / SAMPLE_RATE, 2), "end": round(b / SAMPLE_RATE, 2),
                                 "text": text})
        except APIError:
            raise
        except Exception as e:
            log.error("asr backend error: %s", type(e).__name__)
            raise internal_error() from e
        text = " ".join(s["text"] for s in segments if s["text"]).strip()
        duration = round(audio.size / SAMPLE_RATE, 2)
        if fmt == "text":
            return PlainTextResponse(text)
        out: dict[str, Any] = {"text": text, "language": language, "duration": duration, "model": model_id}
        if fmt == "verbose_json":
            out = {"task": "transcribe", **out, "segments": segments}
        return out

    @app.websocket("/v1/audio/live")
    async def live(ws: WebSocket) -> None:
        await ws.accept(headers=[(ATTRIBUTION_HEADER.lower().encode(), ATTRIBUTION.encode())])
        try:
            key_id = authorize(ws.headers)
            _, model_id = resolve_asr(ws.query_params.get("language"), ws.query_params.get("model"))
            asr = await manager.get(model_id)
        except APIError as e:
            log.info("live rejected code=%s", e.code)
            await close_with_error(ws, e)
            return
        await run_session(ws, asr, model_id, key_id)

    return app


def _str(v: Any) -> str | None:
    return v if isinstance(v, str) else None


class MetaMiddleware:
    """Pure ASGI: adds X-NAtlas-Attribution and X-Request-Id to every HTTP
    response, turns uncaught errors into an OpenAI-shaped 500, and logs
    metadata only (method, path, status, time). Never bodies."""

    def __init__(self, app: Any) -> None:
        self.app = app

    async def __call__(self, scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        t0 = time.monotonic()
        rid = uuid.uuid4().hex[:12]
        state = {"status": 0, "started": False}
        extra = [(ATTRIBUTION_HEADER.lower().encode(), ATTRIBUTION.encode()), (b"x-request-id", rid.encode())]

        async def send_wrapper(message: dict[str, Any]) -> None:
            if message["type"] == "http.response.start":
                state["status"], state["started"] = message["status"], True
                message = {**message, "headers": [*message.get("headers", []), *extra]}
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        except Exception:
            log.exception("unhandled error rid=%s", rid)
            if not state["started"]:
                err = internal_error()
                await JSONResponse(err.body(), status_code=500)(scope, receive, send_wrapper)
        finally:
            ua = "-"
            for k, v in scope.get("headers", []):
                if k == b"user-agent":
                    ua = v.decode("latin-1")[:60]
            log.info("%s %s %d %.0fms rid=%s ua=%s", scope["method"], scope["path"], state["status"],
                     (time.monotonic() - t0) * 1000, rid, ua)
