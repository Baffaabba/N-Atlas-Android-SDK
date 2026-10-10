"""The fixed set of models this server may serve. Nothing outside NCAIR1."""

from __future__ import annotations

from .errors import APIError

ATTRIBUTION = (
    "N-ATLAS is an initiative of the Federal Ministry of Communications, "
    "Innovation and Digital Economy, and powered by Awarri Technologies."
)

LLM_ID = "NCAIR1/N-ATLaS"

ASR_BY_LANGUAGE: dict[str, str] = {
    "ha": "NCAIR1/Hausa-ASR",
    "ig": "NCAIR1/Igbo-ASR",
    "yo": "NCAIR1/Yoruba-ASR",
    "en": "NCAIR1/NigerianAccentedEnglish",
}
LANGUAGE_BY_ASR: dict[str, str] = {v: k for k, v in ASR_BY_LANGUAGE.items()}

# Whisper language names, used only when NATLAS_ASR_FORCE_LANGUAGE=1.
# Igbo has no Whisper language token, so it is never forced.
WHISPER_LANGUAGE: dict[str, str | None] = {
    "ha": "hausa",
    "yo": "yoruba",
    "en": "english",
    "ig": None,
}

ALL_MODEL_IDS = [LLM_ID, *ASR_BY_LANGUAGE.values()]

LANGUAGES_TEXT = ", ".join(f"'{k}'" for k in ASR_BY_LANGUAGE)


def resolve_asr(language: str | None, model: str | None) -> tuple[str, str]:
    """Map the (language, model) pair from a request to (language, model id).

    Raises a 400 for unknown values, missing both, or a disagreeing pair.
    """
    language = (language or "").strip().lower() or None
    model = (model or "").strip() or None
    if language is None and model is None:
        raise APIError(
            400,
            "invalid_request",
            f"Give a 'language' ({LANGUAGES_TEXT}) or an ASR 'model'.",
            param="language",
        )
    if language is not None and language not in ASR_BY_LANGUAGE:
        raise APIError(
            400,
            "invalid_request",
            f"Unsupported language '{language}'. Supported: {LANGUAGES_TEXT}.",
            param="language",
        )
    if model is not None and model not in LANGUAGE_BY_ASR:
        raise APIError(
            400,
            "invalid_request",
            f"Unknown ASR model '{model}'. Supported: {', '.join(ASR_BY_LANGUAGE.values())}.",
            param="model",
        )
    if language and model and ASR_BY_LANGUAGE[language] != model:
        raise APIError(
            400,
            "invalid_request",
            f"'language' {language} and 'model' {model} disagree. "
            f"Language {language} is served by {ASR_BY_LANGUAGE[language]}.",
            param="model",
        )
    if language is None:
        language = LANGUAGE_BY_ASR[model]  # type: ignore[index]
    return language, ASR_BY_LANGUAGE[language]
