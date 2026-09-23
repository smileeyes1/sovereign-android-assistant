from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("LOCAL_FIRST_20094=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20094, "version")
req("versionName '2.0.94-local-first-routing'" in BUILD, "version_name")
req("LOCAL_RESPONSE" in ROUTER, "local_channel_missing")
req('"مرحبا"' in ROUTER and '"السلام عليكم"' in ROUTER, "arabic_greeting_missing")
req("localReply(q) != null" in ROUTER, "local_route_not_first")
req("compactExternalPrompt" in ROUTER, "compact_external_prompt_missing")
req("HakimIntentEngine.governedPrompt(context, prompt)" not in ROUTER, "full_governance_leaked_to_provider")
req("HakimModelToolRouter.Channel.LOCAL_RESPONSE" in CENTER, "local_response_not_wired")
req('recordRoute("local_response", true)' in CENTER, "local_completion_not_recorded")
req("HakimModelToolRouter.recordOutcome(this, provider.id, true)" not in CENTER, "provider_launch_counted_as_success")
req("فتح التطبيق وحده ليس نجاحًا للمهمة" in CENTER, "launch_not_explicitly_unverified")

# Known failure sentinel: a provider launch must never be counted as task success.
probe = CENTER + "\nHakimModelToolRouter.recordOutcome(this, provider.id, true)"
try:
    req("HakimModelToolRouter.recordOutcome(this, provider.id, true)" not in probe, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("LOCAL_FIRST_20094=FAIL reason=sentinel_not_detected")

print("LOCAL_FIRST_20094=PASS greeting=local external=compact launch_not_success")
