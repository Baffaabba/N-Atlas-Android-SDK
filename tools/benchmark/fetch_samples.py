"""Fetch a small set of real, labelled speech clips for the ASR benchmark.

Sources (public, with reference transcripts written by people):

- Hausa, Igbo, Yoruba: Google FLEURS, dev split (CC-BY-4.0)
  https://huggingface.co/datasets/google/fleurs
- Nigerian-accented English: AfriSpeech-200, dev split, Nigerian speakers
  (CC-BY-NC-SA-4.0) https://huggingface.co/datasets/intronhealth/afrispeech-200

Only the first N clips of each archive are read: the .tar.gz is streamed and
the download stops once N clips with a transcript are found. Audio is written
to the output folder (keep it out of git; *.wav is git-ignored).

    python tools/benchmark/fetch_samples.py --out <dir> --n 10
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import http.client
import os
import tarfile
import time
import urllib.request

FLEURS = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/{cfg}/{f}"
AFRI = "https://huggingface.co/datasets/intronhealth/afrispeech-200/resolve/main/{f}"

SETS = [
    # (language code for the SDK, source, config/accent)
    ("ha", "fleurs", "ha_ng"),
    ("ig", "fleurs", "ig_ng"),
    ("yo", "fleurs", "yo_ng"),
    ("en", "afrispeech", "hausa"),
    ("en", "afrispeech", "yoruba"),
    ("en", "afrispeech", "igbo"),
]


def _open(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "natlas-sdk-benchmark/0.1"})
    tok = os.environ.get("HF_TOKEN")
    if tok:
        req.add_header("Authorization", f"Bearer {tok}")
    for attempt in range(4):
        try:
            return urllib.request.urlopen(req, timeout=120)
        except (OSError, http.client.HTTPException):
            if attempt == 3:
                raise
            time.sleep(3 * (attempt + 1))


def fleurs_refs(cfg: str) -> dict[str, str]:
    text = _open(FLEURS.format(cfg=cfg, f="dev.tsv")).read().decode("utf-8")
    refs = {}
    for line in text.splitlines():
        cols = line.split("\t")
        if len(cols) >= 3:
            refs.setdefault(cols[1], cols[2])  # file name -> raw transcription
    return refs


def afri_refs(accent: str) -> dict[str, str]:
    text = _open(AFRI.format(f=f"transcripts/{accent}/dev.csv")).read().decode("utf-8")
    refs = {}
    for row in csv.DictReader(io.StringIO(text)):
        if row.get("origin") == "nigerian":
            refs[os.path.basename(row["audio_paths"])] = row["transcript"]
    return refs


def stream_clips(url: str, refs: dict[str, str], n: int, out_dir: str, prefix: str) -> list[dict]:
    got = []
    with _open(url) as resp, tarfile.open(fileobj=resp, mode="r|gz") as tar:
        for member in tar:
            name = os.path.basename(member.name)
            if not member.isfile() or name not in refs:
                continue
            data = tar.extractfile(member).read()
            path = os.path.join(out_dir, f"{prefix}_{name}")
            with open(path, "wb") as fh:
                fh.write(data)
            got.append({"file": os.path.basename(path), "reference": refs[name]})
            if len(got) >= n:
                break
    return got


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--n", type=int, default=10, help="clips per language (English: split across accents)")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    manifest = []
    for lang, source, cfg in SETS:
        if source == "fleurs":
            refs = fleurs_refs(cfg)
            url = FLEURS.format(cfg=cfg, f="audio/dev.tar.gz")
            n = args.n
            meta = {"source": "google/fleurs dev", "licence": "CC-BY-4.0"}
        else:
            refs = afri_refs(cfg)
            url = AFRI.format(f=f"audio/{cfg}/dev/dev_{cfg}_0.tar.gz")
            n = max(1, round(args.n / 3))
            meta = {"source": f"intronhealth/afrispeech-200 dev, {cfg}-accented", "licence": "CC-BY-NC-SA-4.0"}
        clips = stream_clips(url, refs, n, args.out, f"{lang}_{cfg}")
        for c in clips:
            manifest.append({"language": lang, **meta, **c})
        print(f"{lang} {cfg}: {len(clips)} clips")

    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=1)
    print(f"wrote {len(manifest)} clips to {args.out}")


if __name__ == "__main__":
    main()
