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
- Candidate versionCode: `20096`.
- Branch: `feature/hakim-20096-visible-conversation`.
- Field evidence on 20094/20095 proved the Local-First route works: «مرحبا» no longer opens ChatGPT.
- New user-eye evidence on the actual phone showed the local reply was technically written into the small status/header line, so the user reasonably perceived that Hakim did not answer.
- 20096 fixes the presentation contract, not the routing contract: each submitted user message and Hakim reply are rendered in a dedicated scrollable conversation transcript, persisted locally, and automatically scrolled to the newest reply.
- The status line is now only execution state; it is never the sole place for a conversational answer.
- All Android-native / Play Protect-safe / no-install-prompt / Local-First protections remain inherited and must pass again in CI.
- Status starts as SOURCE/CI CANDIDATE ONLY and does not replace the field baseline until the exact signed APK passes in-place update and the reply is visibly accepted by the user.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
