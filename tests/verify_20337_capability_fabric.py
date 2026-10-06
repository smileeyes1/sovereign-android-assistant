from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
KERNEL=(APP/"HakimCapabilityKernel.kt").read_text(encoding="utf-8")
REGISTRY=(APP/"HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimCapabilityFabric.kt").read_text(encoding="utf-8")
RECEIPT=(APP/"HakimExecutionReceipt.kt").read_text(encoding="utf-8")
VERIFIER=(APP/"HakimCapabilityVerifier.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimToolRouter.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("CAPABILITY_FABRIC_20337=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))>=20337,"version_floor")
req("termux-local-control-r27-capability-fabric-r28" in BUILD,"version_lineage")

for token in [
    'Capability("observe_status"',
    'Capability("observe_browser"',
    'Capability("observe_notifications"',
    'Capability("observe_termux"',
]:
    req(token in KERNEL,"kernel_read_class:"+token)

for token in [
    'CAPABILITY-REGISTRY-2026-10-05-r28',
    'Contract("system.status"',
    'Contract("browser.read"',
    'Contract("ui.observe"',
    'Contract("notifications.read"',
    'Contract("termux.status"',
    'Contract("calendar.read"',
    'Contract("contacts.read"',
    'Contract("sms.send"',
    'Contract("calls.search"',
    'Contract("location.get"',
    'Contract("health.read"',
    'Contract("ble.scan"',
    'Contract("camera.capture"',
    'Contract("voice.dictate"',
    'Contract("artifact.create"',
    'Contract("connector.call"',
    'Contract("computer.session"',
    '.put("ownership", c.ownership)',
    '.put("kernel_capability", c.kernelCapability)',
]:
    req(token in REGISTRY,"registry:"+token)

for token in [
    'CAPABILITY-FABRIC-2026-10-05-r28',
    '.put("read_only_r28", true)',
    '.put("new_android_permissions", false)',
    '.put("writes_blocked", true)',
    'if (!contract.implemented)',
    'if (!contract.readOnly)',
    'if (!contract.remoteReadable)',
    'HakimCapabilityKernel.authorize(',
    'contract.kernelCapability',
    'HakimCapabilityVerifier.verify(capabilityId, result)',
    'HakimExecutionReceipt.record(',
]:
    req(token in FABRIC,"fabric:"+token)

for forbidden in [
    "sendTextMessage",
    "WRITE_CALENDAR",
    "WRITE_CONTACTS",
    "PackageInstaller",
    "install_candidate",
    "financial_action",
]:
    req(forbidden not in FABRIC,"fabric_write_path:"+forbidden)

for token in [
    'result_sha256',
    'MessageDigest.getInstance("SHA-256")',
    '.putString("last_receipt", receipt.toString())',
]:
    req(token in RECEIPT,"receipt:"+token)
req('putString("last_result"' not in RECEIPT,"raw_result_persisted")

for token in [
    '"system.status"',
    '"browser.read"',
    '"ui.observe"',
    '"notifications.read"',
    '"termux.status"',
    '"expected_observation_present"',
]:
    req(token in VERIFIER,"verifier:"+token)

# Regression root found on 20336: Relay allowed Termux ops but ToolRouter rejected them.
for token in [
    '"termux_status", "capabilities", "capability_read"',
    'private val SAFE_AUTOMATIC = setOf("termux_probe", "termux_recover")',
    'private val SUPPORTED = READ_ONLY + SAFE_AUTOMATIC + MUTATING',
    '"termux_status" -> HakimTermuxControl.isInstalled(context)',
    '"termux_probe", "termux_recover" -> HakimTermuxControl.ready(context)',
    '"capabilities", "capability_read" -> "capability_fabric"',
]:
    req(token in ROUTER,"router:"+token)

for token in [
    '"capabilities", "capability_read"',
    '"capabilities" -> HakimCapabilityFabric.status(context)',
    '"capability_read" -> HakimCapabilityFabric.executeReadOnly(',
    '.put("capability_fabric", HakimCapabilityFabric.status(context))',
    '"termux_status" -> HakimTermuxControl.status(context).put("ok", true)',
    '"termux_probe" -> HakimTermuxControl.probe(context, "secure_relay_probe")',
    '"termux_recover" -> HakimTermuxControl.recover(context, "secure_relay_recover")',
]:
    req(token in RELAY,"relay:"+token)

# R28 itself does not expand Android permissions. Future Muse-parity permissions stay planned.
for permission in [
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.CALL_PHONE",
    "android.permission.BLUETOOTH_SCAN",
]:
    req(permission not in MANIFEST,"new_manifest_permission:"+permission)

req(STATE["android"]["candidate"]["version_code"]>=20337,"state_candidate_floor")
req(STATE["android"]["candidate"]["field_verified"] is False,"candidate_field_must_remain_false")
req(STATE["android"]["candidate"]["same_signed_apk_field_verified"] is False,"candidate_same_artifact_must_remain_false")
runtime=STATE["android"]["latest_runtime_observation_unpromoted"]
req(runtime["version_code"]==20336,"runtime_baseline_version")
req(runtime["apk_sha256"]=="28467885a36e9833b8ef49b76ed929b8bde4454d8e5bd5e7a04e36cbfc7a679b","runtime_baseline_hash")
req(runtime["service_connected_observed"] is True,"runtime_bridge")
req(runtime["termux_wrapper_status"]=="PASS","termux_wrapper_observed")
req(runtime["termux_app_to_secure_relay_route_verified"] is False,"termux_route_must_not_be_overclaimed")
req("missing_from_tool_router_supported_set" in runtime["termux_router_source_gap"],"root_cause_recorded")

req(STATE["productization"]["candidate_version"]>=20337,"product_candidate_floor")
req(STATE["productization"]["same_signed_apk_field_verified"] is False,"product_same_artifact_must_remain_false")
req(STATE["productization"]["capability_fabric_r28_field_verified"] is False,"fabric_field_must_remain_false")
req(PROMOTION["candidate_version"]>=20337 and PROMOTION["promoted"] is False,"promotion_gate")
req(PROMOTION["same_signed_apk_field_verified"] is False,"promotion_same_artifact_must_remain_false")
req(PROMOTION["capability_fabric_r28_field_verified"] is False,"promotion_fabric_field_must_remain_false")

req('python3 tests/verify_20337_capability_fabric.py' in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20338"' in WORKFLOW or 'test "$VERSION_CODE" = "20337"' in WORKFLOW,"workflow_version")

print(
    "CAPABILITY_FABRIC_20337=PASS "
    "read_only=true termux_router_repaired=true no_new_manifest_permissions=true "
    "receipts_hash_only=true field_inheritance=false"
)
