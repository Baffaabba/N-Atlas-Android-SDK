from __future__ import annotations

import threading
import time

import pytest

from natlas_server.auth import sha256_hex
from natlas_server.registry import ATTRIBUTION, LLM_ID

from .conftest import AUTH, KEY, silence, sse_events, tone, wav_bytes


def assert_error(r, status: int, code: str) -> dict:
    assert r.status_code == status, r.text
    body = r.json()
    assert set(body) == {"error"}
    err = body["error"]
    assert err["code"] == code
    assert isinstance(err["message"], str) and err["message"]
    assert isinstance(err["type"], str)
    assert r.headers["x-natlas-attribution"] == ATTRIBUTION
    return err


# -- health / models / headers ----------------------------------------------
def test_health_no_auth_and_shape(client):
    r = client.get("/health")
    assert r.status_code == 200
    h = r.json()
    assert h["status"] in ("ok", "loading")
    assert h["version"] == "0.1.0"
    assert h["attribution"] == ATTRIBUTION
    assert set(h["models"]) == {LLM_ID, "NCAIR1/Hausa-ASR", "NCAIR1/Igbo-ASR", "NCAIR1/Yoruba-ASR",
                                "NCAIR1/NigerianAccentedEnglish"}
    assert h["users"] == {"active_30d": 0, "cap": 1000}
    assert r.headers["x-natlas-attribution"] == ATTRIBUTION


def test_health_becomes_ok_after_preload(client):
    for _ in range(50):
        h = client.get("/health").json()
        if h["status"] == "ok":
            break
        time.sleep(0.05)
    assert h["status"] == "ok"
    assert all(m["loaded"] and m["backend"] == "fake" for m in h["models"].values())


def test_models_list(client):
    r = client.get("/v1/models", headers=AUTH)
    assert r.status_code == 200
    body = r.json()
    assert body["object"] == "list"
    ids = [m["id"] for m in body["data"]]
    assert ids[0] == LLM_ID and len(ids) == 5
    assert all(m["object"] == "model" and m["owned_by"] == "NCAIR1" for m in body["data"])


def test_unknown_route_is_openai_shaped(client):
    assert_error(client.get("/v1/nope", headers=AUTH), 404, "not_found")


# -- auth ---------------------------------------------------------------------
@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer wrong"}, {"Authorization": KEY},
                                     {"Authorization": "Basic " + KEY}])
def test_auth_rejected(client, headers):
    err = assert_error(client.get("/v1/models", headers=headers), 401, "invalid_api_key")
    assert err["type"] == "authentication_error"


def test_auth_with_hashed_key(make_client):
    c = make_client(api_keys=[], api_key_hashes=[sha256_hex("hashed-secret")])
    assert c.get("/v1/models", headers={"Authorization": "Bearer hashed-secret"}).status_code == 200
    assert c.get("/v1/models", headers=AUTH).status_code == 401


def test_rate_limit_429_with_retry_after(make_client):
    c = make_client(rate_limit_per_minute=2)
    assert c.get("/v1/models", headers=AUTH).status_code == 200
    assert c.get("/v1/models", headers=AUTH).status_code == 200
    r = c.get("/v1/models", headers=AUTH)
    assert_error(r, 429, "rate_limited")
    assert int(r.headers["retry-after"]) >= 1


# -- user cap -------------------------------------------------------------------
def test_user_cap(make_client):
    c = make_client(user_cap=2)
    ok = lambda u: c.get("/v1/models", headers={**AUTH, "X-NAtlas-User": u})  # noqa: E731
    assert ok("user-a").status_code == 200
    assert ok("user-b").status_code == 200
    assert_error(ok("user-c"), 403, "user_cap_reached")
    assert ok("user-a").status_code == 200  # existing users keep working
    assert c.get("/v1/models", headers=AUTH).status_code == 200  # no header: not counted
    assert c.get("/health").json()["users"] == {"active_30d": 2, "cap": 2}


