#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
supervisor = (root / "app/src/main/java/ps/hakim/phoneagent/HakimGoalSupervisor.kt").read_text(encoding="utf-8")
continuation = (root / "app/src/main/java/ps/hakim/phoneagent/HakimAutonomousContinuation.kt").read_text(encoding="utf-8")
tasks = (root / "app/src/main/java/ps/hakim/phoneagent/HakimTaskManager.kt").read_text(encoding="utf-8")
executive = (root / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")

required_supervisor = [
    "fun resumeIfCondition",
    'p.getString("state", "") != State.WAIT.name',
    'p.getString("resume_condition", "")',
    'condition.split("_or_")',
    'putString("state", State.EXECUTE.name)',
    '"resume_condition_met:" + normalized',
]
for token in required_supervisor:
    if token not in supervisor:
        raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL supervisor=" + token)

required_continuation = [
    "resumeEventFor(reason)",
    "HakimGoalSupervisor.resumeIfCondition(app, resumeEvent)",
    "includeWaiting = waitReleased",
    'reason == "command_center_resume" -> "user_return"',
    'reason == "network_available" || reason == "network_capabilities" -> "network_available"',
    'reason == "secure_relay_connected" || reason == "secure_relay_legacy_connected" -> "relay_connected"',
]
for token in required_continuation:
    if token not in continuation:
        raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL continuation=" + token)

if "includeWaiting: Boolean = false" not in tasks:
    raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL task_manager_default")
if "(includeWaiting && task.state == State.WAITING)" not in tasks:
    raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL waiting_task_gate")

# The existing external wait must remain explicitly resumable and user-return driven.
for token in [
    "Recovery.WAIT_RESUMABLE",
    '"external_result_or_user_return"',
]:
    if token not in executive:
        raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL executive=" + token)

# Do not release arbitrary waits from the periodic fallback.
if 'reason == "evolution_job"' in continuation and '-> "user_return"' in continuation:
    raise SystemExit("RESUMABLE_WAIT_EVENT_GATE=FAIL periodic_must_not_fake_user_return")

print("RESUMABLE_WAIT_EVENT_GATE=PASS")
