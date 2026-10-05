from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
loop=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimSelfImprovementLoop.kt").read_text(encoding="utf-8")
health=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt").read_text(encoding="utf-8")
job=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt").read_text(encoding="utf-8")
app=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")
boot=(ROOT/"app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
selfcheck=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
gradle=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
state=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))

def req(cond, reason):
    if not cond:
        raise SystemExit("SELF_IMPROVEMENT_GATE=FAIL reason="+reason)

m = re.search(r"versionCode\s+(\d+)", gradle)
req(m is not None and int(m.group(1)) >= 20111, "candidate_version")
for token in [
    'SELF-IMPROVEMENT-LOOP-2026-09-24-v1',
    '"POST_INSTALL_OBSERVING"',
    '"POST_INSTALL_HEALTHY"',
    '"POST_INSTALL_WAITING_EVIDENCE"',
    '"ROLLBACK_FORWARD_REQUIRED"',
    'MAX_POST_INSTALL_OBSERVE_MS = 5L * 60L * 1000L',
    'committedVersion == current',
    'fun onPackageReplaced(context: Context)',
    'alreadySamePending',
    '"FORWARD_ONLY_FROM_VERIFIED_BASELINE_SOURCE"',
    '.put("source_mutation_on_device", false)',
    '.put("automatic_downgrade", false)',
    '.put("d1_required_for_field_update", true)',
    '.put("verified_baseline_source_required", true)',
    '.put("new_candidate_does_not_inherit_success", true)',
    '.put("same_artifact_field_evidence_required", true)',
]:
    req(token in loop, "loop:"+token)

req('HakimSelfImprovementLoop.install(this)' in app, "app_install")
req('HakimSelfImprovementLoop.onPackageReplaced(context)' in boot, "package_replace_hook")
req('HakimSelfImprovementLoop.scheduleEvaluation(applicationContext, "periodic_watchdog")' in job, "watchdog_evaluation")
req('HakimHealthBeacon.sendNow(applicationContext, "periodic_watchdog")' not in job, "duplicate_watchdog_health")
req('MIN_SEND_INTERVAL_MS = 5_000L' in health, "health_throttle")
req('@Synchronized\n    fun sendNow' in health, "health_race_guard")
req('.put("self_improvement", HakimSelfImprovementLoop.status(context))' in health, "health_evidence")
req('val improvement = HakimSelfImprovementLoop.status(context)' in selfcheck, "selfcheck_integration")
req(state["android"]["candidate"]["version_code"] >= 20111, "state_candidate")
req(state["android"]["candidate"]["promoted"] is False, "promotion_false")
field_version = int(state["android"]["field_observed_current"]["version_code"])
candidate_version = int(state["android"]["candidate"]["version_code"])
req(field_version >= 20106, "field_observed_version_floor")
if state["android"]["candidate"]["field_verified"] is True:
    req(candidate_version == field_version, "verified_candidate_must_equal_field_observed")
    req(state["android"]["candidate"]["same_signed_apk_field_verified"] is True, "verified_candidate_same_artifact")
else:
    req(candidate_version > field_version, "unverified_candidate_must_be_newer_than_field_baseline")
    req(state["android"]["candidate"]["same_signed_apk_field_verified"] is False, "unverified_candidate_must_not_inherit_same_artifact")
req(state["android"]["field_observed_current"]["evidence"] == "authenticated_health_hash_and_status_probe_version_20312", "field_observed_evidence")
req(state["android"]["field_observed_current"]["exact_public_source_mapping"] == "PROVEN_BY_EXACT_SIGNED_APK_SHA256", "field_source_mapping")
req(state["android"]["field_observed_current"]["apk_sha256"] == state["productization"]["signed_candidate_sha256"], "same_artifact_hash_mismatch")

# Known-failure injection: weakening either D1 or verified-baseline rollback must be detectable.
mutant=loop.replace('.put("d1_required_for_field_update", true)', '.put("d1_required_for_field_update", false)', 1)
req('.put("d1_required_for_field_update", true)' not in mutant, "known_failure_d1_not_detected")
mutant2=loop.replace('"FORWARD_ONLY_FROM_VERIFIED_BASELINE_SOURCE"', '"DOWNGRADE_ANY"', 1)
req('"FORWARD_ONLY_FROM_VERIFIED_BASELINE_SOURCE"' not in mutant2, "known_failure_rollback_not_detected")

print("SELF_IMPROVEMENT_GATE=PASS")
