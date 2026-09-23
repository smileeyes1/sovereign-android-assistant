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
- Candidate versionCode: `20104`.
- Branch: `feature/hakim-20104-quranic-governance`.
- 20103 remains the latest signed free-intelligence predecessor: free-only cost gate, adaptive wisdom matrix, OpenRouter free engine, Gemini free-tier path when explicitly confirmed, bounded failover and in-app streaming are inherited.
- 20104 adds Quran/Sunnah values governance and a strict theological/technical boundary: revelation governs purpose, ethics and limits; technical intelligence still comes from models, algorithms, tools, evidence and testing.
- Fixed divine names are used only as ethical reminders (truth, knowledge, wisdom, oversight, mercy), never as magical weights, numerology, hidden algorithms or claims of divine technical causality.
- Qur'anic anchors for verification, knowledge limits, justice, trust, non-aggression, consultation and non-coercion are codified with references.
- The compact governance guard is injected into the model task envelope and the canonical Hakim constitution.
- Status: SOURCE/CI CANDIDATE; NOT FIELD VERIFIED / NOT PROMOTED.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.
