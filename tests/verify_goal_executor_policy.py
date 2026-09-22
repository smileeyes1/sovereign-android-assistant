from pathlib import Path
e=Path("app/src/main/java/ps/hakim/phoneagent/HakimGoalExecutor.kt").read_text(encoding="utf-8")
for x in ["fun tick", "fun heartbeat", '"EXECUTE"', '"VERIFY"', '"REROUTE"', '"WAIT"', '"COMPLETE"', '"GATED"', "heartbeat_at"]:
    assert x in e, x
for p in ["HakimEvolutionJobService.kt","BootReceiver.kt"]:
    s=Path("app/src/main/java/ps/hakim/phoneagent/"+p).read_text(encoding="utf-8")
    assert "HakimGoalExecutor.tick" in s, p
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
assert "goal_executor" in s and "HakimGoalExecutor.heartbeat" in s
print("GOAL_EXECUTOR_POLICY=PASS")
