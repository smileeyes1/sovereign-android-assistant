from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
REGISTRY=(APP/"HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimCapabilityFabric.kt").read_text(encoding="utf-8")
RECEIPT=(APP/"HakimExecutionReceipt.kt").read_text(encoding="utf-8")
VERIFIER=(APP/"HakimCapabilityVerifier.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")
STATE=__import__("json").loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION=__import__("json").loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))


def req(ok, reason):
    if not ok:
        raise SystemExit("CAPABILITY_FABRIC_20313=FAIL reason="+reason)


m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20313,"version")
for token in [
    "control-channel-recovery",
    "direct-autostart",
    "resilient-alarm-watchdog",
    "network-diagnostics",
    "lan-survey",
    "extender-survey",
    "professional-observability",
    "continuity",
    "capability-fabric-v1",
]:
    req(token in BUILD,"version_lineage:"+token)

for token in [
    'CAPABILITY-REGISTRY-2026-10-05-v1',
    'CAPABILITY-FABRIC-2026-10-05-v1',
    'read_only_v1',
    'writes_blocked',
    'unknown_capability_denied',
    'planned_not_implemented',
    'write_capability_blocked_in_v1',
    'remote_read_not_allowed',
]:
    req(token in (REGISTRY+"\n"+FABRIC), "fabric_contract:"+token)

for token in [
    'HakimCapabilityVerifier.verify(capabilityId, result)',
    'HakimExecutionReceipt.record(',
    'result_sha256',
]:
    req(token in (FABRIC+"\n"+RECEIPT+"\n"+VERIFIER), "evidence:"+token)

req('"capabilities" -> HakimCapabilityFabric.status(context)' in RELAY,"catalog_route")
req('"capability_read" -> HakimCapabilityFabric.executeReadOnly(' in RELAY,"read_route")
req('READ_ONLY_OPS + setOf("action", "launch", "browser_back")' in RELAY,"legacy_write_approval_preserved")

for permission in [
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.BLUETOOTH_SCAN",
]:
    req(permission not in MANIFEST, "permission_expansion:"+permission)

req('python3 tests/verify_capability_fabric_v1.py' in WORKFLOW,"v1_gate")
req('python3 tests/verify_20313_capability_fabric.py' in WORKFLOW,"20313_gate")
req('test "$VERSION_CODE" = "20313"' in WORKFLOW,"workflow_version")
req(STATE["android"]["candidate"]["version_code"]==20313,"state_candidate")
req(STATE["android"]["candidate"]["field_verified"] is False,"candidate_field_must_remain_false")
req(STATE["android"]["candidate"]["same_signed_apk_field_verified"] is False,"candidate_same_artifact_must_remain_false")
req(STATE["android"]["field_observed_current"]["version_code"]==20312,"field_baseline_must_remain_20312")
req(STATE["productization"]["candidate_version"]==20313,"product_candidate")
req(STATE["productization"]["signed_candidate_version"]==20312,"last_signed_artifact_baseline")
req(STATE["productization"]["same_signed_apk_field_verified"] is False,"product_same_artifact_must_remain_false")
req(PROMOTION["candidate_version"]==20313 and PROMOTION["promoted"] is False,"promotion_gate")
req(PROMOTION["same_signed_apk_field_verified"] is False,"promotion_same_artifact_must_remain_false")

print(
    "CAPABILITY_FABRIC_20313=PASS "
    "identity=true lineage=true read_only=true evidence=true "
    "no_new_manifest_permissions=true regression_gate=true"
)
