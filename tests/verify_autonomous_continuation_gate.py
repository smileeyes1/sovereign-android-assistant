#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / "app/src/main/java/ps/hakim/phoneagent/HakimGoalSupervisor.kt").read_text(encoding="utf-8")
constitution = (root / "app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")

required_supervisor = [
    "no_normal_stop_state",
    "failure_of_means_never_closes_goal",
    "same_failure_forces_reroute",
    "wait_must_be_resumable",
    "resume_after_restart",
    "State.REROUTE",
    "WAIT_RESUMABLE",
    "resume_condition",
    "fun canClose",
    "effect_verified",
]
required_constitution = [
    "default_auto_completion",
    "safe_auto_continue",
    "self_learning_guarded",
    "self_evolution_guarded",
    "فشل الوسيلة لا يعني فشل الغاية",
    "أصلح السبب الجذري أو بدّل الوسيلة",
]

missing = [x for x in required_supervisor if x not in src]
missing += [x for x in required_constitution if x not in constitution]
if missing:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL missing=" + ",".join(missing))

# Prevent a future regression that turns a resumable wait into ordinary closure.
if "enum class State { EXECUTE, VERIFY_EFFECT, DIAGNOSE, REROUTE, WAIT, PROVEN_GATE, EFFECT_VERIFIED }" not in src:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL state_machine_changed")
if "effect -> State.EFFECT_VERIFIED" not in src or "gate -> State.PROVEN_GATE" not in src:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL closure_semantics_changed")

print("AUTONOMOUS_CONTINUATION_GATE=PASS")


runtime = (root / "app/src/main/java/ps/hakim/phoneagent/HakimAutonomousContinuation.kt").read_text(encoding="utf-8")
app = (root / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")
boot = (root / "app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
alarm = (root / "app/src/main/java/ps/hakim/phoneagent/HakimResilienceAlarmReceiver.kt").read_text(encoding="utf-8")
connection = (root / "app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt").read_text(encoding="utf-8")
evolution = (root / "app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text(encoding="utf-8")
tasks = (root / "app/src/main/java/ps/hakim/phoneagent/HakimTaskManager.kt").read_text(encoding="utf-8")
ui = (root / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

runtime_required = [
    "object HakimAutonomousContinuation",
    "fun pulse(context: Context, reason: String)",
    "HakimValueContinuityEngine.resumePending(app)",
    "HakimGoalSupervisor.resume(app)",
    "HakimGoalExecutor.tick(app)",
    "HakimTaskManager.nextAutoResume(app",
    "HakimTaskManager.requestResume(app, task.id)",
    "loopState != \"active\" || restartPulse",
    "high_impact_still_gated",
]
missing_runtime = [x for x in runtime_required if x not in runtime]
if missing_runtime:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL runtime=" + ",".join(missing_runtime))

hooks = {
    "app_start": (app, 'HakimAutonomousContinuation.pulse(this, "app_start")'),
    "boot": (boot, 'HakimAutonomousContinuation.pulse(context, "boot_or_replace")'),
    "alarm": (alarm, 'HakimAutonomousContinuation.pulse(app, "alarm_receiver_$reason")'),
    "network_available": (connection, 'HakimAutonomousContinuation.pulse(context, "network_available")'),
    "network_capabilities": (connection, 'HakimAutonomousContinuation.pulse(context, "network_capabilities")'),
    "periodic": (evolution, 'HakimAutonomousContinuation.pulse(applicationContext, "evolution_job")'),
    "ui_consume": (ui, "maybeResumeAutonomousTask()"),
}
for name, (body, token) in hooks.items():
    if token not in body:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL hook=" + name)

if tasks.count('.put("auto_resume", true)') < 2:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL auto_resume_default")
for token in ["fun pendingResumeRequest", "fun nextAutoResume", "task.autoResume", "task.resumable"]:
    if token not in tasks:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL task_manager=" + token)

print("AUTONOMOUS_RUNTIME_CONTINUATION=PASS")


runner = (root / "app/src/main/java/ps/hakim/phoneagent/HakimAutonomousGoalRunner.kt").read_text(encoding="utf-8")
selfcheck = (root / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

for token in [
    "object HakimAutonomousGoalRunner",
    "fun scheduleIfSafe",
    "HakimModelToolRouter.Channel.LOCAL_RESPONSE",
    "HakimModelToolRouter.Channel.DIRECT_MODEL",
    "if (intent.highImpact || intent.needsUserGate) return false",
    "HakimAttachmentSessionStore.restore(app).isNotEmpty()",
    'pending_visible_task_id", "").orEmpty().isNotBlank()',
    "HakimTaskManager.pendingResumeRequest(app)",
    "HakimTaskManager.consumeResumeRequest(app)",
    "EvidenceStage.DISPATCHED",
    "effectVerified = false",
    "fun finalizeIfVisible",
    "EvidenceStage.UI_OBSERVED",
    "ui_observation_required_before_close",
]:
    if token not in runner:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL headless_runner=" + token)

if "HakimAutonomousGoalRunner.scheduleIfSafe(app, task, reason)" not in runtime:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL headless_not_wired")
if "HakimAutonomousGoalRunner.finalizeIfVisible" not in ui:
    raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL visible_finalize_not_wired")
for token in [
    "HakimAutonomousContinuation.status(context)",
    "HakimAutonomousGoalRunner.status(context)",
]:
    if token not in selfcheck:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL selfcheck=" + token)

print("AUTONOMOUS_HEADLESS_SAFE_RUNNER=PASS")


relay = (root / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
executive = (root / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")
selfcheck = (root / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

for token in [
    'HakimAutonomousContinuation.pulse(context, "secure_relay_connected")',
    'HakimAutonomousContinuation.pulse(context, "secure_relay_legacy_connected")',
]:
    if token not in relay:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL relay_hook=" + token)

for token in [
    "HakimGoalSupervisor.Recovery.REROUTE",
    '"execution_window_exhausted"',
    '"next_autonomous_wakeup"',
]:
    if token not in executive:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL execution_window=" + token)

for token in [
    "HakimAutonomousContinuation.status(context)",
    '"الاستمرار الذاتي الحدثي مفعّل"',
    '.put("autonomous_continuation", autonomous)',
]:
    if token not in selfcheck:
        raise SystemExit("AUTONOMOUS_CONTINUATION_GATE=FAIL selfcheck=" + token)

print("AUTONOMOUS_EVENT_RECOVERY=PASS")
