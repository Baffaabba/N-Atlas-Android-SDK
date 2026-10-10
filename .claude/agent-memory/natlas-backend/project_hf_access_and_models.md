---
name: project-hf-access-and-models
description: Which NCAIR1 repos the repo .env HF_TOKEN can download (as of 2026-10-10), ASR model quirks, and a usable public Hausa test clip
metadata:
  type: project
---

As of 2026-10-10 the HF_TOKEN in the repo `.env` can download files from `NCAIR1/N-ATLaS`, `Hausa-ASR` and
`Yoruba-ASR`, but gets **403 on files from `Igbo-ASR` and `NigerianAccentedEnglish`** (repo listing works; the gate
terms were apparently not accepted on those two). Baffa must accept them on huggingface.co.

**Why:** a Colab run with that token shows those two models as `failed` in /health (503 backend_unavailable for ig/en).
**How to apply:** check access again before claiming all four languages work; re-run the HF API probe.

ASR quirks: repos only have `pytorch_model.bin` (no safetensors) plus a 1.9 GB `optimizer.pt`; generation_config
leaves the language slot open (`forced_decoder_ids [[1,null],[2,50359]]`); model cards call the pipeline with no
language; Igbo has no Whisper language token. Hausa-ASR weights were already in the local HF cache (dated 2026-10-09).

Public Hausa test clip that works without big downloads: datasets-server `first-rows` of
`SilencioNetwork/hausa-speech-transcribed` (test[0], 26.7 s Kano speech, CC-BY-NC-4.0). FLEURS `ha_ng` is a single
770 MB parquet row group and the datasets-server rows API returns 500/501 for it.

Related: [[project-tunnels-and-colab-policy]]
