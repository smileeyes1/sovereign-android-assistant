from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
LOOP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("EXECUTIVE_LOOP_20098=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20098, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for needed in [
    "MAX_CYCLES = 6",
    "UNDERSTANDING",
    "PLANNING",
    "ROUTING",
    "EXECUTING",
    "VERIFYING",
    "REPAIRING",
    "WAITING_EXTERNAL",
    "COMPLETE",
    "fun providerInstruction",
    "advanceCycle",
]:
    req(needed in LOOP, "loop:" + needed)

for needed in [
    "private lateinit var operations: TextView",
    "HakimExecutiveLoop.start",
    "HakimExecutiveLoop.record",
    "HakimExecutiveLoop.complete",
    "HakimExecutiveLoop.waitExternal",
    'actionButton("إلغاء")',
    "refreshOperations",
]:
    req(needed in CENTER, "ui:" + needed)

req("HakimExecutiveLoop.providerInstruction(context, prompt)" in ROUTER, "router_not_using_executive_instruction")
req("لا تعرض سلسلة التفكير" in (LOOP + DIRECTOR), "private_reasoning_guard_missing")
req("HakimIntentEngine.governedPrompt(context, prompt)" not in ROUTER, "legacy_full_prompt_provider_path")
req("HakimIntentEngine.governedPrompt(this, text)" not in CENTER, "legacy_full_prompt_ui_handoff")
req("recordOutcome(this, provider.id, true)" not in CENTER, "provider_launch_still_success")

# Known-failure sentinel: unbounded self-loop is forbidden.
req("while (true)" not in LOOP, "unbounded_loop")
req("MAX_CYCLES" in LOOP, "cycle_bound_missing")

print("EXECUTIVE_LOOP_GATE=PASS candidate>=20098 bounded=6 operations=visible provider_prompt=private_reasoning")
