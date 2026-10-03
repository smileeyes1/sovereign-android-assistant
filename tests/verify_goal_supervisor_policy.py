from pathlib import Path

s=Path("app/src/main/java/ps/hakim/phoneagent/HakimGoalSupervisor.kt").read_text(encoding="utf-8")
boot=Path("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
job=Path("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text(encoding="utf-8")
auto=Path("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousContinuation.kt").read_text(encoding="utf-8")

for x in [
    "EvidenceStage", "REQUESTED", "DISPATCHED", "OS_ACCEPTED", "OS_INSTALLED",
    "UI_OBSERVED", "USER_CONFIRMED", "effect_requires_observed_evidence",
    "EFFECT_VERIFIED", "PROVEN_GATE", "tool_success_is_not_goal_success",
    "hypothetical_gate_forbidden", "failure_requires_reroute", "canClose",
    "continue_until_effect_or_proven_gate"
]:
    assert x in s, x

# Lifecycle hooks may call the supervisor directly or through the stronger
# autonomous continuation wrapper, but the invariant must remain explicit.
assert 'HakimAutonomousContinuation.pulse(context, "boot_or_replace")' in boot
assert 'HakimAutonomousContinuation.pulse(applicationContext, "evolution_job")' in job
assert "HakimGoalSupervisor.resume(app)" in auto
assert "HakimGoalExecutor.tick(app)" in auto
assert "HakimValueContinuityEngine.resumePending(app)" in auto

print("GOAL_SUPERVISOR_POLICY=PASS")
