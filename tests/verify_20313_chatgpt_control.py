from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
SERVICE=(APP/"HakimService.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
INDEX=(APP/"HakimChatGptIndex.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMO=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("CHATGPT_CONTROL_20313=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20313,"version")
req("chatgpt-web-control" in BUILD,"lineage")
req('"chatgpt_read"' in RELAY and '"chatgpt_navigate"' in RELAY and '"chatgpt_action"' in RELAY,"relay_ops")
read_section=RELAY.split("private val READ_ONLY_OPS",1)[1].split("private val ALLOWED_OPS",1)[0]
req('"chatgpt_read"' in read_section and '"chatgpt_navigate"' in read_section,"read_navigation_classification")
req('"chatgpt_action"' not in read_section,"mutation_gate")
for token in ["chatgpt.com","chatgpt_scope_mismatch","chatgpt_login_required","sync_index","current_messages","send_message"]:
    req(token in SERVICE,"service:"+token)
req("delete_conversation" not in SERVICE and "archive_conversation" not in SERVICE,"no_destructive_chatgpt_ops")
req("AndroidKeyStore" in INDEX and "AES/GCM/NoPadding" in INDEX,"encrypted_index")
req("لا يخزن نصوص المحادثات" in INDEX,"data_minimization")
req('android:name=".HakimAccessibilityService"' not in MANIFEST,"no_accessibility_regression")
req(STATE["android"]["latest_source_parent"]["version_code"]==20312,"baseline_preserved")
req(STATE["android"]["candidate"]["version_code"]==20313,"candidate_state")
req(STATE["android"]["candidate"]["field_verified"] is False,"field_not_claimed")
req(STATE["android"]["field_observed_current"]["version_code"]==20312,"field_baseline_unchanged")
req(PROMO["candidate_version"]==20313 and PROMO["same_signed_apk_field_verified"] is False,"promotion_gate")
req("python3 tests/verify_20313_chatgpt_control.py" in WORKFLOW,"workflow_test")
req('test "$VERSION_CODE" = "20313"' in WORKFLOW,"workflow_version")

print("CHATGPT_CONTROL_20313=PASS scope=chatgpt.com encrypted_index=true accessibility=false field_verified=false")
