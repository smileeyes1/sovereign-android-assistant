from pathlib import Path
v=Path("app/src/main/java/ps/hakim/phoneagent/HakimValueContinuityEngine.kt").read_text()
e=Path("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text()
b=Path("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text()
a=Path("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousContinuation.kt").read_text()
assert "fun resumePending" in v
assert 'checkpoint_recovered_verify_before_continue' in v
assert '.put("autonomous_resume", true)' in v
assert (
    "HakimValueContinuityEngine.resumePending(applicationContext)" in e or
    'HakimAutonomousContinuation.pulse(applicationContext, "evolution_job")' in e
)
assert (
    "HakimValueContinuityEngine.resumePending(context)" in b or
    'HakimAutonomousContinuation.pulse(context, "boot_or_replace")' in b
)
assert "HakimValueContinuityEngine.resumePending(app)" in a
assert "HakimGoalSupervisor.resume(app)" in a
assert "HakimGoalExecutor.tick(app)" in a
print("AUTONOMOUS_RESUME_POLICY=PASS")
