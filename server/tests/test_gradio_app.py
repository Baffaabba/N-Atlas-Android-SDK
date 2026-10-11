"""Gradio front end: the API must be reachable under /natlas on the Gradio server,
and the playground must call the same models. Skipped if gradio isn't installed."""

from __future__ import annotations

import socket

import numpy as np
import pytest

gr = pytest.importorskip("gradio")
httpx = pytest.importorskip("httpx")

from natlas_server.config import Settings  # noqa: E402
from natlas_server.gradio_app import API_PATH, Playground, _audio_to_16k, launch  # noqa: E402


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture(scope="module")
def served(tmp_path_factory):
    db = tmp_path_factory.mktemp("g") / "users.sqlite3"
    settings = Settings(api_keys=["g-key"], db_path=str(db), fake_token_delay_ms=1)
    urls = launch(settings, share=False, port=_free_port())
    yield urls
    gr.close_all()


def test_api_mounted_under_natlas(served):
    api = served["local"]
    assert api.endswith(API_PATH)
    r = httpx.get(api + "/health", timeout=30)
    assert r.status_code == 200
    assert "x-natlas-attribution" in r.headers
    assert httpx.get(api + "/v1/models", timeout=30).status_code == 401
    r = httpx.post(
        api + "/v1/chat/completions",
        headers={"Authorization": "Bearer g-key"},
        json={"messages": [{"role": "user", "content": "Sannu"}]},
        timeout=30,
    )
    assert r.status_code == 200
    assert "Sannu" in r.json()["choices"][0]["message"]["content"]


def test_gradio_ui_needs_login(served):
    r = httpx.get(served["ui"] + "/config", timeout=30)
    assert r.status_code in (401, 403) or "auth_required" in r.text


def test_audio_conversion():
    stereo = (48000, (np.ones((4800, 2)) * 1000).astype(np.int16))
    x = _audio_to_16k(stereo)
    assert x.dtype == np.float32 and x.ndim == 1 and abs(len(x) - 1600) <= 1
    assert _audio_to_16k(None) is None


def test_playground_uses_models(tmp_path):
    from natlas_server.app import create_app

    api = create_app(Settings(api_keys=["k"], db_path=str(tmp_path / "u.sqlite3"), fake_token_delay_ms=1))
    pg = Playground(api.state.manager)
    clip = (16000, (np.random.randn(16000) * 2000).astype(np.int16))
    assert "[fake ha]" in pg.transcribe(clip, "ha")
    history = [{"role": "assistant", "content": [{"type": "text", "text": "hi"}]}]
    assert "Sannu" in list(pg.chat("Sannu", history))[-1]
    heard, convo = list(pg.voice(clip, "yo", []))[-1]
    assert "[fake yo]" in heard and convo[-1]["role"] == "assistant"
