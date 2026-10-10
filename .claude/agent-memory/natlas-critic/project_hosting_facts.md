---
name: project-hosting-facts
description: Web-checked hosting facts for the judges' server (2026-10-11) - HF Docker Spaces need PRO, Modal $30 credits, Oracle card/capacity, QuantFactory GGUF ungated
metadata:
  type: project
---

Checked 2026-10-11 (re-check before relying on them):
- HF Spaces: CPU Basic is 2 vCPU / 16 GB / 50 GB disk that is wiped on restart. **Creating Gradio or Docker Spaces now needs a paid plan** (PRO is $9/month). Free hardware sleeps after 48 h idle; a visit wakes it.
- Modal Starter: $0 base plus $30/month free credits. T4 costs $0.000164/s (~$0.59/h), and CPU and memory are billed on top (~$0.80/h total for 2 cores and 16 GiB). Need max_containers=1 so concurrent requests don't start extra GPU containers.
- Oracle Always Free A1: needs a real credit card (no prepaid or virtual cards). "Out of host capacity" errors are common.
- `QuantFactory/N-ATLaS-GGUF` is ungated and its card has no licence field. It re-uploads gated NCAIR weights, so using it invites a provenance question.
- The Colab FAQ bans web-service offerings (natlas-backend memory has the details).

**Why:** judges' integration check is 15–17 Oct. In the 2026-10-11 checkpoint I recommended Modal with the existing transformers backend. Baffa had not decided at the time of writing.
**How to apply:** check DECISIONS.md for Baffa's hosting choice before reviewing again. Don't re-recommend a free HF Docker Space.
