from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


optimizer = text("app/src/main/java/ps/hakim/phoneagent/HakimExcellenceOptimizer.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
quranic = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicFramework.kt")

require("LEXICOGRAPHIC-PARETO" in optimizer, "P0: محسن التفوق الشامل أو نسخته مفقود")
require("HARD_GATE" in optimizer and "REJECT_CANDIDATE" in optimizer, "P0: البوابات الحاكمة لا تمنع البديل الأدنى")
for gate in ["الحقيقة", "السلامة المعيارية/الشرعية", "السلامة", "الحقوق", "السلطة", "الخصوصية"]:
    require(gate in optimizer, f"P0: بوابة عليا مفقودة: {gate}")

ordered = [
    "الصحة والدليل",
    "مطابقة المقصد والاكتمال",
    "الموثوقية والتعافي والتراجع",
    "خفض العبء والكلفة والوقت",
    "قابلية الاستخدام والعرض",
]
positions = [optimizer.find(x) for x in ordered]
require(all(p >= 0 for p in positions), "P0: طبقة من ترتيب التفوق مفقودة")
require(positions == sorted(positions), "P0: ترتيب أهداف التفوق انقلب")

require("MAX_PROTECTED_REGRESSION" in optimizer, "P0: منع الانحدار في الطبقات الأعلى مفقود")
require("MATERIAL_GAIN" in optimizer, "P0: عتبة المكسب المادي مفقودة")
require("KEEP_BASELINE" in optimizer and "NO_OP" in optimizer, "P0: لا يوجد توقف عند انعدام المكسب المادي")
require("pareto_filter" in optimizer and "lexicographic_priority" in optimizer, "P0: Pareto/الترتيب المعجمي غير مثبتين في الحالة")
require("لا تسمح بتحسن طبقة أدنى مقابل انحدار مادي في طبقة أعلى" in optimizer, "P0: قاعدة عدم شراء الأعلى بالأدنى مفقودة")

require("HakimExcellenceOptimizer.promptContext" in sovereign, "P0: المحرك السيادي لا يمرر محسن التفوق")
require("HakimExcellenceOptimizer.status" in sovereign, "P0: حالة محسن التفوق غير مكشوفة للمراقبة")
require("ولّد البدائل اللازمة" in sovereign and "رشّحها بالبوابات والترتيب الأعلى" in sovereign,
        "P0: دورة القرار لا تستخدم البدائل والترشيح الأعلى")
require("لا تغيّر خط الأساس المثبت لتحسين شكلي" in sovereign, "P0: حماية LAST_VERIFIED_BASELINE من التحسين الشكلي مفقودة")

require("quranic_normative_default" in quranic and "worldly_means_use_reason_science_experience" in quranic,
        "P0: محسن التفوق انفصل عن الميزان القرآني/الأسباب الدنيوية")

print("HAKIM_EXCELLENCE_OPTIMIZER=PASS")
