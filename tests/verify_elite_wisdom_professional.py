from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)


build = text("app/build.gradle")
wisdom = text("app/src/main/java/ps/hakim/phoneagent/HakimEliteWisdomEngine.kt")
deliberation = text("app/src/main/java/ps/hakim/phoneagent/HakimDeliberationQuality.kt")
protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
executor = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
decision = text("app/src/main/java/ps/hakim/phoneagent/HakimDecisionMatrix.kt")
readiness = text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalReadiness.kt")
standard = text("governance/HAKIM_ELITE_PROFESSIONAL_STANDARD.md")
identity = text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json")

version = re.search(r"versionCode\s+(\d+)", build)
require(version and int(version.group(1)) >= 20029, "P0: الحكمة الاحترافية بلا رقم إصدار جديد")
require("2.0.29-elite-wisdom-professional-quranic" in build, "P0: هوية إصدار 20029 مفقودة")
require('"current_field_version": 20025' in identity, "P0: جرى تزوير خط الأساس الميداني بدل تطوير مرشح")
require('"current_candidate_version": 20029' in identity, "P0: هوية المرشح لا تشير إلى 20029")

# القرآن والسنة ميزان، والوسائل الدنيوية بالدليل بلا سببية غيبية تقنية.
for token in [
    "quranic_value_governance", "authentic_sunnah_guidance", "worldly_means_by_evidence",
    "no_mystical_technical_causation", "truth_gate", "justice_gate", "amanah_gate",
    "rights_gate", "evidence_gate", "uncertainty_calibration", "minimal_intervention",
    "reversibility_first", "no_private_chain_of_thought_required"
]:
    require(token in wisdom, f"P0: مكون الحكمة {token} مفقود")
for ref in ["4:58", "5:8", "49:6", "17:36", "42:38", "7:56"]:
    require(ref in wisdom, f"P0: مرجع مبدأ قرآني {ref} مفقود")
require("لا تنقل نص آية حرفيًا هنا إلا بعد تحقق المصدر" in wisdom, "P0: قد ينقل حكيم نصًا قرآنيًا بلا تثبت")
require("القرآن ليس خوارزمية تقنية ولا ضمانًا ماديًا" in wisdom, "P0: حاجز السببية التقنية الغيبية مفقود")
require("العلم والهندسة والخبرة" in wisdom, "P0: الوسائل الدنيوية لا تُحسم بالدليل الفني")

# سجل قرار مهني منضبط بدل طلب سلسلة التفكير الداخلية.
for token in [
    "protocol_version", "confidence", "alternatives_considered", "assumptions", "unknowns",
    "evidence_needed", "success_criteria", "verification", "risk", "rollback"
]:
    require(token in protocol, f"P0: حقل سجل القرار {token} مفقود")
require("لا تكشف سلسلة التفكير الداخلية" in protocol, "P0: البروتوكول يطلب سلسلة التفكير الخاصة")
require("protocolVersion < 2" in deliberation, "P0: المدقق لا يفرض البروتوكول المهني v2")
require("alternativesConsidered < 2" in deliberation, "P0: فحص البدائل غير مفروض")
require("successCriteria.isEmpty()" in deliberation, "P0: معيار النجاح غير مفروض")
require("verification.isEmpty()" in deliberation, "P0: تحقق ما بعد التنفيذ غير مفروض")
require("rollback.isBlank()" in deliberation, "P0: خطة التراجع غير مفروضة")
require("confidence > wisdom.confidenceCeiling" in deliberation, "P0: الثقة غير معايرة بسقف الدليل")

# دفاع متعدد الطبقات: المزود لا يقرر التنفيذ وحده، والمنفذ يعيد التدقيق.
require("HakimEliteWisdomEngine.promptContext" in bridge, "P0: جسر الاستدلال لا يرث الحكمة")
require("HakimDeliberationQuality.promptContext" in bridge, "P0: جسر الاستدلال لا يطلب سجل قرار مهني")
require(bridge.count("HakimDeliberationQuality.audit") >= 3, "P0: الخطط لا تُدقق عبر مسارات المزودات")
require("HakimDeliberationQuality.audit(plan, mission?.goal.orEmpty())" in executor,
        "P0: منفذ الخطة لا يعيد التدقيق قبل أول فعل")
require("if (!professionalAudit.acceptable)" in executor, "P0: خطة ضعيفة قد تستمر إلى التنفيذ")
require("HakimEliteWisdomEngine.assess" in decision, "P0: مصفوفة القرار لا تمر عبر الحكمة")
require("coerceAtMost(wisdom.confidenceCeiling)" in decision, "P0: المصفوفة تستطيع تجاوز سقف الثقة")

# الجاهزية نفسها لا تدعي نجاحًا ميدانيًا من المصدر.
require("field_verified_current_build" in readiness, "P0: حالة الميدان غير ظاهرة في جاهزية الاحتراف")
require('"NOT_FIELD_VERIFIED"' in readiness, "P0: المصدر قد يعلن نجاحًا ميدانيًا افتراضيًا")
require("no_marketing_superlative_without_evidence" in readiness, "P0: لا يوجد حاجز ضد أوصاف تسويقية غير مثبتة")

for phrase in [
    "فصل الحقائق عن الافتراضات والمجهولات",
    "معايرة الثقة بالدليل",
    "بوابات غير قابلة للتعويض",
    "أقل تدخل",
    "معيار نجاح صريح",
    "لا يُطلب من أي مزود كشف سلسلة التفكير الداخلية",
    "KEEP_BASELINE / NO_OP"
]:
    require(phrase in standard, f"P0: معيار الاحتراف ناقص: {phrase}")

print("ELITE_WISDOM_PROFESSIONAL=PASS")