def test_user_counter_rolling_window(tmp_path):
    from natlas_server.auth import ACTIVE_WINDOW_SECONDS, UserCounter

    uc = UserCounter(str(tmp_path / "u.db"), cap=1)
    now = 1_800_000_000.0
    uc.touch("old", now=now - ACTIVE_WINDOW_SECONDS - 10)
    assert uc.active_count(now=now) == 0
    assert uc.touch("new", now=now) == 1  # old user aged out, so the cap has room
    from natlas_server.errors import APIError

    with pytest.raises(APIError) as e:
        uc.touch("another", now=now)
    assert e.value.code == "user_cap_reached"


def test_user_count_survives_restart(make_client, tmp_path):
    c = make_client()
    c.get("/v1/models", headers={**AUTH, "X-NAtlas-User": "persist"})
    c2 = make_client()  # same db path
    assert c2.get("/health").json()["users"]["active_30d"] == 1


# -- chat ---------------------------------------------------------------------
def chat(c, **body):
    payload = {"messages": [{"role": "user", "content": "Sannu! Yaya kake?"}], **body}
    return c.post("/v1/chat/completions", headers=AUTH, json=payload)


def test_chat_non_streaming(client):
    r = chat(client)
    assert r.status_code == 200, r.text
    b = r.json()
    assert b["object"] == "chat.completion"
    assert b["model"] == LLM_ID
    assert b["id"].startswith("chatcmpl-")
    ch = b["choices"][0]
    assert ch["message"]["role"] == "assistant"
    assert ch["message"]["content"] == "[fake N-ATLaS] You said: Sannu! Yaya kake?"
    assert ch["finish_reason"] == "stop"
    u = b["usage"]
    assert u["total_tokens"] == u["prompt_tokens"] + u["completion_tokens"] > 0


def test_chat_content_parts_accepted(client):
    r = client.post("/v1/chat/completions", headers=AUTH, json={
        "messages": [{"role": "user", "content": [{"type": "text", "text": "hi"}]}]})
    assert r.json()["choices"][0]["message"]["content"].endswith("You said: hi")


def test_chat_streaming_sse_format(client):
    r = chat(client, stream=True)
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("text/event-stream")
    assert r.headers["x-natlas-attribution"] == ATTRIBUTION
    ev = sse_events(r.text)
    assert ev[-1] == "[DONE]"
    chunks = ev[:-1]
    assert chunks[0]["choices"][0]["delta"]["role"] == "assistant"
    assert all(c["object"] == "chat.completion.chunk" for c in chunks)
    assert len({c["id"] for c in chunks}) == 1
    assert chunks[-1]["choices"][0]["finish_reason"] == "stop"
    assert all(c["choices"][0]["finish_reason"] is None for c in chunks[:-1])
    text = "".join(c["choices"][0]["delta"].get("content", "") for c in chunks)
    assert text == "[fake N-ATLaS] You said: Sannu! Yaya kake?"
    assert len(chunks) > 3  # really streamed piece by piece


def test_chat_stream_include_usage(client):
    ev = sse_events(chat(client, stream=True, stream_options={"include_usage": True}).text)
    usage_chunk = ev[-2]
    assert usage_chunk["choices"] == [] and usage_chunk["usage"]["total_tokens"] > 0


def test_chat_max_tokens_length(client):
    b = chat(client, max_tokens=3).json()
    assert b["choices"][0]["finish_reason"] == "length"
    assert b["usage"]["completion_tokens"] == 3
    ev = sse_events(chat(client, max_tokens=3, stream=True).text)
    assert ev[-2]["choices"][0]["finish_reason"] == "length"


def test_chat_stop_sequence(client):
    b = chat(client, stop=["Yaya"]).json()
    assert b["choices"][0]["message"]["content"] == "[fake N-ATLaS] You said: Sannu! "
    assert b["choices"][0]["finish_reason"] == "stop"
    ev = sse_events(chat(client, stop="Yaya", stream=True).text)
    text = "".join(c["choices"][0]["delta"].get("content", "") for c in ev[:-1])
    assert text == "[fake N-ATLaS] You said: Sannu! "


