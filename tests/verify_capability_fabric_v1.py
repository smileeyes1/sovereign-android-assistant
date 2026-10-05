from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

REGISTRY = (APP / "HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
FABRIC = (APP / "HakimCapabilityFabric.kt").read_text(encoding="utf-8")
RECEIPT = (APP / "HakimExecutionReceipt.kt").read_text(encoding="utf-8")
VERIFIER = (APP / "HakimCapabilityVerifier.kt").read_text(encoding="utf-8")
RELAY = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok: bool, reason: str) -> None:
    if not ok:
        raise SystemExit("CAPABILITY_FABRIC_V1=FAIL reason=" + reason)


for token in [
    'const val VERSION = "CAPABILITY-REGISTRY-2026-10-05-v1"',
    'Contract("system.status"',
    'Contract("browser.read"',
    'Contract("ui.observe"',
    'Contract("notifications.read"',
    'Contract("screenshot.capture"',
    'Contract("calendar.read"',
    'Contract("calendar.create"',
    'Contract("contacts.read"',
    'Contract("sms.send"',
    'Contract("location.get"',
    'Contract("health.read"',
    'Contract("connector.call"',
    'Contract("computer.session"',
    '.put("implemented", c.implemented)',
    '.put("remote_readable", c.remoteReadable)',
    '.put("approval_mode", c.approvalMode)',
]:
    req(token in REGISTRY, "registry:" + token)

# القدرات المستقبلية مسجلة كغير منفذة ولا توسع أذونات APK الحالي.
for token in [
    'Contract("calendar.read", "قراءة التقويم", "personal_data", false',
    'Contract("contacts.read", "قراءة جهات الاتصال", "personal_data", false',
    'Contract("sms.send", "إرسال رسالة", "communications", false',
    'Contract("location.get", "قراءة الموقع المأذون", "device", false',
    'Contract("connector.call", "استدعاء موصل خارجي", "connectors", false',
    'Contract("computer.session", "جلسة حاسوب وكيل", "computer", false',
]:
    req(token in REGISTRY, "planned_must_stay_unimplemented:" + token)

for permission in [
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.BLUETOOTH_SCAN",
]:
    req(permission not in MANIFEST, "new_manifest_permission:" + permission)

for token in [
    'const val VERSION = "CAPABILITY-FABRIC-2026-10-05-v1"',
    '.put("read_only_v1", true)',
    '.put("new_android_permissions", false)',
    '.put("writes_blocked", true)',
    'if (!contract.implemented)',
    'if (!contract.readOnly)',
    'if (!contract.remoteReadable)',
    'HakimCapabilityKernel.authorize(',
    '"observe_ui"',
    'HakimCapabilityVerifier.verify(capabilityId, result)',
    'HakimExecutionReceipt.record(',
]:
    req(token in FABRIC, "fabric:" + token)

# منفذ V1 لا يحتوي مسارات إرسال أو كتابة أو تثبيت.
for forbidden in [
    "sendTextMessage",
    "WRITE_CALENDAR",
    "WRITE_CONTACTS",
    "PackageInstaller",
    "install_candidate",
    "send_external",
    "financial_action",
]:
    req(forbidden not in FABRIC, "fabric_write_path:" + forbidden)

# لقطة الشاشة موجودة في الجرد ولكن لا تدخل المسار البعيد العام.
req(
    'Contract("screenshot.capture", "لقطة شاشة", "device", true, true, false' in REGISTRY,
    "screenshot_remote_readable"
)

for token in [
    'result_sha256',
    'MessageDigest.getInstance("SHA-256")',
    '.putString("last_receipt", receipt.toString())',
]:
    req(token in RECEIPT, "receipt:" + token)
req('.putString("last_result"' not in RECEIPT, "raw_result_persisted")

for token in [
    '"system.status"',
    '"browser.read"',
    '"ui.observe"',
    '"notifications.read"',
    '"expected_observation_present"',
]:
    req(token in VERIFIER, "verifier:" + token)

req(
    'setOf("status", "ui", "notifications", "screenshot", "browser_read", "capabilities", "capability_read")' in RELAY,
    "relay_read_ops"
)
req('"capabilities" -> HakimCapabilityFabric.status(context)' in RELAY, "relay_catalog_route")
req('"capability_read" -> HakimCapabilityFabric.executeReadOnly(' in RELAY, "relay_read_route")
req('.put("capability_fabric", HakimCapabilityFabric.status(context))' in RELAY, "relay_status")
req('READ_ONLY_OPS + setOf("action", "launch", "browser_back")' in RELAY, "existing_write_gate_regression")
req('python3 tests/verify_capability_fabric_v1.py' in WORKFLOW, "workflow_gate")

print(
    "CAPABILITY_FABRIC_V1=PASS "
    "read_only=true planned_unimplemented=true no_new_manifest_permissions=true "
    "receipt_hash_only=true existing_write_gate_preserved=true"
)
