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
- Candidate versionCode: `20105`.
- Branch: `feature/hakim-20105-free-oauth-no-handoff`.
- 20104 remains the latest Quranic-governance source/CI predecessor; its values/technical boundary is inherited unchanged.
- User-eye evidence showed a remaining product defect: without a configured direct engine, normal Hakim chat still opened ChatGPT and exposed Hakim's internal governed task envelope.
- 20105 closes that product defect in FREE_ONLY mode. Normal non-local chat may use a configured direct free engine, but if none exists it enters FREE_ENGINE_SETUP and never auto-opens ChatGPT/Gemini/Claude/DeepSeek.
- OpenRouter free onboarding now uses official OAuth PKCE S256 with an ephemeral 127.0.0.1 callback. The user performs one unavoidable login/authorization; the returned key is stored in AndroidKeyStore and the pending Hakim intent resumes automatically.
- OpenRouter remains pinned to `openrouter/free`; no paid model is selected by this path. Free inference is quota-limited and therefore cannot be promised as unlimited.
- External provider app/web handoff remains available only as degraded/non-free/manual infrastructure; it is not the normal FREE_ONLY conversation path and never counts as completion.
- Status: SOURCE/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED until the exact signed APK proves one-click authorization and a non-local reply returning inside Hakim.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
