# N-ATLAS SDK test server

A small server that the N-ATLAS Android and Flutter SDKs talk to. It speaks
the wire protocol in [`docs/API.md`](../docs/API.md) Part 1:

| Endpoint | What |
| --- | --- |
| `GET /health` | Status, loaded models, active-user count. No key needed |
| `GET /v1/models` | The models this server serves |
| `POST /v1/chat/completions` | Chat with N-ATLaS, OpenAI shape, SSE streaming |
| `POST /v1/audio/transcriptions` | Speech-to-text, OpenAI shape plus `language` (`ha`, `ig`, `yo`, `en`) |
| `WS /v1/audio/live` | Live microphone transcription (PCM16, 16 kHz) |

It serves **only** the N-ATLAS models: `NCAIR1/N-ATLaS` for chat and
`NCAIR1/Hausa-ASR`, `Igbo-ASR`, `Yoruba-ASR`, `NigerianAccentedEnglish` for
speech. An unsupported language is an error. It never falls back to another
model.

> N-ATLAS is an initiative of the Federal Ministry of Communications,
> Innovation and Digital Economy, and powered by Awarri Technologies.

**Licence cap.** Free use of the N-ATLAS models is limited to 1,000 active
end users in a rolling 30 days. The server counts distinct `X-NAtlas-User`
ids (stored only as hashes), shows the count in `/health`, and refuses new
users with `403 user_cap_reached` once the cap is reached. Past 1,000 users
you need a commercial licence from Awarri (datasupport@awarri.com). The
licence bans surveillance, profiling, disinformation and military use.

## Run it locally with the fake backend (no GPU, no models)

The `fake` backend returns labelled canned text (`[fake N-ATLaS] You said:
...`, `[fake ha] 2.0 seconds of audio`) so you can build and test the SDKs on
a laptop. Python 3.10 or newer.

Windows (PowerShell), from the repo root:

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[test]"
$env:NATLAS_API_KEYS = "dev-key"
.\.venv\Scripts\python.exe -m natlas_server --host 0.0.0.0 --port 8000
```

macOS / Linux:

```bash
cd server
python3 -m venv .venv
.venv/bin/pip install -e ".[test]"
NATLAS_API_KEYS=dev-key .venv/bin/python -m natlas_server --host 0.0.0.0 --port 8000
```

If you leave `NATLAS_API_KEYS` unset, the server makes a random key and
prints it once at start-up.

From an Android emulator the server is at `http://10.0.2.2:8000`. From a
phone on the same Wi-Fi use your computer's LAN IP (`--host 0.0.0.0` is
needed for both). Android blocks plain `http://` by default, so the sample
apps need a debug network-security config for local testing.

Try it:

```bash
curl http://localhost:8000/health
curl http://localhost:8000/v1/chat/completions -H "Authorization: Bearer dev-key" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Sannu! Yaya kake?"}],"stream":true}'
curl http://localhost:8000/v1/audio/transcriptions -H "Authorization: Bearer dev-key" \
  -F language=ha -F file=@clip.wav
```

Interactive docs: `http://localhost:8000/docs`.

Fake-backend test inputs for the SDKs' error paths: the chat message
`__fake_error__` fails the stream after two pieces (you get an SSE `error`
event, then `[DONE]`), and `__fake_long__` produces a 200-word reply (to test
`max_tokens` and `finish_reason: "length"`). `NATLAS_FAKE_TOKEN_DELAY_MS`
(default 30) sets the delay between streamed pieces.

### Run the tests

```powershell
cd server
.\.venv\Scripts\python.exe -m pytest -q
```

## Run it on Google Colab (real models, T4 GPU)

Open [`colab/natlas_server_colab.ipynb`](colab/natlas_server_colab.ipynb) in
Colab, choose a T4 GPU runtime, add the secrets below, and *Run all*. The
notebook clones this repo (or takes an uploaded zip), installs the server,
loads N-ATLaS in 4-bit plus the four ASR models, starts the server with an
automatic-restart watchdog, opens a tunnel, prints the **base URL and API
key**, and runs smoke tests (health, chat, streaming, a Hausa transcription
and a live WebSocket session).

Colab secrets (key icon on the left; switch on notebook access for each):

