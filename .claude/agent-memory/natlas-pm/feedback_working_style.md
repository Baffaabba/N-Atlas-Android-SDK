---
name: pm-working-style
description: How Baffa wants the PM agent to operate - decide and record instead of asking, no git, do not edit builder directories
metadata:
  type: feedback
---

Decide and record in docs/DECISIONS.md (marked "made by natlas-pm under the delegation"); do not ask Baffa questions. Do not run git and do not edit server/, android/ or flutter/ while builders work in parallel. Never read `.env` values.

**Why:** Baffa delegated all decisions on 2026-10-10 ("stop asking me questions"); spending money or creating accounts still needs him.
**How to apply:** end reports with the status table plus at most two Baffa-only items. See [[project-naic-submission]].
