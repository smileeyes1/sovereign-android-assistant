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

## Current Android candidate
- Candidate versionCode: `20092`.
- Branch: `feature/hakim-20092-playprotect-safe`.
- Parent candidate 20091 was signed with D1 and cryptographically verified, but Google Play Protect blocked sideload installation because the APK declared high-risk Accessibility / Notification Listener surfaces.
- 20092 removes those high-risk declarations from the installable core while keeping the audited source code available for future privileged-channel redesign.
- 20092 also removes self-install package permissions from the core; installation/update remains an explicit Android/user action.
- Status starts as SOURCE/CI CANDIDATE ONLY.
- It must not replace the historical Android field baseline until the exact signed APK delivered to the device passes in-place update compatibility and field acceptance.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
