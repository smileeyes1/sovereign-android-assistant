# Hakim component baselines and promotion gates

## Cloud / ChatGPT bridge — LAST_VERIFIED_BASELINE
- Verified production commit: `c84359e422dad0aa205f59a153a1f29d7f4c4e81`.
- Railway production deployment of that commit is SUCCESS.
- External acceptance passed: `/health` = `ok:true`; exact ChatGPT connector callback accepted.
- Negative regression passed: wrong host, userinfo injection, lookalike path, query mutation, and unknown scope remain rejected.

This cloud baseline is the rollback target for bridge/OAuth work until a successor passes the same relevant production checks.

## Android field lineage
- Historical last promoted field baseline: versionCode `20087`, package `ps.hakim.stable`, D1 signer lineage.
- Version `20090` was installed later but was NOT PROMOTED after UX acceptance failed.
- Exact source-to-APK correspondence for the field 20087/20090 build is NOT PROVEN from the currently connected public repository.
- Modern public Android source used for this candidate starts from commit `d3ae6946b2d63e402a1b597dbc93ab5d58efa12f` on `feature/hakim-capability-kernel-v1` (source versionCode 20088).
- Field evidence on 20091: Google Play Protect hard-blocked sideload installation while Accessibility/Notification Listener surfaces were declared.
- Field evidence on 20092: the prior Play Protect hard block was removed, but Hakim exposed an impossible unknown-app-source/install permission prompt.
- Field evidence on 20093: that stale install-source prompt was removed; a new defect remained—typing «مرحبا» and pressing «أنجز» opened ChatGPT and exposed the long governed prompt instead of answering inside Hakim.

## Current Android candidate
- Candidate versionCode: `20103`.
- Branch: `feature/hakim-20103-free-local-core`.
- 20102 proved in CI that a direct in-app provider boundary can exist, but its Gemini path still requires a provider API key and therefore is optional, not the zero-cost default.
- 20103 adds the zero-API path: LiteRT-LM + Gemma 4 E2B on-device. After one verified model download, normal text inference can run without subscription, API key, or network.
- The model download is deliberately unmetered-only by default because the reference model is about 2.6 GB. SHA-256 must match before routing any task to it.
- The router prioritizes the verified local engine for supported text tasks, then optional official direct providers, then degraded external handoff.
- Quranic values are encoded as governance principles (verification, justice, trust, consultation, epistemic discipline) and explicitly are not treated as magical computational primitives, secret numerology, or guarantees of technical success.
- Status starts as SOURCE/CI CANDIDATE ONLY. Local runtime performance, thermals, memory fit, Arabic quality and the exact signed APK must be field-proven before promotion.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
