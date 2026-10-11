"""The NCAIR1 Whisper-small ASR fine-tunes through the transformers pipeline.

By default Whisper's language token is forced for ha, yo and en
(NATLAS_ASR_FORCE_LANGUAGE=1). The fine-tunes' generation_config leaves the
language slot open, so without forcing Whisper guesses the language from the
audio, and on short or noisy microphone clips a wrong guess makes it decode
in the wrong language. Set NATLAS_ASR_FORCE_LANGUAGE=0 to follow the model
cards exactly. Igbo has no Whisper token and is never forced.
"""

from __future__ import annotations

import logging
import threading
from typing import Any

import numpy as np

from ..audio import SAMPLE_RATE
from ..registry import WHISPER_LANGUAGE
from .hf_download import ASR_FILES, download

log = logging.getLogger("natlas.asr")


class TransformersASR:
    def __init__(
        self,
        model_id: str,
        language: str,
        device: str = "auto",
        force_language: bool = False,
        cache_dir: str | None = None,
    ) -> None:
        import torch
        import transformers
        from transformers import pipeline

        self.model_id = model_id
        self.language = language
        self._lock = threading.Lock()
        cuda = torch.cuda.is_available() and device in ("auto", "cuda")
        if device == "cuda" and not torch.cuda.is_available():
            raise RuntimeError("NATLAS_DEVICE=cuda but no CUDA GPU is available")
        dtype = torch.float16 if cuda else torch.float32

        path = download(model_id, ASR_FILES, cache_dir)
        log.info("loading %s on %s", model_id, "cuda" if cuda else "cpu")
        from packaging.version import Version

        dkw = {"dtype": dtype} if Version(transformers.__version__) >= Version("4.56.0") else {"torch_dtype": dtype}
        self.pipe = pipeline(
            "automatic-speech-recognition",
            model=path,
            device=0 if cuda else -1,
            **dkw,
        )
        self.whisper_language = WHISPER_LANGUAGE.get(language) if force_language else None
        self.info: dict[str, Any] = {
            "backend": "transformers",
            "device": torch.cuda.get_device_name(0) if cuda else "cpu",
            "dtype": str(dtype).replace("torch.", ""),
            "forced_language": self.whisper_language,
        }

    def transcribe(self, audio: np.ndarray, temperature: float = 0.0) -> str:
        if audio.size == 0:
            return ""
        gen: dict[str, Any] = {"task": "transcribe"}
        if self.whisper_language:
            gen["language"] = self.whisper_language
        if temperature and temperature > 0:
            gen.update(do_sample=True, temperature=float(temperature))
        with self._lock:
            out = self.pipe(
                {"raw": audio.astype(np.float32, copy=False), "sampling_rate": SAMPLE_RATE},
                chunk_length_s=30,
                generate_kwargs=gen,
            )
        return str(out.get("text", "")).strip()
