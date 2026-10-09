from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[1]
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
FIELD=(ROOT/"app/src/field/AndroidManifest.xml").read_text(encoding="utf-8")
ADV=(ROOT/"app/src/advanced/AndroidManifest.xml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMO=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("PLAY_PROTECT_SAFE_FIELD_20342=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1))==20342, "version")
req("play-protect-safe-field-r32" in BUILD, "lineage")
req("field {" in BUILD, "field_flavor")
field_block=BUILD.split("field {",1)[1].split("}",1)[0]
req("applicationId 'ps.hakim.stable'" in field_block, "field_package")
req('"field-safe"' in field_block, "field_channel")

# Parse the manifest; a comment, an unrelated tools:node, or one valid
# removal must never make all field security gates pass.
from copy import deepcopy
from xml.etree import ElementTree as ET

ANDROID_NAME="{http://schemas.android.com/apk/res/android}name"
TOOLS_NODE="{http://schemas.android.com/tools}node"
REQUIRED_PERMISSIONS=(
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
)
REQUIRED_SERVICES=(
    ".HakimAccessibilityService",
    ".HakimNotificationListener",
)

def all_removals_enforced(root):
    app=root.find("application")
    if app is None:
        return False
    for kind, parent, targets in (
        ("uses-permission", root, REQUIRED_PERMISSIONS),
        ("service", app, REQUIRED_SERVICES),
    ):
        for target in targets:
            hits=[e for e in parent.findall(kind) if e.get(ANDROID_NAME)==target]
            if len(hits)!=1 or hits[0].get(TOOLS_NODE)!="remove":
                return False
    return True

field_root=ET.fromstring(FIELD)
req(all_removals_enforced(field_root), "missing_or_ineffective_manifest_removal")

# Test the test: mutation and deletion of each individual security removal
# must turn the gate red. No APK is installed or distributed by these tests.
rejected_mutations=0
for kind, targets in (
    ("uses-permission", REQUIRED_PERMISSIONS),
    ("service", REQUIRED_SERVICES),
):
    for target in targets:
        for mutation in ("merge", "delete"):
            altered=deepcopy(field_root)
            parent=altered if kind=="uses-permission" else altered.find("application")
            entry=next(e for e in parent.findall(kind) if e.get(ANDROID_NAME)==target)
            if mutation=="merge":
                entry.set(TOOLS_NODE, "merge")
            else:
                parent.remove(entry)
            req(not all_removals_enforced(altered), "detector_missed:"+target+":"+mutation)
            rejected_mutations+=1
req(rejected_mutations==14, "negative_test_coverage")

# The owner's explicit disconnect decision must not be silently reversed
# by a previously valid deep link. Preserve local confirmation on re-enable.
PAIRING=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimPairingActivity.kt").read_text(encoding="utf-8")
PAIRING_GUARD="if (matchesExisting && !userDisabled) {"
req('getBoolean("pairing_disabled_by_user", false)' in PAIRING, "owner_disconnect_state")
req(PAIRING.count(PAIRING_GUARD)==1, "owner_disconnect_reenable_guard")
req('userDisabled -> "إعادة تفعيل قناة حكيم؟"' in PAIRING, "owner_reenable_dialog")
req("userDisabled -> \"سبق أن أوقفت قناة حكيم بنفسك." in PAIRING, "owner_reenable_message")
req('.setPositiveButton("اعتماد")' in PAIRING, "owner_local_approval")
for unsafe in (
    "if (matchesExisting) {",
    "if (matchesExisting || userDisabled) {",
    "if (matchesExisting && true) {",
):
    req(PAIRING_GUARD not in PAIRING.replace(PAIRING_GUARD, unsafe), "pairing_guard_negative_probe")
# Do not confuse static regression assertions with Android runtime acceptance.

# Preserve the full lab surface separately; only the installable field flavor is reduced.
req('.HakimAccessibilityService' in ADV, "advanced_accessibility_preserved")
req('.HakimNotificationListener' in ADV, "advanced_notification_preserved")

cand=STATE["android"]["candidate"]
req(cand["version_code"]==20342, "state_candidate")
req(cand["branch"]=="feature/hakim-20342-play-protect-safe-field-r32", "state_branch")
req(cand["field_verified"] is False and cand["same_signed_apk_field_verified"] is False, "no_field_inheritance")
prod=STATE["productization"]
req(prod["candidate_version"]==20342, "product_candidate")
req(prod["field_application_id"]=="ps.hakim.stable", "field_application_id")
req(prod["field_safe_distribution_source_integrated"] is True, "field_source_integrated")
req(prod["field_safe_distribution_source_ci_verified"] is False, "field_ci_starts_false")
req(prod["field_safe_distribution_field_verified"] is False, "field_starts_false")
req(PROMO["candidate_version"]==20342 and PROMO["promoted"] is False, "promotion_closed")
req("python3 tests/verify_20342_play_protect_safe_field.py" in WORKFLOW, "workflow_source_gate")
req('FIELD_SAFE_APK="app/build/outputs/apk/field/release/app-field-release-unsigned.apk"' in WORKFLOW, "workflow_built_gate")
req("PLAY_PROTECT_FIELD_APK_GATE=PASS" in WORKFLOW, "workflow_apk_gate")

print("PLAY_PROTECT_SAFE_FIELD_20342=PASS package=ps.hakim.stable advanced_lab_preserved=true field_evidence=false adversarial_manifest_tests=14")
