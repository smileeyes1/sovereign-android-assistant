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

for token in [
    'android.permission.READ_SMS',
    'android.permission.RECEIVE_SMS',
    'android.permission.READ_PHONE_STATE',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.REQUEST_INSTALL_PACKAGES',
    '.HakimAccessibilityService',
    '.HakimNotificationListener',
]:
    req(token in FIELD and 'tools:node="remove"' in FIELD, "field_removal:"+token)

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

print("PLAY_PROTECT_SAFE_FIELD_20342=PASS package=ps.hakim.stable advanced_lab_preserved=true field_evidence=false")
