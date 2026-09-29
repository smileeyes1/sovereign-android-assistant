from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
FAULT = (APP / "HakimFaultContainment.kt").read_text(encoding="utf-8")
APPKT = (APP / "HakimApp.kt").read_text(encoding="utf-8")
EVOLUTION = (APP / "HakimEvolutionJobService.kt").read_text(encoding="utf-8")
RECOVERY = (APP / "HakimConnectionRecoveryJobService.kt").read_text(encoding="utf-8")
SELF = (APP / "HakimSelfCheck.kt").read_text(encoding="utf-8")
RELAY = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))

def req(ok, reason):
    if not ok:
        raise SystemExit("FAULT_CONTAINMENT=FAIL reason=" + reason)

for token in [
    'FAULT-CONTAINMENT-2026-09-29-v1',
    'MAX_SAME_FAILURES = 3',
    'FAILURE_WINDOW_MS',
    'CIRCUIT_COOLDOWN_MS',
    'fun shouldAttempt',
    'fun recordFailure',
    'fun recordSuccess',
    'fun canExecuteHighImpact',
    '"critical_path_silent_failures_forbidden"',
    '"bounded_retry"',
    '"high_impact_fail_closed"',
    '"raw_exception_message_persisted", false',
]:
    req(token in FAULT, "fault_kernel:" + token)

req(".message" not in FAULT, "raw_exception_message_storage")
req("HakimFaultContainment.guard" in APPKT, "app_start_not_guarded")
req('"constitution_install", critical = true' in APPKT, "constitution_not_critical")
req('"pairing_defaults", critical = true' in APPKT, "pairing_not_critical")
req("catch (_: Exception)" not in EVOLUTION, "evolution_silent_catch")
req("HakimFaultContainment.guard" in EVOLUTION, "evolution_not_guarded")
req("catch (_: Exception)" not in RECOVERY, "recovery_silent_catch")
req("HakimFaultContainment.guard" in RECOVERY, "recovery_not_guarded")
req('val containment = HakimFaultContainment.status(context)' in SELF, "self_check_status_missing")
req('"fault_containment", containment' in SELF, "self_check_report_missing")
req('"fault_containment_blocked"' in RELAY, "high_impact_gate_missing")
req('"operation_circuit_open"' in RELAY, "operation_circuit_gate_missing")
req('HakimFaultContainment.shouldAttempt(context, "remote_op", op)' in RELAY, "operation_circuit_check_missing")
req('IllegalStateException("operation_result_failed")' in RELAY, "logical_failure_not_counted")
req('critical = !READ_ONLY_OPS.contains(op)' not in RELAY, "remote_failure_global_deadlock")
req('HakimFaultContainment.shouldAttempt(context, "secure_relay", "direct_poll")' in RELAY, "relay_circuit_missing")
req('HakimFaultContainment.recordFailure(context, "secure_relay", "direct_poll"' in RELAY, "relay_failure_record_missing")
req('.put("fault_containment", HakimFaultContainment.status(context))' in RELAY, "relay_status_missing")
req("python3 tests/verify_fault_containment_policy.py" in WORKFLOW, "workflow_gate_missing")
m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20315, "candidate_version_floor")
req(STATE["android"]["candidate"]["version_code"] == int(m.group(1)), "candidate_version_changed")
req("fault-containment" in BUILD, "version_name_missing")
req(STATE["android"]["candidate"]["field_verified"] is False, "candidate_field_claim")
req(STATE["productization"].get("fault_containment_source_integrated") is True, "state_source_missing")
req(STATE["productization"].get("fault_containment_field_verified") is False, "state_field_claim")
req(STATE["productization"].get("high_impact_fault_gate") is True, "state_high_impact_gate")

print("FAULT_CONTAINMENT=PASS critical_silent=false bounded_retry=true circuit_breaker=true high_impact_fail_closed=true")
