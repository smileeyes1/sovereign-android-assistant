from pathlib import Path
e=Path("app/src/main/java/ps/hakim/phoneagent/HakimGoalExecutor.kt").read_text(encoding="utf-8")
for x in ["fun tick", "fun heartbeat", '"EXECUTE"', '"VERIFY"', '"REROUTE"', '"WAIT"', '"COMPLETE"', '"GATED"', "heartbeat_at"]:
    assert x in e, x
boot=Path("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
auto=Path("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousContinuation.kt").read_text(encoding="utf-8")
assert (
    "HakimGoalExecutor.tick" in boot
    or 'HakimAutonomousContinuation.pulse(context, "boot_or_replace")' in boot
), "BootReceiver.kt must resume the executor directly or through the autonomous continuation gate"
assert "HakimGoalExecutor.tick(app)" in auto, "autonomous continuation must drive the executor"
evolution=Path("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text(encoding="utf-8")
assert (
    "HakimGoalExecutor.tick" in evolution
    or 'HakimAutonomousContinuation.pulse(applicationContext, "evolution_job")' in evolution
), "HakimEvolutionJobService.kt must resume the executor directly or through the autonomous continuation gate"
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
assert "goal_executor" in s and "HakimGoalExecutor.heartbeat" in s
print("GOAL_EXECUTOR_POLICY=PASS")
