from pathlib import Path
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
for x in ["goal_supervisor","no_normal_stop_state","failure_of_means_never_closes_goal",
          "same_failure_forces_reroute","wait_must_be_resumable","hypothetical_gate_forbidden"]:
    assert x in s, x
print("SELF_CHECK_CONTINUITY=PASS")