| Secret | Required | Effect |
| --- | --- | --- |
| `HF_TOKEN` | yes | Downloads the gated NCAIR1 repos. Never printed or logged |
| `NATLAS_API_KEYS` | no | Fixed API key(s), comma-separated. Set this so the key stays the same across sessions. If unset, a random key is generated each run |
| `NGROK_AUTHTOKEN` | no | Use ngrok instead of a Cloudflare quick tunnel |
| `NGROK_DOMAIN` | no | Your free ngrok static domain, e.g. `your-name.ngrok-free.app`. The URL then never changes |

Your Hugging Face account must accept the terms of all five NCAIR1 repos.
Any repo you have not accepted shows as `failed` in `/health`, and that
language returns `503 backend_unavailable`. The rest keep working.

### Tunnels

| | Cloudflare quick tunnel (default) | ngrok |
| --- | --- | --- |
| Account | none | free ngrok account (`NGROK_AUTHTOKEN`) |
| URL | new `*.trycloudflare.com` each session | fixed if `NGROK_DOMAIN` is set |
| Chat streaming (SSE) | Cloudflare's docs say it is not supported; in our test it worked (see below) | supported |
| Live WebSocket | worked in our test with `--protocol http2` | supported |

The notebook picks ngrok automatically when `NGROK_AUTHTOKEN` is set (or set
`TUNNEL` in the first cell). It uses `pyngrok`:
`ngrok.connect(addr="8000", domain=NGROK_DOMAIN)`. For the judges'
integration check (15–17 Oct) use ngrok with a static domain so the URL in
the README and sample apps stays valid. To get the domain: sign in at
ngrok.com, open *Domains*, and claim your free static domain. ngrok shows a
warning page to web browsers on free domains; API clients such as the SDKs
(OkHttp) and curl are not affected.

Quick-tunnel test, 2026-10-10, fake backend on a Windows laptop (Intel
i5-7200U, home Wi-Fi), cloudflared 2026.10.0, scripts in this session:

- SSE, 40 pieces 100 ms apart (about 4 s of generation): n=5 per transport.
  QUIC transport: first event median 0.56 s, last 4.77 s. HTTP/2 transport:
  first 0.65 s, last 5.42 s. Pieces arrived one by one, not in a lump.
- Live WebSocket, 1.5 s tone then 1 s silence sent in 200 ms frames: with
  the default QUIC transport the server received **no** audio frames (only
  server-to-client worked). With `--protocol http2` it worked: first partial
  at median 1.62 s after the first frame (n=3), against 1.17 s on localhost
  (n=1). The notebook therefore runs cloudflared with `--protocol http2`.

### Memory budget on a T4 (15 GB)

| Item | Approx. VRAM |
| --- | --- |
| N-ATLaS 8B in 4-bit NF4 (embeddings and LM head stay 16-bit) | ~5.7 GB |
| 4 × Whisper-small, fp16 | ~2.0 GB |
| CUDA context | ~0.5 GB |
| KV cache at the 8k-token limit | ~1.0 GB |
| Total | ~9–10 GB |

These are estimates from parameter counts, not measurements; the notebook
prints the real `nvidia-smi` figure after loading. If memory runs short,
load fewer languages (`ASR_LANGUAGES` in the notebook) or set
`NATLAS_PRELOAD` so ASR models load on first use.

## Environment variables

