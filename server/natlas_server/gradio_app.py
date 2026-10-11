"""Gradio front end for Colab: a browser playground plus the SDK API on one
public share link.

`launch()` starts a Gradio app with ``share=True`` (a ``*.gradio.live`` URL,
no ngrok or cloudflared account needed), then mounts the full FastAPI app from
``create_app()`` at ``/natlas`` on the same server. So:

- people open ``https://<id>.gradio.live`` and try chat, transcription and a
  voice turn in the browser (login: user ``natlas``, password = the API key);
- the Android/Flutter SDK uses ``https://<id>.gradio.live/natlas`` as
  ``baseUrl``, with the same API key as a Bearer token.

The playground calls the same model manager as the API, so a model loads once.
"""

from __future__ import annotations

import asyncio
import threading
from typing import Any, Iterator

import numpy as np

from .audio import SAMPLE_RATE, resample, split_on_silence
from .backends.base import ChatParams, StreamEnd
from .config import Settings
from .registry import ASR_BY_LANGUAGE, ATTRIBUTION, LLM_ID

API_PATH = "/natlas"

LANGUAGE_CHOICES = [
    ("Hausa", "ha"),
    ("Igbo", "ig"),
    ("Yorùbá", "yo"),
    ("Nigerian English", "en"),
]

INTRO = """\
# N-ATLAS playground

Try the N-ATLAS models in your browser: chat with **N-ATLaS** and transcribe
speech in Hausa, Igbo, Yorùbá and Nigerian English.
App developers: use the Android/Flutter SDK with the API address printed in
the Colab notebook.
"""

FOOTER = f"""\
---
{ATTRIBUTION} Free use of the N-ATLAS models is limited to 1,000 active users
a month; above that, Awarri's commercial licence applies. Not for
surveillance, profiling, disinformation or military use.
"""


def _text_of(content: Any) -> str:
    """Gradio chat history content can be a string or a list of parts."""
    if isinstance(content, str):
        return content
    if isinstance(content, dict):
        return str(content.get("text") or content.get("value") or "")
    if isinstance(content, (list, tuple)):
        return " ".join(_text_of(c) for c in content).strip()
    return ""


def _to_messages(history: list[Any], message: str, system: str) -> list[dict[str, str]]:
    msgs: list[dict[str, str]] = []
    if system.strip():
        msgs.append({"role": "system", "content": system.strip()})
    for h in history or []:
        if isinstance(h, dict) and h.get("role") in ("user", "assistant"):
            text = _text_of(h.get("content"))
            if text:
                msgs.append({"role": h["role"], "content": text})
    msgs.append({"role": "user", "content": message})
    return msgs


def _audio_to_16k(value: Any) -> np.ndarray | None:
    """gr.Audio(type="numpy") gives (sample_rate, array); make it 16 kHz mono float32."""
    if value is None:
        return None
    sr, data = value
    x = np.asarray(data)
    if x.size == 0:
        return None
    if x.ndim == 2:
        x = x.mean(axis=1)
    if np.issubdtype(x.dtype, np.integer):
        x = x.astype(np.float32) / float(np.iinfo(x.dtype).max)
    x = x.astype(np.float32)
    return resample(x, int(sr), SAMPLE_RATE) if int(sr) != SAMPLE_RATE else x


class Playground:
    """Calls the model manager directly (no HTTP hop)."""

    def __init__(self, manager: Any) -> None:
        self.manager = manager

    def _get(self, model_id: str) -> Any:
        return asyncio.run(self.manager.get(model_id))

    def transcribe(self, audio: Any, language: str) -> str:
        x = _audio_to_16k(audio)
        if x is None:
            return "Record or upload some audio first."
        asr = self._get(ASR_BY_LANGUAGE[language])
        parts = [asr.transcribe(x[a:b]) for a, b in split_on_silence(x)]
        text = " ".join(p for p in parts if p).strip()
        return text or "(No speech heard.)"

    def chat(self, message: str, history: list[Any], system: str = "") -> Iterator[str]:
        llm = self._get(LLM_ID)
        cancel = threading.Event()
        out = ""
        try:
            for piece in llm.stream(_to_messages(history, message, system), ChatParams(), cancel):
                if isinstance(piece, StreamEnd):
                    break
                out += piece
                yield out
        finally:
            cancel.set()  # stops generation if the browser goes away

    def voice(self, audio: Any, language: str, history: list[Any]) -> Iterator[tuple[str, list[Any]]]:
        heard = self.transcribe(audio, language)
        if heard.startswith("(") or heard.startswith("Record"):
            yield heard, history
            return
        history = list(history or []) + [{"role": "user", "content": heard}, {"role": "assistant", "content": ""}]
        for reply in self.chat(heard, history[:-2]):
            history[-1] = {"role": "assistant", "content": reply}
            yield heard, history


def build_ui(manager: Any) -> Any:
    import gradio as gr

    pg = Playground(manager)
    with gr.Blocks(title="N-ATLAS playground") as demo:
        gr.Markdown(INTRO)
        with gr.Tab("Chat"):
            system = gr.Textbox(label="Instructions for N-ATLaS (optional)", lines=1)
            gr.ChatInterface(
                fn=pg.chat,
                additional_inputs=[system],
                examples=[["Sannu! Yaya kake?"], ["Kedu ka ị mere?"], ["Báwo ni o ṣe wà?"]],
            )
        with gr.Tab("Transcribe"):
            lang = gr.Dropdown(LANGUAGE_CHOICES, value="ha", label="Language")
            audio = gr.Audio(sources=["microphone", "upload"], type="numpy", label="Speak or upload audio")
            btn = gr.Button("Write down what was said", variant="primary")
            out = gr.Textbox(label="Transcript", lines=4)
            btn.click(pg.transcribe, [audio, lang], out)
        with gr.Tab("Talk to N-ATLAS"):
            vlang = gr.Dropdown(LANGUAGE_CHOICES, value="ha", label="Language you will speak")
            vaudio = gr.Audio(sources=["microphone"], type="numpy", label="Tap to record, tap again to stop")
            vbtn = gr.Button("Send", variant="primary")
            heard = gr.Textbox(label="What N-ATLAS heard")
            convo = gr.Chatbot(label="Conversation")
            vbtn.click(pg.voice, [vaudio, vlang, convo], [heard, convo])
        gr.Markdown(FOOTER)
    return demo


def launch(settings: Settings | None = None, share: bool = True, port: int = 7860) -> dict[str, str]:
    """Start the playground + API. Returns {"ui": ..., "api": ..., "local": ...}.

    Non-blocking: returns once the server is up (Colab keeps running it).
    """
    from starlette.routing import Mount

    from .app import create_app

    settings = settings or Settings.from_env()
    api = create_app(settings)
    manager = api.state.manager
    # A mounted sub-app's lifespan doesn't run, so start preloading here.
    manager.start_preload()

    demo = build_ui(manager)
    demo.queue(default_concurrency_limit=4)
    keys = list(settings.api_keys)
    auth = ("natlas", keys[0]) if keys else None
    _, local_url, share_url = demo.launch(
        share=share,
        server_name="0.0.0.0",
        server_port=port,
        prevent_thread_lock=True,
        auth=auth,
        show_error=True,
        quiet=True,
    )
    server_app = getattr(demo, "server_app", None) or demo.app
    # First in the route list, so no Gradio route can shadow the API.
    server_app.router.routes.insert(0, Mount(API_PATH, app=api))
    base = (share_url or local_url).rstrip("/")
    return {"ui": base, "api": base + API_PATH, "local": local_url.rstrip("/") + API_PATH}
