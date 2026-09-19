from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimInnovationResiliencePolicy.kt")
elevation = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentElevationPolicy.kt")
sos = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt")
build = text("app/build.gradle")

for token in [
    "innovation_acceleration",
    "problem_and_impact_first",
    "multiple_hypotheses",
    "small_reversible_experiments",
    "explore_exploit_balance",
    "reuse_proven_components",
    "cycle_time_measured",
    "operational_obstacles_trigger_replan",
    "tool_failure_is_not_goal_failure",
    "checkpoint_and_resume",
    "switch_strategy_after_repeated_failure",
    "authoritative_constraints_are_not_bypassed",
    "no_auth_payment_license_or_policy_evasion",
    "no_safety_or_rights_bypass",
    "fallbacks_for_critical_capabilities",
    "direct_usable_result_required",
    "verified_success_required",
    "cannot_expand_authority",
]:
    require(token in policy, f"P0: innovation/resilience guard missing: {token}")

for phrase in [
    "لا يعني فشل الغاية",
    "لا تُتجاوز ولا تُلتف عليها",
    "كسر المصادقة",
    "تجاوز paywall/حصة/ترخيص",
    "غيّر الفرضية أو طبقة الحل",
    "النجاح = نتيجة عملية قابلة للاستخدام",
]:
    require(phrase in policy, f"P0: innovation/resilience semantic guard missing: {phrase}")

require("HakimInnovationResiliencePolicy.promptContext()" in elevation,
        "P0: innovation/resilience policy is not inherited by intent elevation")
require("HakimInnovationResiliencePolicy.status()" in elevation,
        "P0: innovation/resilience status is not exposed by intent elevation")
require("innovation_resilience_inherited_by_every_derived_system" in elevation,
        "P0: derived systems do not explicitly inherit innovation/resilience")
require("HakimIntentElevationPolicy.promptContext()" in sos,
        "P0: system-of-systems does not inject intent elevation")
version = re.search(r"versionCode\s+(\d+)", build)
require(version is not None and int(version.group(1)) >= 20032,
        "P0: innovation/resilience release identity regressed below 20032")
require("innovation-resilience" in build or int(version.group(1)) > 20032,
        "P0: 20032 innovation/resilience identity is neither present nor superseded")

print("HAKIM_INNOVATION_RESILIENCE_POLICY=PASS")
