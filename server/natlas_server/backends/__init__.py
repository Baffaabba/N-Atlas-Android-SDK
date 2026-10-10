"""Backend factory. Only NCAIR1 model ids are ever constructed."""

from __future__ import annotations

from typing import Any

from ..config import Settings
from ..registry import ASR_BY_LANGUAGE, LANGUAGE_BY_ASR, LLM_ID


def build(model_id: str, settings: Settings) -> Any:
    """Load the backend object for one model id (blocking; may download)."""
    if model_id == LLM_ID:
        kind = settings.llm_backend
        if kind == "fake":
            from .fake import FakeLLM

            return FakeLLM(model_id, settings.fake_token_delay_ms)
        if kind == "transformers":
            from .transformers_llm import TransformersLLM

            return TransformersLLM(
                model_id,
                quantization=settings.llm_quantization,
                device=settings.device,
                max_context=settings.llm_max_context,
                cache_dir=settings.hf_cache_dir,
            )
    elif model_id in LANGUAGE_BY_ASR:
        lang = LANGUAGE_BY_ASR[model_id]
        kind = settings.asr_backend
        if kind == "fake":
            from .fake import FakeASR

            return FakeASR(model_id, lang)
        if kind == "transformers":
            from .transformers_asr import TransformersASR

            return TransformersASR(
                model_id,
                lang,
                device=settings.device,
                force_language=settings.asr_force_language,
                cache_dir=settings.hf_cache_dir,
            )
    raise ValueError(f"No backend for model {model_id!r} (allowed: {LLM_ID}, {list(ASR_BY_LANGUAGE.values())})")
