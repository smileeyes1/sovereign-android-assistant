#!/usr/bin/env python3
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ev=json.loads((root/"governance/HAKIM_20334_FIELD_EVIDENCE.json").read_text(encoding="utf-8"))
p=json.loads((root/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
assert ev["field"]["secure_relay_primary_connected"] is True
assert ev["field"]["secure_relay_state"]=="direct_connected"
assert ev["field"]["legacy_connected"] is False
assert ev["field"]["direct_https_commands_roundtrip_verified"] is True
assert p["secure_relay_primary_field_connected"] is True
assert p["legacy_fallback_field_connected"] is False
assert p["direct_https_commands_roundtrip_verified"] is True
print("DIRECT_CONTROL_FIELD_20334=PASS")
