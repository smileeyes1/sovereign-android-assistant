from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


one = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
proactive = text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")
evolution = text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
workflow = text(".github/workflows/android.yml")

require("object HakimSovereignOneKernel" in one, "P0: النواة السيادية الواحدة مفقودة")
for token in [
    "single_local_control_plane",
    "signal_bus_local_only",
    "deterministic_router_for_same_observed_frame",
    "all_external_inputs_untrusted_by_default",
    "external_models_are_advisers_not_authority",
    "external_tools_are_replaceable",
    "learning_cannot_expand_authority",
    "evolution_cannot_mutate_core_code_silently",
    "high_impact_requires_gate",
]:
    require(token in one, f"P0: عقد النواة الواحدة مفقود: {token}")

for signal in [
    "USER_INTENT", "MISSION", "EVIDENCE", "FAILURE", "RESOURCE", "NETWORK",
    "MODEL", "TOOL", "FEATURE", "IDEA", "SCIENCE", "POLICY", "LEARNING",
    "EVOLUTION", "RECOVERY", "HEALTH"
]:
    require(signal in one, f"P0: نوع إشارة سيادي مفقود: {signal}")

require("MAX_SIGNAL_EVENTS = 64" in one and "while (arr.length() > MAX_SIGNAL_EVENTS)" in one,
        "P0: سجل الإشارات غير محدود")
require("secretPattern" in one and "[محجوب]" in one,
        "P0: سجل الإشارات قد يحتفظ بسر خام")
require("MessageDigest.getInstance(\"SHA-256\")" in one and "fingerprint(" in one,
        "P0: إطار القرار لا يملك بصمة ثابتة قابلة للمراجعة")
require("System.currentTimeMillis()" not in one[one.index("private fun fingerprint"):],
        "P0: البصمة الحتمية تعتمد على الوقت")
require("startActivity(" not in one and "WebView" not in one and "HakimLocalReasoningBridge.complete" not in one,
        "P0: النواة الواحدة تنفذ أثرًا خارجيًا بدل أن تبقى مركز قرار")
require("http://" not in one and "https://" not in one,
        "P0: النواة الواحدة مرتبطة بعنوان مزود خارجي")
require("HakimDecisionMatrix.evaluate" in one, "P0: القرار لا يُجمع داخل النواة الواحدة")
require("HakimSystemOfSystems.compose" in one, "P0: نظام الأنظمة لا يدخل النواة الواحدة")
require("HakimCapabilityMesh.rank" in one, "P0: الأدوات لا تدخل النواة الواحدة")
require("HakimAdaptiveLearning.status" in one, "P0: التعلم لا يدخل النواة الواحدة")
require("HakimScientificEngineeringKernel" in one or "scientific_engineering_kernel" in one,
        "P0: العلم/الدليل غير ممثل في إطار الإشارات")
require("HakimFaultLedger.status" in one and "HakimConnectionResilience.status" in one,
        "P0: الفشل/التعافي خارج إطار النواة الواحدة")

require("HakimSovereignOneKernel.frame" in sovereign,
        "P0: المحرك السيادي لا يأخذ مساره من النواة الواحدة")
require("val decision = one.decision" in sovereign and "val route = one.route" in sovereign,
        "P0: المحرك السيادي يعيد حساب القرار/المسار خارج النواة")
require("HakimSovereignOneKernel.recordSignal" in sovereign,
        "P0: قرار المهمة لا يعود إلى سجل الإشارات")
require("one_sovereign_kernel" in sovereign,
        "P0: حالة المحرك لا تكشف النواة الواحدة")

require("HakimSovereignOneKernel.frame" in proactive,
        "P0: الاستئناف التلقائي يتخذ قرارًا خارج النواة الواحدة")
require("HakimSovereignOneKernel.recordSignal" in proactive,
        "P0: المبادرة لا تسجل إشارتها في النواة")
require("HakimSovereignOneKernel.recordSignal" in evolution,
        "P0: التطور لا يسجل إشاراته في النواة")
require("SignalKind.HEALTH" in evolution and "SignalKind.EVOLUTION" in evolution,
        "P0: إشارات الصحة/التطور غير موحدة")

for edge in [
    "decision_matrix→one_sovereign_kernel",
    "system_of_systems→one_sovereign_kernel",
    "capability_registry→one_sovereign_kernel",
    "learning→one_sovereign_kernel",
    "adaptive_learning→one_sovereign_kernel",
    "resource_governor→one_sovereign_kernel",
    "one_sovereign_kernel→sovereign_engine",
    "one_sovereign_kernel→proactive_engine",
    "one_sovereign_kernel→agent_system",
]:
    require(edge in fabric, f"P0: وصلة النواة الواحدة مفقودة: {edge}")

require("one_sovereign_kernel_integrated" in fabric,
        "P0: نسيج التكامل لا يعتبر النواة الواحدة شرط سلامة")
require("مركز القرار المحلي واحد" in selfcheck and "ناقل الإشارات محلي فقط" in selfcheck,
        "P0: الفحص الذاتي لا يحرس الوحدة والعزل")
require("النماذج الخارجية مستشارون لا سلطة" in selfcheck,
        "P0: الفحص الذاتي لا يحرس سيادة القرار على النموذج")

require("verify_one_sovereign_kernel.py" in workflow,
        "P0: لا توجد بوابة CI تمنع رجوع مراكز القرار المتعددة")

print("HAKIM_ONE_SOVEREIGN_KERNEL=PASS")
print("HAKIM_LOCAL_SIGNAL_CONTROL_PLANE=PASS")
print("HAKIM_DETERMINISTIC_ROUTE_FOR_SAME_FRAME=PASS")
