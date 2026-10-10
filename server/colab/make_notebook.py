"""Generate natlas_server_colab.ipynb. Run: python server/colab/make_notebook.py

The notebook is generated (not hand-edited) so it stays valid JSON.
"""

from __future__ import annotations

import os
import sys

import nbformat
from nbformat.v4 import new_code_cell, new_markdown_cell, new_notebook

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "natlas_server_colab.ipynb")

cells = []
md = lambda s: cells.append(new_markdown_cell(s.strip("\n")))  # noqa: E731
code = lambda s: cells.append(new_code_cell(s.strip("\n")))  # noqa: E731

md(r"""
# N-ATLAS SDK test server on Google Colab

Runs the N-ATLAS SDK server (`server/` in the repo) on a Colab **T4 GPU** and
gives it a public HTTPS URL so the Android and Flutter SDKs can call it.

It serves only the N-ATLAS models: `NCAIR1/N-ATLaS` (4-bit NF4) for chat and
`NCAIR1/Hausa-ASR`, `Igbo-ASR`, `Yoruba-ASR`, `NigerianAccentedEnglish` for
speech-to-text.

> N-ATLAS is an initiative of the Federal Ministry of Communications,
> Innovation and Digital Economy, and powered by Awarri Technologies.
> Free use is capped at 1,000 active end users in a rolling 30 days.

**Before you start**

1. *Runtime → Change runtime type → T4 GPU.*
2. With your Hugging Face account, accept the terms on all five repos:
   [N-ATLaS](https://huggingface.co/NCAIR1/N-ATLaS),
   [Hausa-ASR](https://huggingface.co/NCAIR1/Hausa-ASR),
   [Igbo-ASR](https://huggingface.co/NCAIR1/Igbo-ASR),
   [Yoruba-ASR](https://huggingface.co/NCAIR1/Yoruba-ASR),
   [NigerianAccentedEnglish](https://huggingface.co/NCAIR1/NigerianAccentedEnglish).
   A repo whose terms you have not accepted shows as an error in `/health`;
   the other models still work.
3. Add Colab secrets (key icon on the left) and switch on notebook access:

| Secret | Required | What |
| --- | --- | --- |
| `HF_TOKEN` | yes | Hugging Face read token |
| `NATLAS_API_KEYS` | no | Comma-separated API keys. Set it to keep the same key across sessions; otherwise a random key is made each run |
| `NGROK_AUTHTOKEN` | no | Use ngrok instead of a Cloudflare quick tunnel |
| `NGROK_DOMAIN` | no | Your free ngrok static domain (e.g. `name.ngrok-free.app`), so the URL never changes |

Then *Runtime → Run all*. First run downloads about 20 GB and takes roughly
10–20 minutes (not measured on every run; see the timing printed below).

**Limits.** A Cloudflare quick tunnel gives a new URL every session and,
per Cloudflare's docs, does not support Server-Sent Events, so chat
streaming may arrive in one piece. ngrok supports SSE. Colab ends idle
sessions and caps free sessions at 12 hours. Colab's FAQ also lists "web
service offerings not related to interactive compute" among disallowed
activities, so treat this as a short-lived test server, not hosting.
""")

md("## 1. Settings")
code(r"""
# Where to get the server code. Change REPO_URL if the repo lives elsewhere.
REPO_URL = "https://github.com/Baffaabba/N-Atlas-SDK.git"   #@param {type:"string"}
REPO_BRANCH = ""          #@param {type:"string"}
# Which ASR languages to load: any of ha, ig, yo, en
ASR_LANGUAGES = "ha,ig,yo,en"   #@param {type:"string"}
LOAD_LLM = True           #@param {type:"boolean"}
# "auto" = ngrok if NGROK_AUTHTOKEN is set, else Cloudflare quick tunnel
TUNNEL = "auto"           #@param ["auto", "cloudflared", "ngrok"]
PORT = 8000
WORKDIR = "/content"
""")

md("## 2. Check the GPU")
code(r"""
import subprocess
print(subprocess.run(["nvidia-smi", "--query-gpu=name,memory.total,memory.used", "--format=csv"],
                     capture_output=True, text=True).stdout or "No GPU. Runtime -> Change runtime type -> T4 GPU.")
""")

