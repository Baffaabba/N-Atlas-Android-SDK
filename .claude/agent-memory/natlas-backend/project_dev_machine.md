---
name: project-dev-machine
description: Dev laptop limits for backend work (2-core i5-7200U, 12 GB RAM, no usable GPU, no ffmpeg, slow network, CPU shared with desktop apps)
metadata:
  type: project
---

Dev machine (checked 2026-10-10): Intel i5-7200U, 2 cores / 4 threads, 11.9 GB RAM, no usable GPU (nvidia-smi needs
admin), no ffmpeg on PATH, Python 3.11.0. Server venv at `server/.venv` (torch 2.14 CPU, transformers 5.19 — v5).
pip installs of torch took 10+ min. The CPU is often saturated by other apps (Edge WebView, Teams, VS Code, other
agents' builds): a 26.7 s Hausa clip took ~900 s wall in one run with only a fraction of that as process CPU.

**Why:** latency numbers from this box are not representative; long jobs need run_in_background.
**How to apply:** never publish latency from this laptop as a product number; measure on the Colab T4 instead and
state hardware, n, median/p90. The full pytest suite takes ~70 s here.

Related: [[project-tunnels-and-colab-policy]]
