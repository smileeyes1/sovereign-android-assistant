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
- Candidate versionCode: `20101`.
- Branch: `feature/hakim-20101-intent-to-completion-director`.
- Field evidence on 20100: the composer and «أنجز/إلغاء» remained visible above the Android keyboard/navigation area on the actual phone, satisfying the pinned-composer user-eye test.
- New user goal: every request to Hakim should be transformed internally into the strongest suitable execution instruction for the selected model/tool, and the model should privately optimize its own method until the user's completion criterion is met.
- 20101 adds HakimIntentDirector. It derives an intent-specific acceptance contract, tells the selected model to formulate its best internal work instruction, execute it, privately review/improve it up to three revisions, and return only the final result/evidence—not chain-of-thought.
- Hakim remains the supervisor: provider launch is not success, external completion still needs returned evidence, and no consumer ChatGPT session is treated as an API.
- All 20092–20100 safety/routing/executive/user-eye gates remain required.
- Status starts as SOURCE/CI CANDIDATE ONLY and does not replace the field baseline until the exact signed APK passes field acceptance.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
