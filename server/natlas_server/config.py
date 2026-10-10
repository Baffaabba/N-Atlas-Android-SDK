"""Settings, read from environment variables (see server/README.md)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Mapping

from .registry import ASR_BY_LANGUAGE


def _split(value: str | None) -> list[str]:
    return [p.strip() for p in (value or "").split(",") if p.strip()]


def _bool(value: str | None, default: bool) -> bool:
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


@dataclass
class Settings:
    api_keys: list[str] = field(default_factory=list)
    api_key_hashes: list[str] = field(default_factory=list)
    user_cap: int = 1000
    db_path: str = "natlas_users.sqlite3"
    rate_limit_per_minute: int = 120

    llm_backend: str = "fake"  # fake | transformers | none
    asr_backend: str = "fake"  # fake | transformers | none
    asr_languages: list[str] = field(default_factory=lambda: list(ASR_BY_LANGUAGE))
    preload: list[str] = field(default_factory=lambda: ["all"])  # "all", "none", or model ids / language codes
    load_wait_seconds: float = 60.0

    llm_quantization: str = "4bit"  # 4bit | 8bit | none
    llm_max_context: int = 8192
    llm_max_tokens_cap: int = 2048
    asr_force_language: bool = False
    device: str = "auto"  # auto | cuda | cpu
    hf_cache_dir: str | None = None

    fake_token_delay_ms: int = 30
    log_level: str = "INFO"

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> "Settings":
        e = os.environ if env is None else env
        backend = (e.get("NATLAS_BACKEND") or "fake").strip().lower()
        langs = _split(e.get("NATLAS_ASR_LANGUAGES")) or list(ASR_BY_LANGUAGE)
        unknown = [l for l in langs if l not in ASR_BY_LANGUAGE]
        if unknown:
            raise ValueError(f"NATLAS_ASR_LANGUAGES has unsupported codes {unknown}; use {list(ASR_BY_LANGUAGE)}")
        return cls(
            api_keys=_split(e.get("NATLAS_API_KEYS")),
            api_key_hashes=[h.lower() for h in _split(e.get("NATLAS_API_KEY_HASHES"))],
            user_cap=int(e.get("NATLAS_USER_CAP") or 1000),
            db_path=e.get("NATLAS_DB_PATH") or "natlas_users.sqlite3",
            rate_limit_per_minute=int(e.get("NATLAS_RATE_LIMIT_PER_MINUTE") or 120),
            llm_backend=(e.get("NATLAS_LLM_BACKEND") or backend).strip().lower(),
            asr_backend=(e.get("NATLAS_ASR_BACKEND") or backend).strip().lower(),
            asr_languages=langs,
            preload=_split(e.get("NATLAS_PRELOAD")) or ["all"],
            load_wait_seconds=float(e.get("NATLAS_LOAD_WAIT_SECONDS") or 60),
            llm_quantization=(e.get("NATLAS_LLM_QUANTIZATION") or "4bit").strip().lower(),
            llm_max_context=int(e.get("NATLAS_LLM_MAX_CONTEXT") or 8192),
            llm_max_tokens_cap=int(e.get("NATLAS_LLM_MAX_TOKENS_CAP") or 2048),
            asr_force_language=_bool(e.get("NATLAS_ASR_FORCE_LANGUAGE"), False),
            device=(e.get("NATLAS_DEVICE") or "auto").strip().lower(),
            hf_cache_dir=e.get("NATLAS_HF_CACHE_DIR") or None,
            fake_token_delay_ms=int(e.get("NATLAS_FAKE_TOKEN_DELAY_MS") or 30),
            log_level=(e.get("NATLAS_LOG_LEVEL") or "INFO").upper(),
        )

    def validate(self) -> None:
        for name, value in (("NATLAS_LLM_BACKEND", self.llm_backend), ("NATLAS_ASR_BACKEND", self.asr_backend)):
            if value not in ("fake", "transformers", "none"):
                raise ValueError(f"{name} must be fake, transformers or none, not '{value}'")
        if self.llm_quantization not in ("4bit", "8bit", "none"):
            raise ValueError("NATLAS_LLM_QUANTIZATION must be 4bit, 8bit or none")
        if not self.api_keys and not self.api_key_hashes:
            raise ValueError("No API keys configured. Set NATLAS_API_KEYS (comma-separated) or NATLAS_API_KEY_HASHES.")
        if self.user_cap < 1:
            raise ValueError("NATLAS_USER_CAP must be at least 1")
