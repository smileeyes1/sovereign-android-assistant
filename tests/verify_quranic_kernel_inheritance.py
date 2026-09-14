from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


kernel = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicInvariantKernel.kt")
quranic = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicFramework.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
decision = text("app/src/main/java/ps/hakim/phoneagent/HakimDecisionMatrix.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
lead = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfLeadershipController.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
auto = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
reason = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
learning = text("app/src/main/java/ps/hakim/phoneagent/HakimLearning.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")

# النواة نفسها: قرآن أصل الميزان، سنة بيان، وعلم تجريبي للوسائل الدنيوية بلا غلو تقني.
require("QURANIC-KERNEL-TO-EDGE" in kernel, "P0: جذر الثقة القرآني من النواة إلى الحافة مفقود")
require("kernel_to_edge_inheritance" in kernel, "P0: وراثة النواة إلى الحافة غير معلنة")
require("atomic_inheritance_is_architectural_metaphor" in kernel, "P0: معنى «ذريًا» المعماري غير مضبوط")
require("no_claim_quran_encodes_nuclear_or_atomic_physics" in kernel, "P0: لا يوجد حاجز يمنع نسبة الفيزياء النووية/الذرية إلى القرآن بلا دليل")
require("العلم والتجربة والمصادر الموثوقة" in kernel, "P0: الوسائل الدنيوية ليست مرتبطة بالدليل التجريبي")
require("نص القرآن أو السنة الدقيق يحتاج تحققًا" in kernel, "P0: التثبت من النص الشرعي الدقيق غير حاكم")

# الإطار القرآني يجب أن يرث النواة مباشرة.
require('HakimQuranicInvariantKernel.requireInherited("quranic_framework")' in quranic,
        "P0: الإطار القرآني لا يرث جذر الثقة مباشرة")
require("HakimQuranicInvariantKernel.promptContext" in quranic,
        "P0: سياق الإطار القرآني لا يحمل جذر الثقة")
require("invariant_kernel" in quranic,
        "P0: حالة الإطار لا تكشف جذر الثقة")
require("الفيزياء الذرية والنووية" in quranic,
        "P0: الفيزياء الذرية/النووية غير مصنفة كوسيلة دنيوية تجريبية")
require("لا تنسب نتيجة تقنية أو تجريبية إلى القرآن بلا دليل" in quranic,
        "P0: حاجز عدم اختلاق أثر تقني للقرآن مفقود")
require("لا تنسب قانونًا ذريًا أو نوويًا إلى القرآن بلا دليل مستقل صالح" in quranic,
        "P0: حاجز عدم نسبة قانون ذري/نووي إلى القرآن بلا دليل مفقود")

# السلسلة الحاكمة: دستور -> إطار -> قرار -> محرك سيادي.
require("HakimQuranicFramework.status()" in constitution and "QURAN-FIRST" in constitution,
        "P0: الدستور منفصل عن الإطار القرآني")
require("HakimQuranicFramework.assess" in decision and "normativeIntegrity" in decision,
        "P0: مصفوفة القرار لا ترث الميزان القرآني")
require("HakimQuranicFramework.assess" in sovereign and "HakimQuranicFramework.promptContext" in sovereign,
        "P0: المحرك السيادي لا يرث الإطار القرآني")
require("اعرض الغاية والأثر على الميزان القرآني" in sovereign,
        "P0: دورة التنفيذ السيادي لا تمر صراحة على الميزان القرآني")

# القيادة والوكلاء والتنفيذ الفعلي يرثون النواة عبر المحرك/المصفوفة، لا بمجرد نص واجهة.
require("HakimDecisionMatrix.evaluate" in lead, "P0: القيادة الذاتية منفصلة عن مصفوفة القرار الموروثة")
require("HakimSovereignEngine.assess" in agents and "HakimSovereignEngine.promptContext" in agents,
        "P0: الوكلاء لا يرثون المحرك السيادي")
require("HakimSovereignEngine.assess" in auto and "HakimDecisionMatrix.evaluate" in auto,
        "P0: التنفيذ المحلي لا يرث القرار السيادي/المعياري")
require("HakimSovereignEngine.recordVerification" in reason and "HakimAuthorityEnvelope" in reason,
        "P0: منفذ الاستدلال لا يعيد النتيجة إلى القلب السيادي")

# التعلم لا يغيّر القلب مباشرة؛ النتائج تمر عبر المحرك السيادي، والتطبيق يثبت الدستور عند البدء.
require("HakimLearning.recordResult" in sovereign, "P0: التعلم التشغيلي منفصل عن التحقق السيادي")
require("لا تدريب نموذج ولا تعديل كود تلقائي عشوائي" in learning,
        "P0: التعلم قد يتحول إلى تعديل ذاتي غير محكوم")
require("HakimConstitution.install(this)" in app,
        "P0: التطبيق لا يثبت الدستور الحاكم عند التشغيل")

print("HAKIM_QURANIC_KERNEL_INHERITANCE=PASS")
