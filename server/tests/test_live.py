from __future__ import annotations

import numpy as np
import pytest
from starlette.websockets import WebSocketDisconnect

from natlas_server.registry import ATTRIBUTION

from .conftest import AUTH, pcm16, silence, tone

URL = "/v1/audio/live?language=ha"


def recv_until_close(ws):
    """Collect JSON frames until the server closes; return (frames, close_code)."""
    frames = []
    while True:
        msg = ws.receive()
        if msg["type"] == "websocket.close":
            return frames, msg.get("code")
        import json

        frames.append(json.loads(msg["text"]))


def test_live_partial_final_stop_done(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ready = ws.receive_json()
        assert ready == {"type": "ready", "model": "NCAIR1/Hausa-ASR", "sample_rate": 16000}

        ws.send_bytes(pcm16(tone(1.2)))
        partial = ws.receive_json()
        assert partial["type"] == "partial"
        assert partial["text"] == "[fake ha] 1.2 seconds of audio"
        assert partial["start"] == 0.0 and partial["end"] == pytest.approx(1.2, abs=0.05)

        # 1 s of silence ends the utterance (VAD needs ~0.8 s)
        for i in range(10):
            ws.send_bytes(pcm16(silence(0.1)))
        final = ws.receive_json()
        assert final["type"] == "final"
        assert final["start"] == 0.0
        assert 1.2 <= final["end"] <= 1.5  # trailing silence trimmed to ~150 ms
        assert final["text"].startswith("[fake ha]")

        ws.send_text('{"type": "stop"}')
        frames, code = recv_until_close(ws)
        assert frames == [{"type": "done"}]
        assert code == 1000


def test_live_stop_finalises_open_utterance(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()  # ready
        # odd-sized frames are fine: send 0.6 s in uneven pieces
        data = pcm16(tone(0.6))
        for i in range(0, len(data), 3001):
            ws.send_bytes(data[i:i + 3001])
        ws.send_text('{"type": "stop"}')
        frames, code = recv_until_close(ws)
        types = [f["type"] for f in frames]
        assert types[-2:] == ["final", "done"]
        assert types.count("final") == 1
        final = frames[-2]
        assert final["start"] == 0.0 and final["end"] == pytest.approx(0.6, abs=0.05)
        assert code == 1000


def test_live_two_utterances_absolute_times(client):
    audio = np.concatenate([silence(0.5), tone(0.6), silence(1.0), tone(0.6), silence(1.0)])
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()
        ws.send_bytes(pcm16(audio))
        ws.send_text('{"type": "stop"}')
        frames, code = recv_until_close(ws)
    finals = [f for f in frames if f["type"] == "final"]
    assert len(finals) == 2
    assert finals[0]["start"] == pytest.approx(0.2, abs=0.05)  # 0.3 s pre-roll before speech at 0.5 s
    # second tone starts at 2.1 s; pre-roll only covers silence after the first final closed (~1.9 s)
    assert 1.85 <= finals[1]["start"] < 2.1
    assert finals[0]["end"] < finals[1]["start"]
    assert frames[-1] == {"type": "done"} and code == 1000


def test_live_silence_only_gives_done(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()
        ws.send_bytes(pcm16(silence(2.0)))
        ws.send_text('{"type": "stop"}')
        frames, code = recv_until_close(ws)
    assert frames == [{"type": "done"}] and code == 1000


def test_live_max_utterance_30s(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()
        ws.send_bytes(pcm16(tone(31.0)))
        ws.send_text('{"type": "stop"}')
        frames, _ = recv_until_close(ws)
    finals = [f for f in frames if f["type"] == "final"]
    assert len(finals) == 2
    assert finals[0]["end"] == pytest.approx(30.0, abs=0.05)
    assert finals[1]["start"] == pytest.approx(30.0, abs=0.05)


@pytest.mark.parametrize("url,headers,status,code", [
    (URL, {}, 401, "invalid_api_key"),
    (URL, {"Authorization": "Bearer nope"}, 401, "invalid_api_key"),
    ("/v1/audio/live?language=fr", AUTH, 400, "invalid_request"),
    ("/v1/audio/live", AUTH, 400, "invalid_request"),
])
def test_live_rejections_send_error_frame(client, url, headers, status, code):
    with client.websocket_connect(url, headers=headers) as ws:
        frames, close_code = recv_until_close(ws)
    assert len(frames) == 1
    assert frames[0]["type"] == "error"
    assert frames[0]["error"]["code"] == code
    assert close_code == 4000 + status


def test_live_user_cap(make_client):
    c = make_client(user_cap=1)
    c.get("/v1/models", headers={**AUTH, "X-NAtlas-User": "first"})
    with c.websocket_connect(URL, headers={**AUTH, "X-NAtlas-User": "second"}) as ws:
        frames, close_code = recv_until_close(ws)
    assert frames[0]["error"]["code"] == "user_cap_reached" and close_code == 4403


def test_live_bad_text_frame(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()
        ws.send_text("hello")
        frames, close_code = recv_until_close(ws)
    assert frames[-1]["type"] == "error" and frames[-1]["error"]["code"] == "invalid_request"
    assert close_code == 4400


def test_live_client_disconnect_is_clean(client):
    with client.websocket_connect(URL, headers=AUTH) as ws:
        ws.receive_json()
        ws.send_bytes(pcm16(tone(0.3)))
    # server must not crash; a new session still works
    with client.websocket_connect(URL, headers=AUTH) as ws:
        assert ws.receive_json()["type"] == "ready"