md(r"""
## 3. Get the server code

Clones `REPO_URL`. If the repo is private or you have no network access to
it, skip this cell and run the **upload a zip** cell below instead.
""")
code(r"""
import os, subprocess
REPO_DIR = os.path.join(WORKDIR, "N-Atlas-SDK")
if os.path.isdir(os.path.join(REPO_DIR, ".git")):
    print(subprocess.run(["git", "-C", REPO_DIR, "pull", "--ff-only"], capture_output=True, text=True).stdout)
else:
    cmd = ["git", "clone", "--depth", "1"] + (["-b", REPO_BRANCH] if REPO_BRANCH else []) + [REPO_URL, REPO_DIR]
    r = subprocess.run(cmd, capture_output=True, text=True)
    print(r.stdout, r.stderr)
SERVER_DIR = os.path.join(REPO_DIR, "server")
print("server dir present:", os.path.isfile(os.path.join(SERVER_DIR, "pyproject.toml")))
""")

md(r"""
### 3b. Fallback: upload a zip

Only if the clone failed. Zip the repo (or just its `server/` folder) on your
computer and upload it here.
""")
code(r"""
RUN_UPLOAD = False   #@param {type:"boolean"}
if RUN_UPLOAD:
    import glob, os, zipfile
    from google.colab import files
    up = files.upload()
    name = next(iter(up))
    dest = os.path.join(WORKDIR, "upload")
    with zipfile.ZipFile(name) as z:
        z.extractall(dest)
    os.remove(name)
    found = [os.path.dirname(p) for p in glob.glob(os.path.join(dest, "**", "pyproject.toml"), recursive=True)
             if os.path.isdir(os.path.join(os.path.dirname(p), "natlas_server"))]
    assert found, "No server/pyproject.toml with natlas_server/ inside the zip"
    SERVER_DIR = found[0]
    print("SERVER_DIR =", SERVER_DIR)
""")

md("## 4. Install")
code(r"""
import subprocess, sys
r = subprocess.run([sys.executable, "-m", "pip", "install", "-q", "-e", f"{SERVER_DIR}[gpu]", "websockets"],
                   capture_output=True, text=True)
print(r.stdout[-3000:], r.stderr[-3000:])
assert r.returncode == 0, "pip install failed"
import importlib; importlib.invalidate_caches()
print("installed natlas-server from", SERVER_DIR)
""")

md(r"""
## 5. Secrets and API key

Reads `HF_TOKEN` (never printed) and the optional secrets. If
`NATLAS_API_KEYS` is not set, a random key is generated for this session.
""")
code(r"""
import os, secrets
from google.colab import userdata

def secret(name):
    try:
        v = userdata.get(name)
        return v.strip() if v else None
    except Exception:   # not defined, or notebook access not granted
        return None

HF_TOKEN = secret("HF_TOKEN")
assert HF_TOKEN, "Add the HF_TOKEN secret (key icon on the left) and allow notebook access."
API_KEYS = secret("NATLAS_API_KEYS")
NGROK_AUTHTOKEN = secret("NGROK_AUTHTOKEN")
NGROK_DOMAIN = secret("NGROK_DOMAIN")

if API_KEYS:
    API_KEY = API_KEYS.split(",")[0].strip()
    key_note = "from the NATLAS_API_KEYS secret (stable across sessions)"
else:
    API_KEY = "natlas-" + secrets.token_urlsafe(24)
    API_KEYS = API_KEY
    key_note = "generated for this session only (set the NATLAS_API_KEYS secret to keep it)"

if TUNNEL == "auto":
    TUNNEL_KIND = "ngrok" if NGROK_AUTHTOKEN else "cloudflared"
else:
    TUNNEL_KIND = TUNNEL
if TUNNEL_KIND == "ngrok":
    assert NGROK_AUTHTOKEN, "TUNNEL=ngrok needs the NGROK_AUTHTOKEN secret"
print("API key:", key_note)
print("tunnel:", TUNNEL_KIND, "| static domain:", NGROK_DOMAIN or "none")
""")

