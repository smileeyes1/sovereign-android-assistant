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
- Candidate versionCode: `20100`.
- Branch: `feature/hakim-20100-insets-pinned-composer`.
- Field evidence on 20099: compact progress succeeded when the keyboard was closed, but on the actual Android window the keyboard still covered/pushed the composer out of view; the system navigation bar also overlapped Hakim controls.
- 20100 treats Android 15 edge-to-edge insets explicitly. The composer is a dedicated bottom area, padded by the greater of IME or system-navigation bottom insets. The conversation no longer has a forced minimum height.
- While typing, title/status and secondary tools hide to preserve space; compact operation state remains, and the composer + execute/cancel row stay together above the keyboard.
- All 20092–20099 safety/routing/executive/user-eye gates remain required.
- Status starts as SOURCE/CI CANDIDATE ONLY and does not replace the field baseline until the exact signed APK proves, on the same phone window, that typed text and execute/cancel controls remain visible above both the keyboard and navigation bar.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