| Variable | Default | Meaning |
| --- | --- | --- |
| `NATLAS_API_KEYS` | (random, printed) | Comma-separated API keys. Hashed in memory at start-up |
| `NATLAS_API_KEY_HASHES` | | Comma-separated SHA-256 hex hashes of keys, so plain keys need not sit in the environment. Revoke a key by removing it and restarting |
| `NATLAS_USER_CAP` | `1000` | Active users per rolling 30 days before `403 user_cap_reached` |
| `NATLAS_DB_PATH` | `natlas_users.sqlite3` | SQLite file for the user counter (hashed ids and timestamps only) |
| `NATLAS_RATE_LIMIT_PER_MINUTE` | `120` | Requests per minute per API key; `0` turns it off |
| `NATLAS_BACKEND` | `fake` | `fake` or `transformers`, for both chat and ASR |
| `NATLAS_LLM_BACKEND` / `NATLAS_ASR_BACKEND` | `NATLAS_BACKEND` | Per kind: `fake`, `transformers` or `none` (disabled) |
| `NATLAS_ASR_LANGUAGES` | `ha,ig,yo,en` | Which ASR models to serve |
| `NATLAS_PRELOAD` | `all` | `all`, `none`, or a list of model ids / language codes to load at start-up. Others load on first use |
| `NATLAS_LOAD_WAIT_SECONDS` | `60` | How long a request waits for a loading model before `503 model_loading` |
| `NATLAS_LLM_QUANTIZATION` | `4bit` | `4bit` (NF4), `8bit` or `none` |
| `NATLAS_LLM_MAX_CONTEXT` | `8192` | Prompt plus `max_tokens` limit (the model card's advice) |
| `NATLAS_LLM_MAX_TOKENS_CAP` | `2048` | Highest `max_tokens` accepted |
| `NATLAS_ASR_FORCE_LANGUAGE` | `0` | `1` forces Whisper's language token for ha, yo and en. Off by default because the model cards call the pipeline without it |
| `NATLAS_DEVICE` | `auto` | `auto`, `cuda` or `cpu` |
| `NATLAS_HF_CACHE_DIR` | HF default | Where model files are stored |
| `HF_TOKEN` | | Hugging Face token for the gated repos |
| `NATLAS_HOST` / `NATLAS_PORT` | `127.0.0.1` / `8000` | Bind address (or `--host` / `--port`) |
| `NATLAS_LOG_LEVEL` | `INFO` | Log level |
| `NATLAS_FAKE_TOKEN_DELAY_MS` | `30` | Fake backend: delay between streamed pieces |

Model downloads fetch only config, tokenizer and weight files. They never
fetch `optimizer.pt` (1.9 GB per ASR repo), scheduler, RNG or trainer state.

## Privacy

No request audio or text is stored or logged. Logs hold metadata only:
method, path, status, duration, request id, User-Agent, and for live
sessions the audio length and number of partials and finals. Uploaded audio
stays in memory; only the ffmpeg fallback (m4a, webm and other formats
libsndfile can't read) writes a temp file, deleted straight after decoding.

## How it works

- FastAPI + uvicorn. Models sit behind a small backend interface
  (`natlas_server/backends/`): `fake` for tests, `transformers` for real use.
- Chat: N-ATLaS through transformers, 4-bit NF4 via bitsandbytes on a CUDA
  GPU, the repo's Llama-3 chat template, temperature 0.1 and
  repetition_penalty 1.12 by default, streaming through
  `TextIteratorStreamer`. One generation runs at a time; others queue. If the
  client disconnects, generation stops.
- Transcription: the NCAIR1 Whisper-small models through the transformers
  pipeline. Audio over 30 s is cut at the quietest point near each 30 s
  boundary and transcribed window by window.
- Live: energy-based voice activity detection splits the stream into
  utterances. About once a second the open utterance is transcribed again
  (`partial`); after about 0.8 s of silence or 30 s it is transcribed once
  more (`final`).

## Limitations

- **This is a test server, not hosting.** Colab ends idle sessions and caps
  free sessions at 12 hours. Colab's FAQ lists "web service offerings not
  related to interactive compute" and "connecting to remote proxies" as
  disallowed on managed runtimes, so a public API served from Colab can be
  stopped without warning. Do not rely on it for the 15–17 Oct check without
  a fallback.
- **The quick-tunnel URL changes every session.** Use ngrok with a static
  domain when the URL must stay fixed. The SDKs' `baseUrl` is always
  configurable.
- **Live transcription is chunked, not true streaming.** Whisper re-reads the
  open utterance about once a second; text can change until the `final`.
  Partials thin out when the server is slower than real time.
- **VAD is a simple energy detector.** Steady loud background noise can look
  like speech and keep an utterance open until the 30 s limit.
- **Long files are split in 30 s windows**, one segment per window, so
  segment times are coarse.
- **The Igbo language code `ig` has no Whisper language token.** The Igbo
  model is never forced to a language.
- **N-ATLaS needs a CUDA GPU** in this server. On CPU use the fake backend or
  disable chat (`NATLAS_LLM_BACKEND=none`); the ASR models do run on CPU.
- **One GPU, one generation at a time.** Concurrent chats wait in line.
- **API keys are static** (from environment variables). There is no key
  management UI. Revoking means editing the variable and restarting.
- **The user counter is per server.** Several servers would each count
  separately.

## Status

Test server only. Production hosting for the judges' integration check
(15–17 Oct 2026) is not settled; see `docs/DECISIONS.md`. When a server is
running, `GET /health` reports `ok`, `loading` or `degraded`.
