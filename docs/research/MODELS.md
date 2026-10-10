# N-ATLAS models (research, 2026-10-10)

Checked against the Hugging Face API (https://huggingface.co/api/models?author=NCAIR1)
and the model cards. All five repos were created on 2025-09-19 and last
modified between 23 and 30 Sep 2025. There has been no update since: no v2
and no TTS.

## Repos

| Repo | What | Size |
| --- | --- | --- |
| `NCAIR1/N-ATLaS` | `LlamaForCausalLM`, 8,030,261,248 params, BF16, 4 shards | ~16.06 GB |
| `NCAIR1/Hausa-ASR` | whisper-small fine-tune | 967 MB fp32 |
| `NCAIR1/Igbo-ASR` | whisper-small fine-tune | ~967 MB (assumed) |
| `NCAIR1/Yoruba-ASR` | whisper-small fine-tune | ~967 MB (assumed) |
| `NCAIR1/NigerianAccentedEnglish` | whisper-small fine-tune | ~967 MB (assumed) |

- **Developer:** Awarri Technologies with NCAIR/NITDA and the Federal Ministry
  of Communications, Innovation and Digital Economy.
- **Launch:** announced at UNGA80 on 2025-09-20.
- **Training:** SFT on about 392M tokens.
- **Human-eval averages (1–5):** EN 4.21, HA 3.98, IG 3.87, YO 2.69.
- **Pidgin:** not supported. BBC Pidgin appears only as a data source.
- **Card bug:** the card's sample output names the assistant "AwaGPT".

## LLM architecture

- 32 layers, hidden size 4096, 32 heads with 8 KV heads, head dimension 128.
- Vocabulary 128,256 (Llama-3 tokenizer).
- `max_position_embeddings` is 131,072 and the template defaults to the date
  "26 Jul 2024", which points to a **Llama-3.1-8B** base.
- The card says it works best within about 8k tokens.
- Llama-3 chat template.
- Recommended settings: temperature 0.1, repetition_penalty 1.12.

## ASR

- whisper-small, 16 kHz input, 30 s windows.
- No published WER.
- Repos contain a 1.9 GB `optimizer.pt`. Download selectively.

## Formats

- **Official:** safetensors and .bin only.
- **Unofficial GGUF:** `QuantFactory/N-ATLaS-GGUF`. Sizes: Q2_K 3.18 GB,
  Q3_K_M 4.02, Q4_K_M 4.92, Q5_K_M 5.73, Q6_K 6.60, Q8_0 8.54 GB. Also
  `tosinamuda/N-ATLaS-GGUF` and `inuwamobarak/N-ATLaS-8B-GGUF-Q4_K_M`.
- No ONNX, ggml or CTranslate2 builds of the ASR models.

## Licence

- "Open-Source Research and Innovation License". Gated.
- Attribution required.
- The same licence applies to derivatives, and renamed models must say
  "Powered by Awarri".
- **Cap of 1,000 active end users per rolling 30 days.** Above that, a
  commercial licence from Awarri (datasupport@awarri.com).
- Banned uses: surveillance, profiling, disinformation, military.
- Governed by Nigerian law, and revocable.
- Probably also covered by the Llama 3.1 Community Licence. Unconfirmed.

## On-device notes (kept in case v2 adds offline mode)

- **LLM:** Q4_K_M needs about 6 GB resident at 4k context, so a 12 GB phone.
  It would produce about 3–10 tokens/s on flagship CPUs.
- **ASR:** whisper.cpp q5/q8 builds are about 180–250 MB and run near
  real-time on mid-range phones. sherpa-onnx has Kotlin and Flutter bindings.
- **TTS (third-party):** Meta MMS-TTS (`facebook/mms-tts-hau`, `-yor`, `-ibo`)
  is CC-BY-NC.
