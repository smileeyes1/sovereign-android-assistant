from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
STATE=json.loads((ROOT/"governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))

def req(v,reason):
    if not v: raise SystemExit("SELLABLE_PRODUCT_FINAL=FAIL reason="+reason)

for key in [
 "same_signed_apk_field_verified",
 "ordinary_chat_field_verified",
 "local_pdf_field_verified",
 "contextual_followup_field_verified",
 "silent_browser_field_verified",
 "attachment_field_verified",
 "reboot_recovery_field_verified",
 "network_loss_recovery_field_verified",
 "second_device_smoke_verified",
 "sellable",
]:
    req(STATE.get(key) is True,"field_state:"+key)

print("SELLABLE_PRODUCT_FINAL=PASS sellable=true")