def test_chat_error_mid_stream(client):
    r = client.post("/v1/chat/completions", headers=AUTH, json={
        "stream": True, "messages": [{"role": "user", "content": "__fake_error__"}]})
    assert r.status_code == 200
    ev = sse_events(r.text)
    assert ev[-1] == "[DONE]"
    assert ev[-2]["error"]["code"] == "internal_error"
    assert "simulated" not in ev[-2]["error"]["message"]  # nothing internal leaks


@pytest.mark.parametrize("body,param", [
    ({"model": "gpt-4o", "messages": [{"role": "user", "content": "x"}]}, "model"),
    ({"messages": []}, "messages"),
    ({"messages": [{"role": "tool", "content": "x"}]}, "messages"),
    ({"messages": [{"role": "user", "content": 5}]}, "messages"),
    ({"messages": [{"role": "user", "content": "x"}], "temperature": 5}, "temperature"),
    ({"messages": [{"role": "user", "content": "x"}], "max_tokens": 0}, "max_tokens"),
    ({"messages": [{"role": "user", "content": "x"}], "n": 2}, "n"),
    ({"messages": [{"role": "user", "content": "x"}], "stream": "yes"}, "stream"),
])
def test_chat_invalid_requests(client, body, param):
    err = assert_error(client.post("/v1/chat/completions", headers=AUTH, json=body), 400, "invalid_request")
    assert err["param"] == param


def test_chat_invalid_json(client):
    r = client.post("/v1/chat/completions", headers={**AUTH, "content-type": "application/json"}, content=b"{nope")
    assert_error(r, 400, "invalid_request")


def test_chat_unknown_fields_ignored(client):
    assert chat(client, frequency_penalty=0.5, user="x", logit_bias={}).status_code == 200


def test_chat_disabled_llm(make_client):
    c = make_client(llm_backend="none")
    assert_error(chat(c), 400, "invalid_request")


# -- model loading / failure ----------------------------------------------------
def test_model_loading_503_then_ok(make_client):
    from natlas_server.backends import build

    gate = threading.Event()

    def slow_builder(model_id, settings):
        if model_id == LLM_ID:
            gate.wait(10)
        return build(model_id, settings)

    c = make_client(builder=slow_builder, load_wait_seconds=0.2)
    assert c.get("/health").json()["status"] == "loading"
    r = chat(c)
    assert_error(r, 503, "model_loading")
    assert int(r.headers["retry-after"]) > 0
    gate.set()
    for _ in range(100):
        if c.get("/health").json()["status"] == "ok":
            break
        time.sleep(0.05)
    assert chat(c).status_code == 200


def test_model_failed_is_degraded_and_503(make_client):
    from natlas_server.backends import build

    def broken(model_id, settings):
        if model_id == "NCAIR1/Igbo-ASR":
            raise RuntimeError("no access to gated repo")
        return build(model_id, settings)

    c = make_client(builder=broken)
    for _ in range(100):
        h = c.get("/health").json()
        if h["status"] != "loading":
            break
        time.sleep(0.05)
    assert h["status"] == "degraded"
    assert "no access" in h["models"]["NCAIR1/Igbo-ASR"]["error"]
    r = c.post("/v1/audio/transcriptions", headers=AUTH, data={"language": "ig"},
               files={"file": ("a.wav", wav_bytes(tone(1)), "audio/wav")})
    assert_error(r, 503, "backend_unavailable")
    # other languages still work
    r = c.post("/v1/audio/transcriptions", headers=AUTH, data={"language": "ha"},
               files={"file": ("a.wav", wav_bytes(tone(1)), "audio/wav")})
    assert r.status_code == 200


def test_only_ncair_models_can_be_built():
    from natlas_server.backends import build
    from natlas_server.config import Settings

    for bad in ("openai/whisper-small", "meta-llama/Llama-3.1-8B-Instruct", "gpt-4o"):
        with pytest.raises(ValueError):
            build(bad, Settings(api_keys=["k"]))


# -- transcription ----------------------------------------------------------------
def transcribe(c, audio_bytes, filename="clip.wav", **fields):
    return c.post("/v1/audio/transcriptions", headers=AUTH, data=fields,
                  files={"file": (filename, audio_bytes, "application/octet-stream")})


