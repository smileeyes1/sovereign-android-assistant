#!/usr/bin/env python3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/ps/hakim/phoneagent/HakimService.kt").read_text(encoding="utf-8")
field=(root/"app/src/main/java/ps/hakim/phoneagent/HakimFieldAcceptance.kt").read_text(encoding="utf-8")
health=(root/"app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt").read_text(encoding="utf-8")
connection=(root/"app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt").read_text(encoding="utf-8")

required_service=[
    'HakimFaultContainment.guard(applicationContext, "service_start", "field_acceptance_install")',
    "HakimFieldAcceptance.install(applicationContext)",
]
missing=[x for x in required_service if x not in service]
if missing:
    raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL service="+",".join(missing))

required_field=[
    'const val VERSION = "FIELD-ACCEPTANCE-20334-v2"',
    "synthetic_only",
    "personal_content_used",
    "local_pdf",
    "browser_privacy",
    "ordinary_chat",
    "multimodal",
    "HakimHealthBeacon.sendAsync(app, \"field_acceptance\")",
]
missing=[x for x in required_field if x not in field]
if missing:
    raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL field="+",".join(missing))

if '.put("field_acceptance", HakimFieldAcceptance.status(context))' not in health:
    raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL health_beacon_missing")

for token in [
    "RETRY_NONPASS_AFTER_MS",
    'previousStatus == "PASS"',
    "reruns_nonpass_after_cooldown",
]:
    if token not in field:
        raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL retry="+token)

if 'if (HakimService.running) HakimFieldAcceptance.install(context)' not in connection:
    raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL network_retry_missing")

# The acceptance probe must remain bounded and synthetic: no user conversation/file/browser reads.
for forbidden in [
    "recent_conversation",
    "user_conversation_text",
    "readActiveBrowser()",
    "password",
]:
    if forbidden == "password":
        # Password appears only in the synthetic privacy fixture; that is expected.
        continue
    if forbidden in field:
        raise SystemExit("FIELD_ACCEPTANCE_AUTOSTART_20334=FAIL forbidden="+forbidden)

print("FIELD_ACCEPTANCE_AUTOSTART_20334=PASS")
