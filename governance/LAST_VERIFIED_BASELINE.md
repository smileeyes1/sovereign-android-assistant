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
- Candidate versionCode: `20107`.
- Branch: `feature/hakim-20107-openrouter-external-browser-loopback`.
- Field evidence on 20106 exposed a real OAuth defect on Android: OpenRouter redirected to `http://127.0.0.1:<port>/callback`, and Hakim/WebView attempted to load that cleartext URL, producing `net::ERR_CLEARTEXT_NOT_PERMITTED`.
- OpenRouter's official OAuth PKCE documentation explicitly supports localhost callbacks on arbitrary ports. The correct fix is therefore not to permit cleartext inside Hakim, but to keep the OAuth authorization/callback in a real external browser while Hakim's local loopback server receives the callback.
- 20107 removes Hakim's generic http/https browsable claim, forces OpenRouter authorization to an external browser package (with an Android browser-selector fallback), preserves PKCE S256 + state + random loopback port, and keeps `usesCleartextTraffic=false`.
- The final browser-to-app hop remains the custom `hakim://openrouter-connected` deep link after the loopback server has exchanged the authorization code.
- All FREE_ONLY, resilience, Quran/Sunnah values governance, no-paid-fallback, circuit-breaker and D1 lineage requirements remain inherited.
- Status: SOURCE/CI CANDIDATE only until CI, D1 signing and the same APK prove on the phone that OpenRouter OAuth returns to Hakim without WebView cleartext failure and then a free non-local answer returns inside Hakim.

## Promotion rule
A newer component inherits no success automatically. Promote only after the tests relevant to what changed pass on the same artifact/deployment that is delivered. If a field-signing credential is unavailable, keep the Android candidate explicitly NOT INSTALLABLE / NOT PROMOTED.


## Current integrated source candidate — 20109
- Parent source baseline: `20108` at `cfb2cf668a2f4431c3706a1b6d6d32bb5a9a8c76`, whose GitHub Actions run #903 completed SUCCESS.
- Candidate branch: `feature/hakim-20109-unified-factory-v1`.
- 20109 integrates the material factory contract and a whole-human biology domain while preserving the 20108 network-guardian lineage.
- Human-biology scope is education, wellness, non-invasive monitoring and evidence-bounded decision support by default. It does not grant autonomous diagnosis, treatment, stimulation, implantation, dosing, surgery, or genome intervention.
- Material state cannot be promoted to MATERIAL_VERIFIED without fabrication evidence, measurement, acceptance, and same-artifact proof.
- Source/CI evidence for the integrated 20109 code parent: commit `22bf46b2878a415155074274fc5346ff9a9e19c0`, GitHub Actions run #909 = SUCCESS.
- 20109 remains NOT PROMOTED until the same correctly signed APK passes field acceptance.
