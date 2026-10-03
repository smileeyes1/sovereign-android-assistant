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
