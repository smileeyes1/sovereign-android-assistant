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
one = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
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
require("fun requireInherited" in kernel and "check(a.inherited)" in kernel,
        "P0: جذر القرآن لا يملك حراسة fail-closed وقت التشغيل")

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
require("HakimSovereignOneKernel.frame" in sovereign and "HakimQuranicFramework.assess" in one and "HakimQuranicFramework.promptContext" in sovereign,
        "P0: المحرك السيادي لا يرث الإطار القرآني عبر النواة الواحدة")
require("اعرض الغاية والأثر على الميزان القرآني" in sovereign,
        "P0: دورة التنفيذ السيادي لا تمر صراحة على الميزان القرآني")

# حراسة وقت التشغيل: بدء التطبيق + كل أطوار القلب السيادي.
require('HakimQuranicInvariantKernel.requireInherited("app_start")' in app,
        "P0: التطبيق قد يبدأ دون إقرار جذر القرآن")
for scope in ["sovereign_assess", "sovereign_prompt", "sovereign_execute", "sovereign_verify", "sovereign_complete"]:
    require(f'HakimQuranicInvariantKernel.requireInherited("{scope}")' in sovereign,
            f"P0: المحرك السيادي لا يفرض الجذر وقت التشغيل في {scope}")
require("quranic_invariant_kernel" in sovereign and "HakimQuranicInvariantKernel.status()" in sovereign,
        "P0: حالة المحرك لا تكشف جذر القرآن")

# القيادة والوكلاء والتنفيذ الفعلي يرثون النواة عبر المحرك/المصفوفة، لا بمجرد نص واجهة.
require("HakimDecisionMatrix.evaluate" in lead, "P0: القيادة الذاتية منفصلة عن مصفوفة القرار الموروثة")
require("HakimSovereignEngine.assess" in agents and "HakimSovereignEngine.promptContext" in agents,
        "P0: الوكلاء لا يرثون المحرك السيادي")
require("HakimSovereignEngine.assess" in auto and "HakimDecisionMatrix.evaluate" in auto,
        "P0: التنفيذ المحلي لا يرث القرار السيادي/المعياري")
require("HakimSovereignEngine.recordVerification" in reason and "HakimAuthorityEnvelope" in reason,
        "P0: منفذ الاستدلال لا يعيد النتيجة إلى القلب السيادي")

# التعلم نفسه لا يعمل خارج الجذر ولا يغيّر القلب مباشرة.
for scope in ["learning_initialize", "learning_attempt", "learning_result", "learning_health", "learning_snapshot", "learning_maintenance"]:
    require(f'HakimQuranicInvariantKernel.requireInherited("{scope}")' in learning,
            f"P0: التعلم التشغيلي لا يفرض جذر القرآن في {scope}")
require("HakimLearning.recordResult" in sovereign, "P0: التعلم التشغيلي منفصل عن التحقق السيادي")
require("لا تدريب نموذج ولا تعديل كود تلقائي عشوائي" in learning,
        "P0: التعلم قد يتحول إلى تعديل ذاتي غير محكوم")
require("quranic_kernel_inherited" in learning,
        "P0: حالة التعلم لا تكشف وراثة جذر القرآن")
require("HakimConstitution.install(this)" in app,
        "P0: التطبيق لا يثبت الدستور الحاكم عند التشغيل")

# اختبار مضاد: كلمات «ذرة/نواة/نووي» لا تسمح بخلط العلم التجريبي بالوحي.
require("worldlyMeansEvidenceBased = true" in kernel,
        "P0: العلم الدنيوي قد يصبح تابعًا لادعاء غير تجريبي")
require("noTechnicalMystification = true" in kernel,
        "P0: الغلو التقني في الوحي غير مغلق من الجذر")

print("HAKIM_QURANIC_KERNEL_INHERITANCE=PASS")
