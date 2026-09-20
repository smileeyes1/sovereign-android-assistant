from pathlib import Path
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimValueContinuityEngine.kt").read_text(encoding="utf-8")
required=[
 "positive_marginal_value_required",
 "anti_loop_no_new_evidence",
 "acceptance_proven",
 "authorization_or_irreversibility_gate",
 "checkpoint_resume",
 "verified_baseline_protected",
 "continuity_is_not_busy_loop",
 "Mode.WAIT",
 "Mode.COMPLETE",
 "Mode.GATE",
]
missing=[x for x in required if x not in s]
assert not missing, f"missing continuity invariants: {missing}"
assert "sameAttemptCount >= 2 && !s.newInformation" in s
assert "net > 0.0" in s
print("VALUE_CONTINUITY_POLICY=PASS")
