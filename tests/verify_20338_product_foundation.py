from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MAIN_MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
ADV_MANIFEST = (ROOT / "app/src/advanced/AndroidManifest.xml").read_text(encoding="utf-8")
CONSUMER_MANIFEST = (ROOT / "app/src/consumer/AndroidManifest.xml").read_text(encoding="utf-8")
PROFILE = (APP / "HakimProductProfile.kt").read_text(encoding="utf-8")
APP_START = (APP / "HakimApp.kt").read_text(encoding="utf-8")
BOOT = (APP / "BootReceiver.kt").read_text(encoding="utf-8")
COMMAND = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
SELF_CHECK = (APP / "HakimSelfCheck.kt").read_text(encoding="utf-8")
CONSTRAINT = (APP / "HakimConstraintDoctor.kt").read_text(encoding="utf-8")
RELAY = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
PAIRING = (APP / "HakimLocalPairing.kt").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok, reason):
    if not ok:
        raise SystemExit("PRODUCT_FOUNDATION_20338=FAIL reason=" + reason)


m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20338, "version")
req("targetSdk 36" in BUILD and "compileSdk 36" in BUILD, "api36")
req("flavorDimensions" in BUILD and 'dimension "distribution"' in BUILD, "distribution_dimension")
req("advanced {" in BUILD and "consumer {" in BUILD, "product_flavors")
req("applicationId 'ps.hakim.stable'" in BUILD, "advanced_lineage_package")
req("applicationId 'ps.hakim.app'" in BUILD, "consumer_package")
req('"HAKIM_ADVANCED", "true"' in BUILD and '"HAKIM_ADVANCED", "false"' in BUILD, "variant_runtime_gate")
req('"HAKIM_DISTRIBUTION_CHANNEL"' in BUILD, "distribution_channel")

for token in [
    'PRODUCT-FOUNDATION-20338-v1',
    'BuildConfig.HAKIM_ADVANCED',
    'BuildConfig.HAKIM_DISTRIBUTION_CHANNEL',
    '.put("play_candidate", consumer)',
    '.put("side_load_update_flow_allowed", advanced)',
    '.put("termux_control_allowed", advanced)',
    '.put("device_vpn_allowed", advanced)',
    '.put("accessibility_agent_allowed", advanced)',
]:
    req(token in PROFILE, "profile:" + token)

# Consumer must remove advanced/sensitive product surfaces from its merged-manifest input.
for token in [
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.CAMERA',
    'android.permission.RECORD_AUDIO',
    'android.permission.MODIFY_AUDIO_SETTINGS',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_WIFI_STATE',
    'android.permission.CHANGE_WIFI_MULTICAST_STATE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'com.termux.permission.RUN_COMMAND',
    '.HakimFamilyDnsVpnService',
    '.HakimFamilyDnsVpnActivity',
    '.HakimRouterAuthActivity',
    '.HakimMobileTaskActivity',
    '.HakimTermuxResultReceiver',
    '.UpdateJobService',
    '.HakimNetworkGuardianJobService',
]:
    req(token in CONSUMER_MANIFEST and 'tools:node="remove"' in CONSUMER_MANIFEST, "consumer_removal:" + token)

# Advanced repairs the 20337 manifest/source inconsistency.
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

# Consumer runtime must not schedule side-load updater, device VPN/router, or Termux permission flow.
req('if (HakimProductProfile.advanced) {' in APP_START, "app_start_gate")
for token in [
    'HakimNetworkGuardian.install(this)',
    'HakimDeviceProtection.ensureRunning(this)',
    'AutoUpdater.schedule(this)',
    'AutoUpdater.startRealtimeListener(this)',
]:
    req(token in APP_START, "advanced_startup_preserved:" + token)
req('if (HakimProductProfile.advanced) {' in BOOT, "boot_gate")
for token in [
    'if (!HakimProductProfile.advanced) return\n        if (termuxPermissionPromptAttempted) return',
    'if (!HakimProductProfile.advanced) return\n        if (!HakimTaskManager.shouldAutoOpenRouterProtection(this)) return',
    'if (!HakimProductProfile.advanced) return\n        // أولوية واجهة الراوتر',
]:
    req(token in COMMAND, "command_gate:" + token)

# Variant package identity must be dynamic; hard-coded stable package cannot be the check target.
req('context.packageName == BuildConfig.APPLICATION_ID' in SELF_CHECK, "selfcheck_variant_identity")
req('setClassName(context.packageName' in CONSTRAINT, "constraint_variant_identity")
req('Uri.parse("package:${context.packageName}")' in CONSTRAINT, "unknown_sources_variant_identity")
req('BuildConfig.APPLICATION_ID + ".REMOTE_APPROVE"' in RELAY, "relay_variant_approve")
req('BuildConfig.APPLICATION_ID + ".REMOTE_REJECT"' in RELAY, "relay_variant_reject")
req('BuildConfig.APPLICATION_ID + ".SUBMIT_LOCAL_ADB_PAIRING_CODE"' in PAIRING, "pairing_variant_action")

# Shared main must remain protected and must not add Muse-parity permissions prematurely.
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
    req(permission not in MAIN_MANIFEST, "premature_permission:" + permission)

req(STATE["android"]["candidate"]["version_code"] == 20338, "state_candidate")
req(STATE["android"]["candidate"]["field_verified"] is False, "candidate_field_false")
req(STATE["android"]["candidate"]["same_signed_apk_field_verified"] is False, "candidate_same_artifact_false")
runtime = STATE["android"]["latest_runtime_observation_unpromoted"]
req(runtime["version_code"] == 20337, "installed_baseline_version")
req(runtime["apk_sha256"] == "0bc68002ad297e524b94822bc73fc70e5c5ce7fcbd76b7a2133068160e61bc04", "installed_baseline_hash")
req(runtime["runtime_acceptance_complete"] is False, "installed_runtime_not_overclaimed")
product = STATE["productization"]
req(product["candidate_version"] == 20338, "product_candidate")
req(product["sellable"] is False, "not_sellable_yet")
req(product["consumer_target_sdk"] == 36 and product["advanced_target_sdk"] == 36, "product_api36")
req(product["consumer_play_bundle_required"] is True, "consumer_bundle_required")
req(product["consumer_side_load_update_flow"] is False, "consumer_no_sideload_updater")
req(product["consumer_termux_control"] is False, "consumer_no_termux")
req(product["consumer_device_vpn"] is False, "consumer_no_device_vpn")
req(product["consumer_accessibility_agent"] is False, "consumer_no_accessibility_agent")
req(product["advanced_accessibility_manifest_declared"] is True, "advanced_accessibility_declared")
req(product["advanced_notification_listener_manifest_declared"] is True, "advanced_notification_declared")

req(PROMOTION["candidate_version"] == 20338, "promotion_candidate")
req(PROMOTION["promoted"] is False, "promotion_false")
req(PROMOTION["candidate_field_verified"] is False, "promotion_field_false")
req(PROMOTION["consumer_play_bundle_built"] is False, "bundle_not_claimed_before_ci")

for token in [
    "python3 tests/verify_20338_product_foundation.py",
    "assembleConsumerDebug",
    "assembleConsumerRelease",
    "assembleAdvancedDebug",
    "assembleAdvancedRelease",
    "bundleConsumerRelease",
    'test "$VERSION_CODE" = "20338"',
]:
    req(token in WORKFLOW, "workflow:" + token)

print(
    "PRODUCT_FOUNDATION_20338=PASS "
    "api36=true flavors=true consumer_restricted=true advanced_manifest_repaired=true "
    "baseline_20337_preserved=true publication=false"
)
