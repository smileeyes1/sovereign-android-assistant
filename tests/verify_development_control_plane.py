from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"

def text(path:str)->str:
    return (ROOT/path).read_text(encoding="utf-8")

def req(cond,reason):
    if not cond:
        raise SystemExit("DEVELOPMENT_CONTROL_GATE=FAIL reason="+reason)

control=text("app/src/main/java/ps/hakim/phoneagent/HakimDevelopmentControlPlane.kt")
health=text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
evolution=text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
selfcheck=text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
workflow=text(".github/workflows/android.yml")

for token in [
    "HAKIM-DEVELOPMENT-CONTROL-2026-09-30-v1",
    'source_mutation_on_device", false',
    'github_secret_on_device", false',
    'isolated_branch_required", true',
    'ci_required", true',
    'regression_test_required", true',
    'field_install_requires_separate_authorization", true',
    'same_artifact_field_evidence_required", true',
    'no_permission_expansion", true',
    'MIN_REPEAT_MS = 6L * 60L * 60L * 1000L',
    '"field_candidate_regression"',
    '"self_check_failed"',
    '"repeated_runtime_failure"',
]:
    req(token in control,"control:"+token)

req("HakimDevelopmentControlPlane.requestForBeacon(context)" in health,"health_request_missing")
req('.put("development_control", HakimDevelopmentControlPlane.status(context))' in health,"health_status_missing")
req('health.put("development_request", developmentRequest)' in health,"development_request_not_embedded")
req("HakimDevelopmentControlPlane.markEmitted(context, developmentRequest)" in health,"request_not_acknowledged")
req("HakimDevelopmentControlPlane.shouldSignal(applicationContext)" in evolution,"evolution_signal_missing")
req('HakimHealthBeacon.sendNow(applicationContext, "development_request")' in evolution,"evolution_beacon_missing")
req("val development = HakimDevelopmentControlPlane.status(context)" in selfcheck,"selfcheck_control_missing")
req("python3 tests/verify_development_control_plane.py" in workflow,"workflow_gate_missing")

# Permanent anti-regression sentinels: development requests may ask for source work,
# but the phone itself must never receive repo credentials or mutate source.
req("GitHub" not in control or "github_secret_on_device" in control,"unexpected_github_path")
req("source_mutation_on_device",)
print("DEVELOPMENT_CONTROL_GATE=PASS")
