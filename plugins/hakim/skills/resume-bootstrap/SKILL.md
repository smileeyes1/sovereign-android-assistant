---
name: resume-bootstrap
description: Resume Hakim from the newest verified live and durable state after any chat, model, or session change.
---

When the user's request concerns Hakim, its Android device, unfinished Hakim work, video production, or continuing a prior Hakim task, call `resume_hakim` first whenever the current turn does not already contain a fresh result from that tool.

Treat the returned evidence in this exact precedence:
1. fresh live preflight;
2. durable continuation checkpoint and pending operation tokens;
3. current bridge version and capabilities;
4. prior conversation descriptions.

Never ask the user to restate Hakim state that `resume_hakim` can recover. Never replay a pending or completed mutation merely because the chat, model, or session changed. Reuse operation tokens and continue from the durable checkpoint.

If live device state is unavailable, preserve the recovered durable state and report only the actual blocker. Do not invent connection or execution success.

A renderer capability means only what its evidence proves. A technically verified fallback MP4 is not equivalent to generative cinematic approval unless the returned gates explicitly prove that stronger claim.
