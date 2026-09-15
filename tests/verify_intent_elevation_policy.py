from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)

policy = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentElevationPolicy.kt")
heart = text("app/src/main/java/ps/hakim/phoneagent/HakimHeartAlignmentPolicy.kt")
sos = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")

for token in [
    "intent_goal_purpose_elevation",
    "continuous_governed_improvement",
    "tool_failure_does_not_equal_goal_failure",
    "last_verified_baseline_protected",
    "proven_success_protected",
    "no_regression_for_new_improvement",
    "verification_required_for_success_claim",
    "practical_direct_result_required_before_stop",
    "heart_alignment_inherited_by_every_derived_system",
    "wisdom_then_benefit_then_speed",
    "complexity_requires_material_gain",
    "kun_fayakun_not_a_technical_causal_mechanism",
]:
    require(token in policy, f"P0: intent elevation guard missing: {token}")

for stage in ["النية", "المقصد", "الهدف", "الغاية", "الواقع", "القيود", "الدليل", "البدائل", "المفاضلة", "القرار", "التنفيذ", "التحقق", "الإصلاح", "التعلم", "التثبيت"]:
    require(stage in policy, f"P0: elevation stage missing: {stage}")

for token in [
    "heart_alignment_default",
    "adaptive_flexible_continuous_sustainable",
    "intent_values_mercy_conscience_impact",
    "no_claim_to_read_inner_heart_or_unseen",
    "no_emotional_manipulation",
    "practical_direct_result_required",
    "cannot_expand_authority",
]:
    require(token in heart, f"P0: heart alignment guard missing: {token}")

require("HakimHeartAlignmentPolicy.promptContext()" in policy, "P0: heart alignment is not inherited through intent elevation")
require("INTENT_ELEVATION" in sos, "P0: intent elevation is not a system unit")
require("HakimIntentElevationPolicy.promptContext()" in sos, "P0: intent elevation is not injected into system context")
require("HakimIntentElevationPolicy.status()" in sos, "P0: intent elevation status is not exposed")
require("inherits_intent_elevation" in sos, "P0: derived systems do not inherit intent elevation")
require("قاعدة الارتقاء المستمر المحكوم" in governance, "P0: governance does not persist intent elevation")
require("كُن فيكون" in governance and "لا يُحوّل إلى آلية تقنية" in governance, "P0: Kun fayakun boundary missing")
require("ممنوع التوقف عند نجاح شكلي" in policy, "P0: practical direct result stop gate missing")

print("HAKIM_INTENT_ELEVATION_POLICY=PASS")
print("HAKIM_HEART_ALIGNMENT=PASS")
