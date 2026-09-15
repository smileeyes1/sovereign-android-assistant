from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

build = text("app/build.gradle")
question = text("app/src/main/java/ps/hakim/phoneagent/HakimQuestionOperator.kt")
nstar = text("app/src/main/java/ps/hakim/phoneagent/HakimAdaptiveNStarLoop.kt")
science = text("app/src/main/java/ps/hakim/phoneagent/HakimScientificEngineeringKernel.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
deliberation = text("app/src/main/java/ps/hakim/phoneagent/HakimDeliberationQuality.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
readiness = text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalReadiness.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
standard = text("governance/HAKIM_ELITE_PROFESSIONAL_STANDARD.md")

version = re.search(r"versionCode\s+(\d+)", build)
require(version and int(version.group(1)) >= 20029, "P0: قاعدة ؟/العلوم بلا إصدار 20029+")

for token in [
    "WHAT_IS_UNKNOWN", "WHAT_IS_EVIDENCE", "WHAT_CAN_FAIL", "WHAT_IS_IMPACT",
    "WHAT_IS_ALTERNATIVE", "WHY_THIS", "WHAT_NEXT", "WHAT_STOPS_US",
    "execution_requires_material_unknowns_closed", "no_infinite_questioning"
]:
    require(token in question, f"P0: قاعدة ؟ ناقصة: {token}")
require('plan.phase == "execute" && unresolved > 0' in question,
        "P0: التنفيذ لا يتوقف عند مجهول مادي مفتوح")
require("HakimQuestionOperator.gate(plan)" in deliberation,
        "P0: ناقد المداولة لا يطبق قاعدة ؟")
require("HakimQuestionOperator.promptContext" in deliberation,
        "P0: المزود لا يرث قاعدة ؟/و؟")

for stage in ["UNDERSTAND", "RESEARCH", "MODEL", "PLAN", "CRITIQUE", "EXECUTE", "VERIFY", "REPAIR", "LEARN", "COMPLETE"]:
    require(stage in nstar, f"P0: مرحلة ن★ مفقودة: {stage}")
for token in ["MIN_MATERIAL_GAIN", "MAX_STAGNANT_ROUNDS", "MAX_ROUNDS", "no_infinite_loop", "failure_of_tool_not_failure_of_goal"]:
    require(token in nstar, f"P0: ن★ ناقصة: {token}")
require("KEEP_BASELINE/NO_OP" in nstar, "P0: ن★ لا تتوقف عند انعدام المكسب")

for domain in ["MATHEMATICS", "PHYSICS", "CHEMISTRY", "ENGINEERING", "SECURITY", "SAFETY"]:
    require(domain in science, f"P0: المجال العلمي/الهندسي {domain} مفقود")
for phrase in [
    "تحليل الأبعاد والوحدات SI", "موازنة المعادلات والحفظ الذري", "أنماط الفشل وحدود الأمان",
    "نموذج تهديد", "شدة واحتمال التعرض", "scientific_causation_separate_from_revelation"
]:
    require(phrase in science, f"P0: فحص علمي/هندسي مفقود: {phrase}")

require("ADAPTIVE-NSTAR-QUESTION" in constitution and "personal_sovereignty_default" in constitution and "halal_shubuhat_guard_default" in constitution, "P0: الدستور لم يحتفظ بقاعدة ؟ ضمن النسخة الحاكمة الأحدث")
for token in ["question_operator_contract", "question_operator_default", "recursive_what_next_default", "no_infinite_questioning"]:
    require(token in constitution, f"P0: قاعدة ؟ غير مثبتة في الدستور: {token}")
for link in ["HakimQuestionOperator.promptContext", "HakimAdaptiveNStarLoop.promptContext", "HakimScientificEngineeringKernel.promptContext"]:
    require(link in sovereign, f"P0: المحرك السيادي لا يرث {link}")

for agent in ["MATHEMATICS", "PHYSICS", "CHEMISTRY", "ENGINEERING", "SECURITY"]:
    require(agent in agents, f"P0: الوكيل التخصصي {agent} مفقود")
require("scientific.researchRequired" in agents, "P0: الوكلاء العلمية لا ترفع التحقق/البحث عند الحاجة")

for token in ["question_operator", "adaptive_nstar", "scientific_engineering", "benchmark_required_for_no_peer_claim", "no_absolute_omnipotence_claim"]:
    require(token in readiness, f"P0: جاهزية الاحتراف لا تعرض {token}")
require("لَيْسَ كَمِثْلِهِ شَيْءٌ" in religious,
        "P0: حارس التنزيه الإلهي غير مثبت في النزاهة الشرعية")
require("لا يُستعمل كوصف لحكيم" in religious,
        "P0: التطبيق قد يستعمل وصف التنزيه الإلهي لنفسه")
for phrase in [
    "## قاعدة «؟ / و؟»", "لا يبدأ التنفيذ المؤثر مع مجهول مادي مفتوح",
    "الرياضيات والفيزياء والكيمياء والهندسة والأمن والسلامة",
    "لا تُقبل إلا كهدف تنافسي بقياس ومقارنة ودليل"
]:
    require(phrase in standard, f"P0: المعيار المهني لا يثبت: {phrase}")

print("QUESTION_SCIENCE_NSTAR=PASS")
