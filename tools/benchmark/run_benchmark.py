"""Benchmark a running N-ATLAS server with real speech and real prompts.

Measures, against the same endpoints the Kotlin and Flutter SDK call:

- /health: which NCAIR1 models are loaded, where, and how
- transcription (POST /v1/audio/transcriptions): word and character error
  rate against human reference transcripts, and time per clip
- live mic (WS /v1/audio/live): one clip per language streamed at real-time
  speed in 100 ms frames; time from `stop` to the final text
- chat (POST /v1/chat/completions, stream): time to first token and tokens
  per second, with prompts in Hausa, Igbo, Yoruba and English

Clips come from tools/benchmark/fetch_samples.py. The key is read from the
NATLAS_API_KEY environment variable and never written to the report.

    NATLAS_BASE_URL=https://<id>.gradio.live/natlas NATLAS_API_KEY=... \
      python tools/benchmark/run_benchmark.py --clips <dir> --out <report.json>
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import statistics
import sys
import time
import unicodedata
from datetime import datetime, timezone

import httpx
import numpy as np
import soundfile as sf
import websockets

MODEL_BY_LANG = {
    "ha": "NCAIR1/Hausa-ASR",
    "ig": "NCAIR1/Igbo-ASR",
    "yo": "NCAIR1/Yoruba-ASR",
    "en": "NCAIR1/NigerianAccentedEnglish",
}

CHAT_PROMPTS = [
    ("ha", "Sannu! Ka ba ni shawarwari uku kan yadda manomi zai kare amfanin gonarsa daga fari."),
    ("ig", "Ndewo! Kọwaa n'ụzọ dị mfe ihe mere o ji dị mkpa ịsa aka tupu iri nri."),
    ("yo", "Ẹ kú àárọ̀! Ṣàlàyé ní ṣókí bí a ṣe lè tọ́jú omi mímu nílé."),
    ("en", "Give a small trader in Kano three simple tips for keeping daily sales records."),
]


# -- text normalisation and error rates ------------------------------------
def normalise(text: str) -> str:
    text = unicodedata.normalize("NFC", text.lower())
    text = text.replace("’", "'").replace("‘", "'")
    # drop punctuation, keep letters (with diacritics), digits and apostrophes
    text = "".join(ch if (unicodedata.category(ch)[0] in "LNM" or ch in " '") else " " for ch in text)
    return re.sub(r"\s+", " ", text).strip()


def edit_distance(a: list, b: list) -> int:
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def rates(ref: str, hyp: str) -> tuple[int, int, int, int]:
    r, h = normalise(ref), normalise(hyp)
    return edit_distance(r.split(), h.split()), len(r.split()), edit_distance(list(r), list(h)), len(r)


def summary(values: list[float]) -> dict:
    if not values:
        return {}
    s = sorted(values)
    p90 = s[min(len(s) - 1, int(round(0.9 * (len(s) - 1))))]
    return {"n": len(s), "median": round(statistics.median(s), 3), "p90": round(p90, 3),
            "min": round(s[0], 3), "max": round(s[-1], 3)}


# -- benchmarks --------------------------------------------------------------
def bench_transcribe(client: httpx.Client, clips: list[dict], clip_dir: str) -> dict:
    per_lang: dict[str, dict] = {}
    rows = []
    for c in clips:
        path = os.path.join(clip_dir, c["file"])
        info = sf.info(path)
        t0 = time.perf_counter()
        with open(path, "rb") as fh:
            r = client.post("/v1/audio/transcriptions",
                            data={"language": c["language"], "response_format": "json"},
                            files={"file": (c["file"], fh, "audio/wav")})
        dt = time.perf_counter() - t0
        ok = r.status_code == 200
        hyp = r.json().get("text", "") if ok else ""
        we, wn, ce, cn = rates(c["reference"], hyp)
        row = {"language": c["language"], "source": c["source"], "file": c["file"],
               "audio_seconds": round(info.duration, 2), "status": r.status_code,
               "model": r.json().get("model") if ok else None,
               "seconds": round(dt, 3), "reference": c["reference"], "hypothesis": hyp,
               "wer": round(we / max(wn, 1), 3), "cer": round(ce / max(cn, 1), 3)}
        if not ok:
            row["error"] = r.text[:300]
        rows.append(row)
        agg = per_lang.setdefault(c["language"], {"we": 0, "wn": 0, "ce": 0, "cn": 0, "t": [], "rtf": [], "ok": 0, "n": 0})
        agg["n"] += 1
        if ok:
            agg["ok"] += 1
            agg["we"] += we; agg["wn"] += wn; agg["ce"] += ce; agg["cn"] += cn
            agg["t"].append(dt); agg["rtf"].append(dt / max(info.duration, 0.01))
        print(f"  [{c['language']}] {row['status']} {dt:5.2f}s wer={row['wer']:.2f}  {hyp[:70]!r}")
    langs = {}
    for lang, a in per_lang.items():
        langs[lang] = {"model": MODEL_BY_LANG[lang], "clips": a["n"], "succeeded": a["ok"],
                       "wer": round(a["we"] / max(a["wn"], 1), 3), "cer": round(a["ce"] / max(a["cn"], 1), 3),
                       "request_seconds": summary(a["t"]), "real_time_factor": summary(a["rtf"])}
    return {"per_language": langs, "clips": rows}


async def _live_one(ws_url: str, key: str, path: str, lang: str) -> dict:
    audio, sr = sf.read(path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != 16000:
        idx = np.linspace(0, len(audio) - 1, int(len(audio) * 16000 / sr))
        audio = np.interp(idx, np.arange(len(audio)), audio).astype("float32")
    pcm = (np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes()
    frame = 3200  # 100 ms
    events, partials = [], []
    t_first_partial = None
    async with websockets.connect(f"{ws_url}/v1/audio/live?language={lang}",
                                  additional_headers={"Authorization": f"Bearer {key}"},
                                  max_size=None, open_timeout=60) as ws:
        ready = json.loads(await asyncio.wait_for(ws.recv(), 120))
        events.append(ready["type"])
        if ready["type"] != "ready":
            return {"language": lang, "error": ready}
        t_start = time.perf_counter()

        async def reader():
            nonlocal t_first_partial
            final_text, t_final = [], None
            async for msg in ws:
                ev = json.loads(msg)
                events.append(ev["type"])
                if ev["type"] == "partial":
                    partials.append(ev.get("text", ""))
                    if t_first_partial is None:
                        t_first_partial = time.perf_counter() - t_start
                elif ev["type"] == "final":
                    final_text.append(ev.get("text", ""))
                    t_final = time.perf_counter()
                elif ev["type"] in ("done", "error"):
                    if ev["type"] == "error":
                        final_text.append(f"<error {ev}>")
                    break
            return " ".join(final_text).strip(), t_final

        task = asyncio.create_task(reader())
        for i in range(0, len(pcm), frame):
            await ws.send(pcm[i:i + frame])
            await asyncio.sleep(0.1)
        t_stop = time.perf_counter()
        await ws.send(json.dumps({"type": "stop"}))
        text, t_final = await asyncio.wait_for(task, 180)
    return {"language": lang, "audio_seconds": round(len(audio) / 16000, 2),
            "first_partial_seconds": round(t_first_partial, 2) if t_first_partial else None,
            "partials": len(partials),
            "stop_to_final_seconds": round(t_final - t_stop, 2) if t_final else None,
            "final_text": text, "event_types": sorted(set(events))}


def bench_live(base: str, key: str, clips: list[dict], clip_dir: str) -> list[dict]:
    ws_url = base.replace("https://", "wss://").replace("http://", "ws://")
    out = []
    seen = set()
    for c in clips:
        if c["language"] in seen:
            continue
        seen.add(c["language"])
        try:
            r = asyncio.run(_live_one(ws_url, key, os.path.join(clip_dir, c["file"]), c["language"]))
            we, wn, _, _ = rates(c["reference"], r.get("final_text", ""))
            r["wer"] = round(we / max(wn, 1), 3)
        except Exception as e:  # report, keep going
            r = {"language": c["language"], "error": f"{type(e).__name__}: {e}"}
        print(f"  live [{c['language']}] {r}")
        out.append(r)
    return out


def bench_chat(client: httpx.Client, rounds: int) -> dict:
    rows = []
    for _ in range(rounds):
        for lang, prompt in CHAT_PROMPTS:
            body = {"messages": [{"role": "user", "content": prompt}], "stream": True,
                    "max_tokens": 200, "stream_options": {"include_usage": True}}
            t0 = time.perf_counter()
            ttft, text, usage, chunks, status = None, [], None, 0, None
            with client.stream("POST", "/v1/chat/completions", json=body) as r:
                status = r.status_code
                for line in r.iter_lines():
                    if not line.startswith("data: "):
                        continue
                    data = line[6:]
                    if data == "[DONE]":
                        break
                    ev = json.loads(data)
                    if ev.get("usage"):
                        usage = ev["usage"]
                    for ch in ev.get("choices", []):
                        piece = ch.get("delta", {}).get("content")
                        if piece:
                            if ttft is None:
                                ttft = time.perf_counter() - t0
                            text.append(piece)
                            chunks += 1
            total = time.perf_counter() - t0
            toks = (usage or {}).get("completion_tokens") or chunks
            row = {"language": lang, "status": status, "prompt": prompt,
                   "time_to_first_token_seconds": round(ttft, 3) if ttft else None,
                   "total_seconds": round(total, 3), "completion_tokens": toks,
                   "sse_chunks": chunks,
                   "tokens_per_second": round(toks / max(total - (ttft or 0), 0.01), 2),
                   "reply": "".join(text)}
            print(f"  chat [{lang}] {status} ttft={row['time_to_first_token_seconds']} "
                  f"tok/s={row['tokens_per_second']} {row['reply'][:60]!r}")
            rows.append(row)
    ok = [r for r in rows if r["status"] == 200 and r["time_to_first_token_seconds"]]
    return {"runs": len(rows), "succeeded": len(ok),
            "time_to_first_token_seconds": summary([r["time_to_first_token_seconds"] for r in ok]),
            "tokens_per_second": summary([r["tokens_per_second"] for r in ok]),
            "replies": rows}


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--clips", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--chat-rounds", type=int, default=3)
    ap.add_argument("--skip-live", action="store_true")
    args = ap.parse_args()
    base = os.environ["NATLAS_BASE_URL"].rstrip("/")
    key = os.environ["NATLAS_API_KEY"]
    clips = json.load(open(os.path.join(args.clips, "manifest.json"), encoding="utf-8"))

    headers = {"Authorization": f"Bearer {key}", "User-Agent": "natlas-benchmark/0.1",
               "X-NAtlas-User": "benchmark-script"}
    report = {"date_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
              "server": "Google Colab, Gradio share link (/natlas), " + base.split("//")[1].split(".")[1],
              "client": "Windows 11 laptop, home broadband, Lagos/Nigeria -> Colab (US)"}
    with httpx.Client(base_url=base, headers=headers, timeout=300) as client:
        t0 = time.perf_counter()
        h = client.get("/health")
        report["health"] = {"http_status": h.status_code, "seconds": round(time.perf_counter() - t0, 3), "body": h.json()}
        print("health", h.status_code)
        print("transcription ...")
        report["transcription"] = bench_transcribe(client, clips, args.clips)
        print("chat ...")
        report["chat"] = bench_chat(client, args.chat_rounds)
    if not args.skip_live:
        print("live ...")
        report["live"] = bench_live(base, key, clips, args.clips)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=1)
    print("wrote", args.out)


if __name__ == "__main__":
    main()