md(r"""
## 6. Memory budget (T4, 15 GB)

| Item | Approx. VRAM |
| --- | --- |
| N-ATLaS 8B, 4-bit NF4 (embeddings and LM head stay fp16) | ~5.7 GB |
| 4 × Whisper-small ASR, fp16 (242M params each) | ~2.0 GB |
| CUDA context | ~0.5 GB |
| KV cache at the 8k-token limit (32 layers × 8 KV heads × 128 × 2 × fp16) | ~1.0 GB |
| **Total** | **~9–10 GB of 15 GB** |

These are arithmetic estimates; the cell after start-up prints the real
`nvidia-smi` number. If you run out of memory, load fewer ASR languages
(`ASR_LANGUAGES`) or set `NATLAS_PRELOAD` to load ASR models on first use.
CPU RAM (12.7 GB on free Colab) is enough because weights load shard by shard.
""")

md("## 7. Start the server (with automatic restart)")
code(r"""
import os, subprocess, sys, threading, time, json, urllib.request

LOG_PATH = os.path.join(WORKDIR, "natlas_server.log")
server_env = dict(os.environ)
server_env.update({
    "HF_TOKEN": HF_TOKEN,
    "NATLAS_API_KEYS": API_KEYS,
    "NATLAS_BACKEND": "transformers",
    "NATLAS_LLM_BACKEND": "transformers" if LOAD_LLM else "none",
    "NATLAS_ASR_LANGUAGES": ASR_LANGUAGES,
    "NATLAS_PRELOAD": "all",
    "NATLAS_DB_PATH": os.path.join(WORKDIR, "natlas_users.sqlite3"),
    "NATLAS_HF_CACHE_DIR": os.path.join(WORKDIR, "hf_cache"),
    "NATLAS_LOG_LEVEL": "INFO",
})

def start_server():
    log = open(LOG_PATH, "ab")
    return subprocess.Popen([sys.executable, "-m", "natlas_server", "--host", "127.0.0.1", "--port", str(PORT)],
                            env=server_env, stdout=log, stderr=subprocess.STDOUT, cwd=WORKDIR)

def health():
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=5) as r:
            return json.load(r)
    except Exception:
        return None

try:
    server_proc
except NameError:
    server_proc = None
if server_proc is None or server_proc.poll() is not None:
    server_proc = start_server()
    restarts = 0

def watchdog():
    # Restart policy: if the server process exits, start it again (with back-off).
    global server_proc, restarts
    while True:
        time.sleep(10)
        if server_proc.poll() is not None:
            restarts += 1
            print(f"[watchdog] server exited with {server_proc.returncode}; restart #{restarts}")
            time.sleep(min(60, 5 * restarts))
            server_proc = start_server()

if not any(t.name == "natlas-watchdog" for t in threading.enumerate()):
    threading.Thread(target=watchdog, name="natlas-watchdog", daemon=True).start()

t0 = time.time()
last = None
while time.time() - t0 < 40 * 60:
    h = health()
    if h:
        states = {m.split("/")[-1]: v["state"] for m, v in h["models"].items()}
        line = f"{h['status']}: {states}"
        if line != last:
            print(f"[{time.time() - t0:5.0f}s] {line}")
            last = line
        if h["status"] in ("ok", "degraded"):
            break
    time.sleep(10)
print(f"Load time (this run): {time.time() - t0:.0f} s")
for m, v in (h or {}).get("models", {}).items():
    if v.get("error"):
        print("ERROR", m, "->", v["error"])
print(subprocess.run(["nvidia-smi", "--query-gpu=memory.used,memory.total", "--format=csv"],
                     capture_output=True, text=True).stdout)
""")

