from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("VISIBLE_CONVERSATION_20096=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20096, "version")
req("versionName '2.0.96-visible-conversation'" in BUILD, "version_name")

for needed in [
    "ScrollView",
    "private lateinit var conversation: TextView",
    "appendConversation(\"أنت\"",
    "appendConversation(\"حكيم\"",
    "تم الرد داخل حكيم",
    "hakim_conversation",
    "scrollConversationToBottom",
    "اكتب رسالتك إلى حكيم",
]:
    req(needed in CENTER, "missing:" + needed)

req("LOCAL_RESPONSE" in ROUTER, "local_response_missing")
req('"مرحبا"' in ROUTER, "greeting_missing")
req('status.text = reply.ifBlank' not in CENTER, "reply_still_hidden_in_status_only")

# Known failure sentinel: a reply only in status would reproduce the field defect.
probe = CENTER + '\nstatus.text = reply.ifBlank { "تم" }'
try:
    req("status.text = reply.ifBlank" not in probe, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("VISIBLE_CONVERSATION_20096=FAIL reason=sentinel_not_detected")

print("VISIBLE_CONVERSATION_20096=PASS reply=transcript persistent=true")
