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
- Candidate versionCode: `20093`.
- Branch: `feature/hakim-20093-no-install-prompt`.
- 20092 installed successfully enough to reach the Android unknown-app-source settings screen, proving the prior Play Protect hard block was removed.
- Field evidence exposed a new defect: the safe core still contained legacy runtime calls that asked for the very install-source permission intentionally removed from its manifest. Android therefore showed a disabled toggle and Hakim displayed an impossible request.
- 20093 removes that residual updater/onboarding path from CommandCenter, BootReceiver, EvolutionJob, ConstraintDoctor, SelfCheck, and the manifest. Updates are explicitly external/user-managed in this safe core.
- Status starts as SOURCE/CI CANDIDATE ONLY. It does not replace the historical Android field baseline until the exact signed APK passes in-place update and field acceptance.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