md(r"""
## 8. Public URL (tunnel)

- **cloudflared** (default, no account): new `*.trycloudflare.com` URL each
  session. Cloudflare documents that quick tunnels do not support SSE. In our
  own test on 2026-10-10 (fake backend, n=5) SSE did arrive token by token,
  and live WebSocket audio worked with `--protocol http2`, but neither is
  guaranteed by Cloudflare.
- **ngrok** (`NGROK_AUTHTOKEN` secret): supports SSE. With `NGROK_DOMAIN` the
  URL is the same every session (use this for the judges' check).
""")
code(r"""
import os, re, subprocess, time, threading

def start_cloudflared():
    binary = os.path.join(WORKDIR, "cloudflared")
    if not os.path.exists(binary):
        subprocess.run(["wget", "-q", "-O", binary,
                        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"],
                       check=True)
        os.chmod(binary, 0o755)
    log_path = os.path.join(WORKDIR, "cloudflared.log")
    # --protocol http2: in our test (2026-10-10) the default QUIC transport dropped
    # client-to-server WebSocket frames; http2 carried live audio and SSE fine.
    proc = subprocess.Popen([binary, "tunnel", "--no-autoupdate", "--protocol", "http2",
                             "--url", f"http://127.0.0.1:{PORT}"],
                            stdout=open(log_path, "w"), stderr=subprocess.STDOUT)
    for _ in range(60):
        time.sleep(1)
        m = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", open(log_path).read())
        if m:
            return proc, m.group(0)
    raise RuntimeError("cloudflared did not print a URL; see " + log_path)

def start_ngrok():
    subprocess.run([sys.executable, "-m", "pip", "install", "-q", "pyngrok"], check=True)
    from pyngrok import conf, ngrok
    conf.get_default().auth_token = NGROK_AUTHTOKEN
    ngrok.kill()
    kwargs = {"domain": NGROK_DOMAIN} if NGROK_DOMAIN else {}
    tunnel = ngrok.connect(addr=str(PORT), proto="http", **kwargs)
    return tunnel, tunnel.public_url.replace("http://", "https://")

if TUNNEL_KIND == "ngrok":
    tunnel_handle, BASE_URL = start_ngrok()
else:
    tunnel_handle, BASE_URL = start_cloudflared()

print("=" * 70)
print("BASE URL :", BASE_URL)
print("API KEY  :", API_KEY)
print("=" * 70)
print("Android : NAtlasConfig(baseUrl = \"%s\", apiKey = ...)" % BASE_URL)
print("Flutter : --dart-define=NATLAS_BASE_URL=%s --dart-define=NATLAS_API_KEY=..." % BASE_URL)
print("Health  :", BASE_URL + "/health")
""")

md("## 9. Smoke test")
code(r"""
import json, time, urllib.request

def call(path, body=None, headers=None, timeout=300):
    h = {"Authorization": f"Bearer {API_KEY}", "User-Agent": "natlas-colab-smoke/0.1", **(headers or {})}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE_URL + path, data=data, headers=h)
    return urllib.request.urlopen(req, timeout=timeout)

print("health:", json.load(call("/health"))["status"])
if LOAD_LLM:
    t = time.time()
    r = json.load(call("/v1/chat/completions", {"messages": [{"role": "user", "content": "Sannu! Yaya kake?"}],
                                                 "max_tokens": 64}))
    print(f"chat ({time.time() - t:.1f} s):", r["choices"][0]["message"]["content"][:300])
    print("usage:", r["usage"])

    # streaming: does the first token arrive before the end?
    t = time.time(); first = None; n = 0
    with call("/v1/chat/completions", {"stream": True, "max_tokens": 64,
                                       "messages": [{"role": "user", "content": "Tell me about Kano in two sentences."}]}) as resp:
        for raw in resp:
            line = raw.decode().strip()
            if line.startswith("data: ") and line != "data: [DONE]":
                n += 1
                first = first or time.time() - t
    total = time.time() - t
    print(f"stream: {n} chunks, first after {first:.2f} s, total {total:.2f} s",
          "(buffered by the tunnel)" if first and total - first < 0.2 and n > 3 else "")
""")

