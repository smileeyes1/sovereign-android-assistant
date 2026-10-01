from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
SERVICE=(APP/"HakimService.kt").read_text(encoding="utf-8")
FAULT=(APP/"HakimFaultContainment.kt").read_text(encoding="utf-8")
INDEX=(APP/"HakimChatGptIndex.kt").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMO=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("AUTONOMY_20315=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1))>=20315, "version")
candidate=int(m.group(1))
req("chatgpt-web-control" in BUILD and "fault-containment" in BUILD and "coherent-status" in BUILD, "lineage")
req('"chatgpt_read"' in RELAY and '"chatgpt_navigate"' in RELAY and '"chatgpt_action"' in RELAY, "chatgpt_ops")
read_ops=RELAY.split("private val READ_ONLY_OPS",1)[1].split("private val ALLOWED_OPS",1)[0]
req('"chatgpt_action"' not in read_ops, "chatgpt_mutation_must_require_approval")
req('if (!liveConnected && persistedRelayState == "direct_connected")' in RELAY, "coherent_status_guard")
req('.put("secure_relay_state", coherentRelayState)' in RELAY, "coherent_state_output")
req('.put("secure_relay_connected", liveConnected)' in RELAY, "coherent_connected_output")
for token in ["chatgpt.com","chatgpt_scope_mismatch","chatgpt_login_required","sync_index","current_messages","send_message"]:
    req(token in SERVICE, "chatgpt_service:"+token)
req("AndroidKeyStore" in INDEX and "AES/GCM/NoPadding" in INDEX, "encrypted_chat_index")
req("canExecuteHighImpact" in RELAY and "shouldAttempt" in RELAY, "fault_gate_wired")
req("circuit" in FAULT.lower() and "recordFailure" in FAULT, "fault_containment")
verified_parent=STATE["android"]["latest_source_parent"]
field_current=STATE["android"]["field_observed_current"]
req(verified_parent["version_code"]>=20313 and verified_parent["version_code"]<candidate, "verified_parent")
req(field_current["version_code"]==verified_parent["version_code"], "field_baseline")
req(field_current["apk_sha256"]==verified_parent["exact_apk_sha256"], "field_hash")
req(STATE["android"]["candidate"]["version_code"]==candidate, "candidate")
req(STATE["android"]["candidate"]["field_verified"] is False, "no_field_success_inheritance")
req(STATE["productization"]["same_signed_apk_field_verified"] is False, "same_artifact_gate")
req(PROMO["candidate_version"]==candidate and PROMO["promoted"] is False, "promotion_gate")
req("python3 tests/verify_20315_autonomy.py" in WORKFLOW, "workflow_test")
req(('test "$VERSION_CODE" = "'+str(candidate)+'"') in WORKFLOW, "workflow_version")

print(f"AUTONOMY_20315=PASS parent={verified_parent['version_code']} chatgpt=true fault_containment=true coherent_status=true field_verified=false")
