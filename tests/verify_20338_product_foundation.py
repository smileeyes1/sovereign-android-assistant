from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
BASE = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
ADVANCED = (ROOT / "app/src/advanced/AndroidManifest.xml").read_text(encoding="utf-8")
EDITION = (APP / "HakimProductEdition.kt").read_text(encoding="utf-8")
APP_KT = (APP / "HakimApp.kt").read_text(encoding="utf-8")
COMMAND = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
SETTINGS = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok: bool, reason: str) -> None:
    if not ok:
        raise SystemExit("PRODUCT_FOUNDATION_20338=FAIL reason=" + reason)


m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20338, "version")
req("compileSdk 36" in BUILD, "compile_sdk")
req("targetSdk 36" in BUILD, "target_sdk")
for token in [
    "flavorDimensions 'edition'",
    "advanced {",
    "consumer {",
    "applicationIdSuffix '.consumer'",
    "'HAKIM_EDITION', '"advanced"'",
    "'HAKIM_EDITION', '"consumer"'",
    "'HAKIM_ADVANCED', 'true'",
    "'HAKIM_ADVANCED', 'false'",
    "product-foundation-v1",
]:
    req(token in BUILD, "build:" + token)

for token in [
    "PRODUCT-FOUNDATION-20338-v1",
    "BuildConfig.HAKIM_ADVANCED",
    "BuildConfig.HAKIM_EDITION",
    '.put("play_ready_claimed", false)',
    '.put("field_verified", false)',
]:
    req(token in EDITION, "edition:" + token)

# Consumer-safe base: no privileged control-plane declarations.
for forbidden in [
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_WIFI_STATE",
    "com.termux.permission.RUN_COMMAND",
    ".HakimFamilyDnsVpnService",
    ".HakimAccessibilityService",
    ".HakimNotificationListener",
    ".BootReceiver",
    ".HakimService",
]:
    req(forbidden not in BASE, "consumer_base_leak:" + forbidden)

for required in [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    ".CommandCenterActivity",
    ".UnifiedHomeActivity",
    ".HakimTaskManagerActivity",
]:
    req(required in BASE, "consumer_base_missing:" + required)

# Advanced edition gets the control surfaces explicitly rather than by accident.
for required in [
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_WIFI_STATE",
    "com.termux.permission.RUN_COMMAND",
    ".HakimFamilyDnsVpnService",
    ".HakimAccessibilityService",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    ".HakimNotificationListener",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    ".BootReceiver",
    ".HakimService",
]:
    req(required in ADVANCED, "advanced_missing:" + required)

req("if (HakimProductEdition.isAdvanced)" in APP_KT, "app_runtime_boundary")
for fn in [
    "maybeEnsureTermuxControlPermission",
    "maybeOpenLocalRouterAuth",
    "maybeEnsureDeviceProtection",
]:
    marker = "private fun " + fn + "() {\n        if (!HakimProductEdition.isAdvanced) return"
    req(marker in COMMAND, "command_boundary:" + fn)

req('if (HakimProductEdition.isAdvanced) {' in SETTINGS, "settings_advanced_update_gate")
req('"الإصدار الاستهلاكي — التحديث عبر قناة التوزيع الموثوقة فقط."' in SETTINGS, "consumer_update_copy")

req(STATE["android"]["candidate"]["version_code"] == 20338, "state_candidate")
req(STATE["android"]["candidate"]["field_verified"] is False, "candidate_field_false")
req(STATE["android"]["candidate"]["same_signed_apk_field_verified"] is False, "candidate_same_artifact_false")
runtime = STATE["android"]["latest_runtime_observation_unpromoted"]
req(runtime["version_code"] == 20337, "runtime_baseline")
req(runtime["apk_sha256"] == "0bc68002ad297e524b94822bc73fc70e5c5ce7fcbd76b7a2133068160e61bc04", "runtime_baseline_hash")
req(STATE["productization"]["consumer_advanced_split"] is True, "product_split")
req(STATE["productization"]["consumer_target_sdk"] == 36, "consumer_target")
req(STATE["productization"]["advanced_target_sdk"] == 36, "advanced_target")
req(STATE["productization"]["consumer_play_ready"] is False, "play_not_claimed")
req(PROMOTION["candidate_version"] == 20338, "promotion_candidate")
req(PROMOTION["promoted"] is False, "promotion_false")
req(PROMOTION["consumer_play_ready"] is False, "promotion_play_false")

req("python3 tests/verify_20338_product_foundation.py" in WORKFLOW, "workflow_gate")
req('test "$VERSION_CODE" = "20338"' in WORKFLOW, "workflow_version")
req("assembleAdvancedDebug" in WORKFLOW, "advanced_build")
req("assembleConsumerRelease" in WORKFLOW, "consumer_apk_build")
req("bundleConsumerRelease" in WORKFLOW, "consumer_aab_build")
req("hakim-consumer-20338-NOT-FOR-PUBLISHING" in WORKFLOW, "consumer_artifact_gate")

print(
    "PRODUCT_FOUNDATION_20338=PASS "
    "api36=true split=true consumer_base_minimized=true "
    "advanced_services_explicit=true play_claim=false field_inheritance=false"
)
