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
- Branch: `feature/hakim-20103-openrouter-android-pkce`.
- Field evidence from the user's Tecno POVA 7 showed the previous OpenRouter authorization redirecting to `http://127.0.0.1:46041/callback` inside Hakim WebView and failing with `ERR_CLEARTEXT_NOT_PERMITTED`. This was a desktop-style callback path used incorrectly on Android.
- 20103 replaces that path with an Android-native local-first PKCE flow: Hakim binds an ephemeral loopback server on `127.0.0.1`, opens OpenRouter authorization in the system browser, validates `state` and S256 PKCE, exchanges the returned code over HTTPS, stores the resulting API key in AndroidKeyStore, and closes the loopback server.
- The direct model is `openrouter/free` by default. It streams text back into Hakim, supports image input on the free router, and never silently falls back to a paid model.
- OpenRouter is preferred before the optional Gemini direct engine when both are configured, because the user explicitly requires a zero-paid-default path.
- Global cleartext traffic remains disabled; Hakim does not weaken Android network security to fix the OAuth bug.
- Status starts as SOURCE/CI CANDIDATE ONLY. Product V1 remains NOT PROMOTED until the exact signed APK proves on the same phone: OAuth loopback success, direct free reply inside Hakim, at least one image reply inside Hakim, and bounded handling of quota/failure.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
