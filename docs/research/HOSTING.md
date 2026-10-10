# Where can the SDK send requests? (research, 2026-10-10)

Facts checked online on 2026-10-10. Estimates are marked as estimates.

## Verified

**No public official API.** The NCAIR LLM page (https://ncair.nitda.gov.ng/llm/)
links only to https://huggingface.co/NCAIR1. The model card says "This model
isn't deployed by any Inference Provider". The HF API returns an empty
`inferenceProviderMapping` for N-ATLaS and Hausa-ASR. There is no Awarri,
AwaGPT or Ministry developer API.

**NAIC.** The NAIC page (https://ncair.nitda.gov.ng/naic/) says shortlisted
teams receive "N-ATLAS API credentials" and partner compute credits. It gives
no endpoint, docs or format. PS02 (voice) says solutions "must use the
official N-ATLAS ASR service", and that service isn't publicly documented.
Questions go to naic@nitda.gov.ng.

**Third-party hosts.**
- OpenRouter, Together, Replicate, Groq: no listing.
- featherless.ai: unverified.
- OpenAtlas (https://github.com/Benedict258/OpenAtlas): `api.getopenatlas.xyz`,
  keys issued by hand. The README says the backend "is not always on" and
  returns 503 when it's down.
- N-Atlas-Toolkit (https://github.com/samolubukun/N-Atlas-Toolkit): an
  OpenAI-compatible server you deploy yourself on Modal or Lightning, serving
  `/v1/chat/completions` and `/v1/audio/transcriptions`.
- About 27 HF Spaces use N-ATLaS, all third-party. They run on ZeroGPU, sleep
  when idle and can change at any time. Fine for a demo, not for an SDK
  backend.

**Licence.** Redistribution is allowed "under these same Terms". The cap is
1,000 active end users per rolling 30 days. Attribution is mandatory. The terms
don't explicitly cover hosting the model as an API; that looks allowed if the
cap and attribution are enforced. Not confirmed with Awarri.

**Prices.**
- HF Inference Endpoints: T4 $0.50/h, L4 $0.70–0.80/h, A10G $1.00/h, with
  scale-to-zero.
- Modal: L4 about $0.80/h, billed per second, with $30/month free credit.
- RunPod: L4 pods from about $0.39/h.

## Estimates (not verified)

- One 24 GB GPU (L4/A10G) running vLLM can hold N-ATLaS fp16 or AWQ plus all
  four Whisper-small models. Running it 24/7 costs about $290–580/month.
- Serverless scale-to-zero at hackathon traffic costs about $0–30/month, with
  LLM cold starts of 20–60 s.
- A CPU VM (8 vCPU / 16 GB) running llama.cpp Q4_K_M costs about
  $50–100/month and produces roughly 5–10 tokens/s. faster-whisper small int8
  runs at about real-time on it.
- The NCAIR Whisper checkpoints need a one-time `ct2-transformers-converter`
  conversion before faster-whisper can load them.

## Recommendation (awaiting Baffa)

- Host our own thin gateway.
- The SDK speaks the **OpenAI-compatible protocol**: `/v1/chat/completions`
  with SSE, and `/v1/audio/transcriptions` with a language parameter routed to
  the right NCAIR ASR model. The base URL and key are configurable, so the
  official NAIC credentials or a future NCAIR endpoint becomes a config change.
- Backend: vLLM for N-ATLaS plus a small ASR service, on serverless L4/A10G
  with scale-to-zero, and an always-on llama.cpp CPU VM as the fallback for
  the 15–17 Oct integration check.
- The gateway issues per-app keys, counts active users against the 1,000 cap,
  and returns the attribution.
- Email naic@nitda.gov.ng before 12 Oct to ask for the official API/ASR
  service format.
