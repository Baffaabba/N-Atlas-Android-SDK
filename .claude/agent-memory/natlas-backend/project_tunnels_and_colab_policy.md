---
name: project-tunnels-and-colab-policy
description: Tested tunnel behaviour (cloudflared QUIC drops WS upstream; http2 works; SSE worked despite docs) and the Colab FAQ policy risk for serving a public API
metadata:
  type: project
---

Tested 2026-10-10 on the dev laptop with cloudflared 2026.10.0 against the fake server:
- Default QUIC transport: SSE streamed piece by piece (n=5), but **client-to-server WebSocket frames never arrived**
  (server saw 0 s of audio; client hit keepalive timeout).
- `--protocol http2`: both SSE and live WS worked (first partial median 1.62 s vs 1.17 s localhost).
- Cloudflare docs still say quick tunnels do not support SSE, so it is not guaranteed.

Colab FAQ (checked 2026-10-10) disallows on all managed runtimes "web service offerings not related to interactive
compute with Colab" and "connecting to remote proxies"; free sessions max 12 h. PM chose Colab + ngrok static domain
for the 15–17 Oct judges' check (DECISIONS 2026-10-10) — this policy is a live risk to that plan.

**Why:** these facts are not in code and were expensive to find.
**How to apply:** keep `--protocol http2` in the notebook; flag the Colab policy whenever judges' hosting comes up;
any paid fallback (Modal/HF Endpoints/RunPod) needs Baffa's approval.

Related: [[project-hf-access-and-models]], [[project-dev-machine]]
