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
- Candidate versionCode: `20095`.
- Branch: `feature/hakim-20095-android-native-only`.
- Field evidence on 20094: Local-First greeting passed on the actual phone; «مرحبا» stayed inside Hakim.
- New field evidence: the private ChatGPT plugin page on Android is marked «Desktop only». OpenAI's current documentation confirms imported plugins declaring MCP servers are desktop-only, and custom MCP apps are not available on mobile.
- Therefore Android must not depend on the ChatGPT MCP plugin. The mobile path is: local deterministic handling -> Android provider app / system share -> provider web/browser fallback -> optional official direct API only when separately configured and authorized.
- 20095 makes that boundary explicit in the UI and disambiguates Hakim bridge pairing from ChatGPT plugin setup. It does not remove the desktop plugin; it stops treating it as an Android dependency.
- Status starts as SOURCE/CI CANDIDATE ONLY and does not replace the field baseline until the exact signed APK passes in-place update and field acceptance.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