def test_transcription_json(client):
    r = transcribe(client, wav_bytes(tone(2.0)), language="ha")
    assert r.status_code == 200, r.text
    b = r.json()
    assert b == {"text": "[fake ha] 2.0 seconds of audio", "language": "ha", "duration": 2.0,
                 "model": "NCAIR1/Hausa-ASR"}
    assert r.headers["x-natlas-attribution"] == ATTRIBUTION


def test_transcription_by_model_and_resampling(client):
    b = transcribe(client, wav_bytes(tone(1.0, sr=44100), sr=44100), model="NCAIR1/Yoruba-ASR").json()
    assert b["language"] == "yo" and b["model"] == "NCAIR1/Yoruba-ASR" and b["duration"] == 1.0


def test_transcription_text_and_verbose(client):
    r = transcribe(client, wav_bytes(tone(1.0)), language="en", response_format="text")
    assert r.status_code == 200 and r.text == "[fake en] 1.0 seconds of audio"
    assert r.headers["content-type"].startswith("text/plain")
    b = transcribe(client, wav_bytes(tone(1.0)), language="ig", response_format="verbose_json").json()
    assert b["segments"] == [{"id": 0, "start": 0.0, "end": 1.0, "text": "[fake ig] 1.0 seconds of audio"}]


def test_transcription_long_audio_split_into_windows(client):
    # 25 s speech, 1 s silence, 25 s speech, 1 s silence, 10 s speech = 62 s
    audio = __import__("numpy").concatenate([tone(25), silence(1), tone(25), silence(1), tone(10)])
    b = transcribe(client, wav_bytes(audio), language="ha", response_format="verbose_json").json()
    segs = b["segments"]
    assert b["duration"] == 62.0
    assert len(segs) == 3
    assert segs[0]["start"] == 0.0 and segs[-1]["end"] == 62.0
    for a, n in zip(segs, segs[1:]):
        assert a["end"] == n["start"]
    assert all(s["end"] - s["start"] <= 30.0 for s in segs)
    assert 25.0 <= segs[0]["end"] <= 26.0  # cut inside the silence


@pytest.mark.parametrize("fields,code,param", [
    ({}, "invalid_request", "language"),
    ({"language": "fr"}, "invalid_request", "language"),
    ({"language": "pcm"}, "invalid_request", "language"),
    ({"model": "openai/whisper-small"}, "invalid_request", "model"),
    ({"language": "ha", "model": "NCAIR1/Yoruba-ASR"}, "invalid_request", "model"),
    ({"language": "ha", "response_format": "srt"}, "invalid_request", "response_format"),
])
def test_transcription_bad_fields(client, fields, code, param):
    err = assert_error(transcribe(client, wav_bytes(tone(1)), **fields), 400, code)
    assert err["param"] == param


def test_transcription_missing_file(client):
    r = client.post("/v1/audio/transcriptions", headers=AUTH, data={"language": "ha"},
                    files={"other": ("x.txt", b"hi", "text/plain")})
    assert_error(r, 400, "invalid_request")


def test_transcription_undecodable_is_415(client):
    assert_error(transcribe(client, b"this is not audio at all" * 10, filename="x.wav", language="ha"),
                 415, "unsupported_audio")


def test_transcription_too_large_is_413(client):
    big = b"\0" * (25 * 1024 * 1024 + 10)
    assert_error(transcribe(client, big, language="ha"), 413, "audio_too_large")


def test_transcription_language_not_enabled(make_client):
    c = make_client(asr_languages=["ha"])
    assert transcribe(c, wav_bytes(tone(1)), language="ha").status_code == 200
    assert_error(transcribe(c, wav_bytes(tone(1)), language="yo"), 400, "invalid_request")


def test_transcription_requires_auth(client):
    r = client.post("/v1/audio/transcriptions", data={"language": "ha"},
                    files={"file": ("a.wav", wav_bytes(tone(1)), "audio/wav")})
    assert_error(r, 401, "invalid_api_key")
