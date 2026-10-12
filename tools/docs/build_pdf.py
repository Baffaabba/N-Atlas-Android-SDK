"""Build the submission PDFs from the Markdown docs in this repo.

Needs the `markdown` package and Microsoft Edge or Google Chrome (headless
print to PDF). Output goes to dist/ (git-ignored).

    python tools/docs/build_pdf.py
"""

from __future__ import annotations

import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "dist"

ATTRIBUTION = ("N-ATLAS is an initiative of the Federal Ministry of Communications, Innovation and "
               "Digital Economy, and powered by Awarri Technologies.")
REPO = "https://github.com/Baffaabba/N-Atlas-Android-SDK"

BOOKS = {
    "NAtlas-SDK-Technical-Documentation.pdf": {
        "title": "N-ATLAS SDK for Android",
        "subtitle": "Technical documentation: architecture, setup and usage",
        "parts": [
            "README.md",
            "docs/ARCHITECTURE.md",
            "docs/API.md",
            "server/README.md",
            "docs/N-ATLAS-INTEGRATION.md",
            "docs/validation/RESULTS.md",
        ],
    },
    "NAtlas-SDK-Integration-Evidence.pdf": {
        "title": "N-ATLAS SDK for Android",
        "subtitle": "Evidence of N-ATLAS integration: live server, real requests, benchmark",
        "parts": [
            "docs/N-ATLAS-INTEGRATION.md",
            "docs/validation/RESULTS.md",
            "docs/validation/measurements/2026-10-12-kotlin-unit-tests.md",
            "docs/validation/measurements/2026-10-12-flutter-tests.md",
            "docs/validation/measurements/2026-10-12-server-tests.md",
        ],
    },
    "NAtlas-SDK-Validation-Evidence.pdf": {
        "title": "N-ATLAS SDK for Android",
        "subtitle": "Validation: real speech data, live benchmark and tests",
        "parts": [
            "docs/validation/RESULTS.md",
            "docs/validation/measurements/2026-10-12-benchmark-clips.md",
            "docs/validation/README.md",
        ],
    },
}

CSS = """
@page { size: A4; margin: 18mm 16mm; }
body { font-family: 'Segoe UI', 'Noto Sans', Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #1a1a1a; }
h1 { font-size: 20pt; border-bottom: 2px solid #0b6b3a; padding-bottom: 4px; margin-top: 0; }
h2 { font-size: 14pt; color: #0b6b3a; margin-top: 18px; }
h3 { font-size: 12pt; }
code, pre { font-family: Consolas, 'Courier New', monospace; font-size: 8.8pt; }
pre { background: #f4f6f5; border: 1px solid #dde3df; padding: 8px; white-space: pre-wrap; word-break: break-word; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 9pt; }
th, td { border: 1px solid #c9d1cc; padding: 4px 6px; vertical-align: top; text-align: left; }
th { background: #e9f1ec; }
blockquote { border-left: 3px solid #0b6b3a; margin-left: 0; padding-left: 10px; color: #333; }
.part { page-break-before: always; }
.cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; }
.cover h1 { font-size: 30pt; border: none; }
.cover .sub { font-size: 15pt; color: #0b6b3a; margin-bottom: 30px; }
.cover .meta { font-size: 11pt; line-height: 1.8; }
.src { font-size: 8pt; color: #777; margin-bottom: 4px; }
"""


def render(md_text: str) -> str:
    md_text = re.sub(r"```mermaid.*?```", "", md_text, flags=re.S)  # PDFs can't run mermaid; ASCII diagram stays
    return markdown.markdown(md_text, extensions=["tables", "fenced_code", "sane_lists"])


def find_browser() -> str:
    for p in [r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
              r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
              r"C:\Program Files\Google\Chrome\Application\chrome.exe",
              shutil.which("chromium") or "", shutil.which("google-chrome") or ""]:
        if p and os.path.exists(p):
            return p
    sys.exit("Need Edge or Chrome to print PDFs")


def build(name: str, book: dict, browser: str) -> Path:
    body = [f"""<div class="cover"><h1>{html.escape(book['title'])}</h1>
<div class="sub">{html.escape(book['subtitle'])}</div>
<div class="meta">NAIC 2026 &middot; Problem Statement 01: Developer Infrastructure<br>
Innovation &amp; Enterprise track<br>Repository: {REPO}<br>Version 0.1.0 &middot; 12 October 2026<br><br>
<em>{html.escape(ATTRIBUTION)}</em><br>
Free use of N-ATLAS is capped at 1,000 active end users in a rolling 30 days; beyond that a commercial
licence from Awarri is needed (datasupport@awarri.com).</div></div>"""]
    for part in book["parts"]:
        path = ROOT / part
        if not path.exists():
            print(f"  skip missing {part}")
            continue
        body.append(f'<div class="part"><div class="src">{html.escape(part)}</div>{render(path.read_text(encoding="utf-8"))}</div>')
    doc = f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(book['subtitle'])}</title><style>{CSS}</style></head><body>{''.join(body)}</body></html>"
    DIST.mkdir(exist_ok=True)
    out = DIST / name
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "doc.html"
        src.write_text(doc, encoding="utf-8")
        subprocess.run([browser, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                        f"--user-data-dir={tmp}\\profile", f"--print-to-pdf={out}", src.as_uri()],
                       check=True, capture_output=True, timeout=180)
    print(f"  {out} ({out.stat().st_size // 1024} KB)")
    return out


def main() -> None:
    browser = find_browser()
    for name, book in BOOKS.items():
        build(name, book, browser)


if __name__ == "__main__":
    main()
