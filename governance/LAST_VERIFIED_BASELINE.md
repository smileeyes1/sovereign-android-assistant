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
- Candidate versionCode: `20102`.
- Branch: `feature/hakim-20102-gemini-direct`.
- Field evidence on 20100 remains the latest proven Android layout success: composer + «أنجز/إلغاء» stayed visible above IME/system navigation on the real phone.
- 20101 intent-direction logic is inherited: each request gets an intent-specific completion contract and a compact governed instruction; private chain-of-thought is not exposed.
- 20102 adds the first true DIRECT_MODEL path: official Gemini Interactions API streaming replies back into the Hakim conversation instead of opening a provider app.
- The direct engine supports text plus bounded inline image/document/audio/video inputs, multi-turn continuity through `previous_interaction_id`, cancellation, and secure API-key storage via AndroidKeyStore.
- Router policy now prefers a configured direct engine for compatible normal chat/attachments; provider apps/web remain degraded fallbacks only.
- Product V1 FINAL promotion remains fail-closed. The direct engine has NOT yet been field-connected on the user's phone, and no 20102 signed APK has yet passed chat + multimodal field acceptance.
- Status: SOURCE/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
