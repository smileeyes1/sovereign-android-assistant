from pathlib import Path
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimGoalSupervisor.kt").read_text(encoding="utf-8")
for x in ["enum class Recovery", "RETRY_CHANGED", "REROUTE", "WAIT_RESUMABLE", "PROVEN_GATE",
          "recovery_requires_evidence", "wait_requires_resume_condition", "gate_requires_resume_condition",
          "no_normal_stop_state", "failure_of_means_never_closes_goal", "wait_must_be_resumable", "MAX_SAME_FAILURES", "failure_fingerprint", "same_failure_count", "same_failure_forces_reroute"]:
    assert x in s, x
assert "STOP" not in s
print("EXECUTION_CONTINUITY=PASS")
