from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = (ROOT / "governance/PRODUCT_V1_FINAL_CONTRACT.md").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PRODUCT_V1_FINAL_GATE=FAIL reason=" + reason)

for phrase in [
    "at least one GENERAL_CHAT DIRECT_MODEL engine is configured and field-proven",
    "ordinary non-local chat returns into Hakim",
    "the same signed APK passes field acceptance",
]:
    req(phrase in CONTRACT, "contract:" + phrase)

for key in [
    "direct_engine_field_verified",
    "ordinary_chat_in_app_verified",
    "multimodal_in_app_verified",
    "failover_or_precise_blocker_verified",
    "same_signed_apk_field_verified",
    "promoted",
]:
    req(STATE.get(key) is True, "field_state:" + key)

print("PRODUCT_V1_FINAL_GATE=PASS field_verified=true promoted=true")
