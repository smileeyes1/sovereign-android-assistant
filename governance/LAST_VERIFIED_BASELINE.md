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
- Branch: `feature/hakim-20103-free-intelligence-matrix`.
- Field evidence on 20100 remains the latest proven Android layout success: composer + «أنجز/إلغاء» stayed visible above IME/system navigation on the real phone.
- 20101 intent-direction and 20102 direct-model streaming are inherited.
- 20103 makes monetary safety a hard routing rule: FREE_ONLY defaults true and no paid/unknown engine may be selected silently.
- OpenRouter `openrouter/free` is added as a direct in-app text/image engine with zero token price; Gemini is eligible in FREE_ONLY mode only after explicit Free Tier confirmation.
- HakimWisdomMatrix ranks eligible engines by zero-cost certainty, quality, learned reliability, privacy, latency and modality fit. HakimEngineTelemetry learns only success/failure and latency.
- Retryable direct-engine failure automatically switches once through remaining eligible free engines under the bounded Executive Loop. It never falls into an unbounded retry loop.
- Recent Hakim conversation context is bounded and carried into direct-engine instructions so free engines can sustain multi-turn chat without opening another app.
- Product V1 FINAL promotion remains fail-closed until the exact signed APK proves direct chat, multimodal return, failover/blocker behavior and same-artifact acceptance on the phone.
- Status: SOURCE/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
