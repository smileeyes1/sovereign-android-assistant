#!/usr/bin/env python3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
kernel=(root/"app/src/main/java/ps/hakim/phoneagent/HakimCapabilityKernel.kt").read_text(encoding="utf-8")

required = [
"calendar_read","calendar_write","contacts_read","contacts_write",
"sms_read","sms_send","call_place","location_read","alarm_write",
"notification_read","notification_reply","health_read",
"connector_read","connector_write","computer_observe","computer_control"
]
for cap in required:
    assert f'Capability("{cap}"' in kernel, f"missing:{cap}"

# New domains must remain governed: unknown capabilities deny, sensitive/irreversible work gates.
assert '"deny", "unknown_capability"' in kernel
assert 'cap.sensitive || !cap.reversible' in kernel
assert 'if (oneTimeGrant) "allow" else "gate"' in kernel
assert '.put("runtime_matrix", runtimeMatrix(context))' in kernel
assert '.put("capability_is_not_availability", true)' in kernel
assert '.put("availability_is_not_authorization", true)' in kernel

print("CAPABILITY_FABRIC_REGISTRY=PASS")
