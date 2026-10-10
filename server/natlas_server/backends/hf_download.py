"""Selective downloads from the gated NCAIR1 repos.

Only config, tokenizer and weight files. Never optimizer.pt (1.9 GB),
scheduler, RNG state or trainer state. The token comes from HF_TOKEN.
"""

from __future__ import annotations

import os

LLM_FILES = [
    "config.json",
    "generation_config.json",
    "model.safetensors.index.json",
    "model-*.safetensors",
    "tokenizer.json",
    "tokenizer_config.json",
    "special_tokens_map.json",
]

ASR_FILES = [
    "config.json",
    "generation_config.json",
    "preprocessor_config.json",
    "tokenizer.json",
    "tokenizer_config.json",
    "vocab.json",
    "merges.txt",
    "normalizer.json",
    "added_tokens.json",
    "special_tokens_map.json",
    "model.safetensors",
    "pytorch_model.bin",
]

NEVER = ["optimizer.pt", "scheduler.pt", "rng_state*", "trainer_state.json", "training_args.bin", "*.pth"]


class ModelAccessError(RuntimeError):
    pass


def download(repo_id: str, allow: list[str], cache_dir: str | None = None) -> str:
    from huggingface_hub import snapshot_download

    try:
        from huggingface_hub.errors import GatedRepoError, RepositoryNotFoundError
    except ImportError:  # older huggingface_hub
        from huggingface_hub.utils import GatedRepoError, RepositoryNotFoundError  # type: ignore

    token = os.environ.get("HF_TOKEN") or None
    try:
        return snapshot_download(
            repo_id=repo_id,
            allow_patterns=allow,
            ignore_patterns=NEVER,
            token=token,
            cache_dir=cache_dir,
        )
    except GatedRepoError as e:
        raise ModelAccessError(
            f"No access to gated repo {repo_id}. Accept its terms at https://huggingface.co/{repo_id} "
            "with the account that owns HF_TOKEN."
        ) from e
    except RepositoryNotFoundError as e:
        hint = "" if token else " HF_TOKEN is not set."
        raise ModelAccessError(f"Repo {repo_id} not found or not accessible.{hint}") from e
