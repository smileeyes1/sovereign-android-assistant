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
- Branch: `feature/hakim-20103-quranic-wisdom-governance`.
- 20102 remains the first source/CI-proven DIRECT_MODEL candidate; its direct-model path is inherited, but it is still NOT FIELD VERIFIED / NOT PROMOTED.
- 20103 adds an explicit Quran/Sunnah values constitution and runtime instruction. It treats revelation as guidance for goals, values and boundaries, while technical means remain governed by evidence and testing.
- Names and attributes of Allah are not modeled as software powers. Their meanings are used only to reinforce human duties such as wisdom, truthfulness, mercy, trust, justice, preservation and accountability.
- Letters, abjad/numerology, awfaq and supposed hidden-letter powers are explicitly barred from acting as technical mechanisms, prediction, encryption, healing, routing or claims of improved model accuracy.
- A fail-closed Wisdom Matrix now separates hard gates (support, authorization, direct return, official channel) from weighted utility dimensions (quality, reliability, privacy, cost efficiency, latency, reversibility and field verification).
- Existing religious-integrity rules remain in force: Quran text/source integrity, hadith attribution checks, separation of revelation from interpretation, and no claim of divine technical causation.
- Status: SOURCE/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
