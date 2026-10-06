from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MAIN_MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
ADV_MANIFEST = (ROOT / "app/src/advanced/AndroidManifest.xml").read_text(encoding="utf-8")
CONSUMER_MANIFEST = (ROOT / "app/src/consumer/AndroidManifest.xml").read_text(encoding="utf-8")
MODE = (APP / "HakimProductMode.kt").read_text(encoding="utf-8")
APP_SRC = (APP / "HakimApp.kt").read_text(encoding="utf-8")
BOOT = (APP / "BootReceiver.kt").read_text(encoding="utf-8")
RECOVERY = (APP / "HakimConnectionRecoveryJobService.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("PRODUCT_FOUNDATION_20338=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20338, "version")
req("product-foundation-v1" in BUILD, "version_lineage")
req("compileSdk 36" in BUILD, "compile_sdk")
req("targetSdk 36" in BUILD, "target_sdk")
req('flavorDimensions += "distribution"' in BUILD, "flavor_dimension")
for token in [
    'advanced {',
    'consumer {',
    'applicationId "ps.hakim.stable"',
    'applicationId "ps.hakim.consumer"',
    'resValue "bool", "hakim_advanced_mode", "true"',
    'resValue "bool", "hakim_advanced_mode", "false"',
    'resValue "bool", "hakim_sideload_updates", "false"',
]:
    req(token in BUILD, "build:" + token)

for token in [
    'HAKIM-PRODUCT-MODE-2026-10-06-v1',
    'fun isAdvanced(context: Context)',
    'fun allowsAdvancedDeviceControl',
    'fun allowsSideloadUpdates',
    '"consumer"',
    '"advanced"',
]:
    req(token in MODE, "mode:" + token)

# Consumer must not inherit advanced control surfaces.
for token in [
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.CHANGE_WIFI_MULTICAST_STATE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'com.termux.permission.RUN_COMMAND',
    '.HakimMobileTaskActivity',
    '.HakimRouterAuthActivity',
    '.HakimFamilyDnsVpnActivity',
    '.HakimTermuxResultReceiver',
    '.HakimFamilyDnsVpnService',
    '.HakimNetworkGuardianJobService',
    '.UpdateJobService',
]:
    req(token in CONSUMER_MANIFEST and 'tools:node="remove"' in CONSUMER_MANIFEST, "consumer_strip:" + token)

# Advanced gets the two services whose source existed but were absent from the 20337 manifest.
for token in [
    '.HakimAccessibilityService',
    'android.permission.BIND_ACCESSIBILITY_SERVICE',
    'android.accessibilityservice.AccessibilityService',
    '@xml/accessibility_service_config',
    '.HakimNotificationListener',
    'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
    'android.service.notification.NotificationListenerService',
]:
    req(token in ADV_MANIFEST, "advanced_manifest:" + token)

req('.HakimAccessibilityService' not in MAIN_MANIFEST, "accessibility_must_not_leak_to_main")
req('.HakimNotificationListener' not in MAIN_MANIFEST, "notification_listener_must_not_leak_to_main")

for token in [
    'HakimProductMode.allowsAdvancedDeviceControl(this)',
    'HakimProductMode.allowsSideloadUpdates(this)',
]:
    req(token in APP_SRC, "app_gate:" + token)
for token in [
    'HakimProductMode.allowsAdvancedDeviceControl(context)',
    'HakimProductMode.allowsSideloadUpdates(context)',
]:
    req(token in BOOT, "boot_gate:" + token)
req(
    'HakimProductMode.allowsAdvancedDeviceControl(this)' in APP_SRC and
    'HakimConstraintDoctor.runAsync(this, "app_start")' in APP_SRC,
    "consumer_startup_constraint_doctor_gate"
)
req(
    'HakimProductMode.allowsAdvancedDeviceControl(app)' in RECOVERY and
    'HakimConstraintDoctor.run(app, "periodic_watchdog")' in RECOVERY and
    'HakimSelfImprovementLoop.scheduleEvaluation(applicationContext, "periodic_watchdog")' in RECOVERY,
    "consumer_recovery_advanced_gate"
)
for token in [
    'if (!HakimProductMode.allowsAdvancedDeviceControl(this)) return',
]:
    req(CENTER.count(token) >= 3, "command_center_advanced_gates")
req('HakimProductMode.allowsSideloadUpdates(this)' in HOME, "consumer_update_ui_gate")
req('التحديثات عبر قناة التوزيع الرسمية.' in HOME, "consumer_update_copy")

candidate = STATE["android"]["candidate"]
req(candidate["version_code"] == 20338, "state_candidate")
req(candidate["field_verified"] is False, "candidate_field_false")
req(candidate["same_signed_apk_field_verified"] is False, "candidate_same_artifact_false")
installed = STATE["android"]["latest_installed_identity_unpromoted"]
req(installed["version_code"] == 20337, "installed_baseline")
req(installed["apk_sha256"] == "0bc68002ad297e524b94822bc73fc70e5c5ce7fcbd76b7a2133068160e61bc04", "installed_hash")
req(installed["runtime_acceptance_complete"] is False, "installed_runtime_not_overclaimed")

prod = STATE["productization"]
req(prod["candidate_version"] == 20338, "product_candidate")
req(prod["consumer_package"] == "ps.hakim.consumer", "consumer_package")
req(prod["advanced_package"] == "ps.hakim.stable", "advanced_package")
req(prod["consumer_target_sdk"] == 36, "consumer_target_sdk")
req(prod["consumer_sideload_updates"] is False, "consumer_sideload")
req(prod["consumer_advanced_device_control"] is False, "consumer_advanced_control")
req(prod["product_foundation_v1_field_verified"] is False, "field_must_remain_false")
req(PROMOTION["candidate_version"] == 20338 and PROMOTION["promoted"] is False, "promotion_closed")
req(PROMOTION["same_signed_apk_field_verified"] is False, "promotion_same_artifact_false")

for token in [
    "python3 tests/verify_20338_product_foundation.py",
    'test "$VERSION_CODE" = "20338"',
    "assembleAdvancedDebug",
    "assembleAdvancedRelease",
    "assembleConsumerDebug",
    "assembleConsumerRelease",
    "bundleConsumerRelease",
    "app-consumer-release.aab",
    "python3 tests/verify_20338_built_variants.py",
]:
    req(token in WORKFLOW, "workflow:" + token)

print(
    "PRODUCT_FOUNDATION_20338=PASS "
    "api36=true flavors=consumer+advanced consumer_advanced_control=false "
    "advanced_manifest_services=true promotion=false"
)
