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
- Candidate versionCode: `20106`.
- Branch: `feature/hakim-20106-resilient-free-completion`.
- 20105 remains the signed predecessor that established FREE_ONLY, one-click OpenRouter OAuth/PKCE, no automatic ChatGPT handoff, and inherited Quranic/Sunnah value governance.
- New resilience requirement comes from repeated real-world evidence that a single AI provider can remain in a long thinking state without producing a usable result.
- 20106 adds bounded no-stall behavior for direct free engines: first visible output <= 25s target, no visible progress <= 45s, total call <= 120s, then automatic retryable failure/failover.
- Repeated retryable failure opens a 10-minute circuit breaker after two consecutive failures, so Hakim does not keep choosing the same unhealthy free engine.
- The Wisdom Matrix excludes engines in cooldown and continues to hard-gate FREE_ONLY before quality scoring.
- Quran/Sunnah governance remains values/limits only: truthfulness, verification, justice, trust, mercy, privacy and no-harm. Divine names, attributes and Qur'anic letters are never treated as hidden technical intelligence mechanisms.
- Status: SIGNING/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED until the exact signed APK proves free OAuth/direct reply and a controlled stall/failover case on the phone.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
