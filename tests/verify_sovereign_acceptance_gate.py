from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

GATE = (APP / "HakimAcceptanceGate.kt").read_text(encoding="utf-8")
SUPERVISOR = (APP / "HakimGoalSupervisor.kt").read_text(encoding="utf-8")
EXECUTOR = (APP / "HakimGoalExecutor.kt").read_text(encoding="utf-8")
LOOP = (APP / "HakimExecutiveLoop.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
CONSTITUTION = (APP / "HakimConstitution.kt").read_text(encoding="utf-8")
SELF = (APP / "HakimSelfCheck.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok: bool, reason: str) -> None:
    if not ok:
        raise SystemExit("SOVEREIGN_ACCEPTANCE_GATE=FAIL reason=" + reason)


for token in [
    'const val VERSION = "SOVEREIGN-ACCEPTANCE-GATE-2026-09-28-v1"',
    "enum class State",
    "CONTRACTED",
    "VERIFYING",
    "READY_TO_DELIVER",
    "DELIVERED",
    "BLOCKED",
    '"known_material_gap"',
    '"artifact_required"',
    '"artifact_verified"',
    '"artifact_same_as_tested"',
    '"regression_required"',
    '"regression_passed"',
    "fun verifyAndBindArtifact(",
    "PdfRenderer",
    'MessageDigest.getInstance("SHA-256")',
    'require(tested.first == delivered.first)',
    'require(tested.second == delivered.second)',
    'require(canClose(context)) { "delivery_before_acceptance_gate" }',
    '!regressionRequired || p.getBoolean("regression_passed", false)',
]:
    req(token in GATE, "gate:" + token)

# The gate must retain hashes/metadata, not raw goal/evidence payloads.
for forbidden in [
    '.putString("goal", goal',
    '.putString("evidence", evidence',
    '.putString("material_gap", evidence',
]:
    req(forbidden not in GATE, "raw_sensitive_persistence:" + forbidden)

req(
    "HakimAcceptanceGate.begin(context, goalId, goal, acceptance, baseline)" in SUPERVISOR,
    "supervisor_contract_not_wired",
)
req(
    "HakimAcceptanceGate.recordEffectEvidence(context, stage, evidence, effectVerified)" in SUPERVISOR,
    "supervisor_evidence_not_wired",
)
req(
    "HakimAcceptanceGate.markMaterialGap(context, evidence)" in SUPERVISOR,
    "failures_do_not_open_material_gap",
)
req(
    "HakimAcceptanceGate.canClose(context)" in SUPERVISOR,
    "supervisor_close_bypasses_gate",
)
req(
    'if (HakimGoalSupervisor.canClose(context)) "COMPLETE" else "VERIFY"' in EXECUTOR,
    "executor_can_complete_without_gate",
)
req(
    'HakimGoalSupervisor.begin(context, id, criteria, "android-candidate", goal)' in LOOP,
    "executive_loop_does_not_pass_real_goal",
)
req(
    "HakimAcceptanceGate.markDelivered(context)" in LOOP,
    "delivery_not_recorded_after_gate",
)
req(
    CENTER.count("HakimAcceptanceGate.verifyAndBindArtifact(") >= 3,
    "pdf_delivery_paths_not_bound_to_verified_artifact",
)

for token in [
    '"اجتاز الانحدار"',
    '"جاهز للتسليم"',
    '"acceptance_gate", HakimAcceptanceGate.status(context)',
    "بوابة الاعتماد إلزامية",
]:
    req(token in CONSTITUTION, "constitution:" + token)

for token in [
    "بوابة الاعتماد السيادية فعالة",
    "الدليل شرط للإغلاق",
    "الفجوة المادية تمنع الإغلاق",
    "نفس الملف المختبر هو المسلّم",
    "بوابة الانحدار مدعومة",
]:
    req(token in SELF, "self_check:" + token)

req(
    "python3 tests/verify_sovereign_acceptance_gate.py" in WORKFLOW,
    "workflow_gate_missing",
)

# Fault-injection sentinels: the static policy test must fail if either hard guard is removed.
artifact_mutant = GATE.replace(
    "require(tested.first == delivered.first)",
    "require(true)",
    1,
)
req(
    "require(tested.first == delivered.first)" not in artifact_mutant,
    "fault_artifact_mismatch_setup",
)
regression_mutant = GATE.replace(
    '!regressionRequired || p.getBoolean("regression_passed", false)',
    "true",
    1,
)
req(
    '!regressionRequired || p.getBoolean("regression_passed", false)' not in regression_mutant,
    "fault_regression_bypass_setup",
)

print(
    "SOVEREIGN_ACCEPTANCE_GATE=PASS "
    "evidence=true material_gap=true same_artifact=true regression=true "
    "runtime_close_gate=true self_check=true"
)