code(r"""
# Transcribe one public Hausa clip through the public URL. The clip is the first test row of
# SilencioNetwork/hausa-speech-transcribed (real, human-validated speech; CC-BY-NC-4.0), fetched
# through the Hugging Face datasets-server so nothing large is downloaded. Kept in memory only.
import io, json, time, uuid, urllib.request, wave
import numpy as np
import soundfile as sf

def hausa_sample():
    ds = "SilencioNetwork/hausa-speech-transcribed"
    try:
        d = json.load(urllib.request.urlopen(
            f"https://datasets-server.huggingface.co/first-rows?dataset={ds}&config=default&split=test", timeout=60))
        row = d["rows"][0]["row"]
        x, sr = sf.read(io.BytesIO(urllib.request.urlopen(row["audio"][0]["src"], timeout=120).read()), dtype="float32")
        if x.ndim > 1:
            x = x.mean(axis=1)
        if sr != 16000:
            x = np.interp(np.arange(int(len(x) * 16000 / sr)) * sr / 16000, np.arange(len(x)), x).astype(np.float32)
        return x, row.get("transcript", "")
    except Exception as e:
        print("Could not fetch the sample (", type(e).__name__, "); using a 2 s tone instead.")
        t = np.arange(32000) / 16000
        return (0.2 * np.sin(2 * np.pi * 220 * t)).astype(np.float32), "(tone)"

audio, reference = hausa_sample()
buf = io.BytesIO()
with wave.open(buf, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
    w.writeframes((np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes())
boundary = uuid.uuid4().hex
body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nha\r\n"
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.wav\"\r\n"
        f"Content-Type: audio/wav\r\n\r\n").encode() + buf.getvalue() + f"\r\n--{boundary}--\r\n".encode()
req = urllib.request.Request(BASE_URL + "/v1/audio/transcriptions", data=body, headers={
    "Authorization": f"Bearer {API_KEY}", "Content-Type": f"multipart/form-data; boundary={boundary}"})
t = time.time()
res = json.load(urllib.request.urlopen(req, timeout=300))
print(f"audio {len(audio)/16000:.1f} s, transcribed in {time.time() - t:.1f} s")
print("model     :", res["model"])
print("hypothesis:", res["text"])
print("reference :", reference)
""")

code(r"""
# Live microphone protocol over WebSocket, fed with the same sample in 200 ms frames.
import asyncio, json, time
import websockets

async def live_demo():
    url = BASE_URL.replace("https://", "wss://") + "/v1/audio/live?language=ha"
    hdr = {"Authorization": f"Bearer {API_KEY}"}
    try:
        ws = await websockets.connect(url, additional_headers=hdr, max_size=None)
    except TypeError:  # older websockets
        ws = await websockets.connect(url, extra_headers=hdr, max_size=None)
    async with ws:
        print(json.loads(await ws.recv()))
        pcm = (np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes()
        pcm += b"\0\0" * 16000  # 1 s of silence at the end
        async def sender():
            for i in range(0, len(pcm), 6400):
                await ws.send(pcm[i:i + 6400]); await asyncio.sleep(0.2)
            await ws.send(json.dumps({"type": "stop"}))
        task = asyncio.create_task(sender())
        t = time.time()
        async for msg in ws:
            ev = json.loads(msg)
            print(f"{time.time() - t:5.1f}s", ev)
            if ev["type"] in ("done", "error"):
                break
        await task

await live_demo()
""")

md(r"""
## 10. Status (re-run any time)

Shows health, the tunnel URL, restarts and the last log lines (metadata only:
no audio or text is logged). Keep this tab open; Colab ends idle sessions.
""")
code(r"""
import json, subprocess
h = health()
print("BASE URL:", BASE_URL, "| tunnel:", TUNNEL_KIND, "| server restarts:", restarts)
print(json.dumps(h, indent=1) if h else "server not answering")
print(subprocess.run(["tail", "-n", "20", LOG_PATH], capture_output=True, text=True).stdout)
""")

md("## 11. Stop")
code(r"""
STOP = False   #@param {type:"boolean"}
if STOP:
    try:
        from pyngrok import ngrok; ngrok.kill()
    except Exception:
        pass
    try:
        tunnel_handle.terminate()
    except Exception:
        pass
    server_proc.terminate()
    print("stopped (the watchdog will restart the server unless you restart the runtime)")
""")

nb = new_notebook(cells=cells, metadata={
    "accelerator": "GPU",
    "colab": {"provenance": [], "gpuType": "T4"},
    "kernelspec": {"display_name": "Python 3", "name": "python3"},
    "language_info": {"name": "python"},
})

if __name__ == "__main__":
    nbformat.validate(nb)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        nbformat.write(nb, f)
    # read back to prove it parses
    nbformat.validate(nbformat.read(OUT, as_version=4))
    print("wrote", OUT, "cells:", len(nb.cells))
    sys.exit(0)
