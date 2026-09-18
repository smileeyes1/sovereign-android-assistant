from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

norm = text("app/src/main/java/ps/hakim/phoneagent/HakimNormativeSovereignty.kt")
one = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("object HakimNormativeSovereignty" in norm, "P0: عقد المرجعية السيادية مفقود")
for token in [
    'normative_authority_quran',
    'normative_authority_authentic_sunnah',
    'other_normative_revelation_sources',
    'tafsir_is_human_interpretive_aid_not_revelation',
    'fiqh_is_human_juristic_understanding_not_revelation',
    'worldly_science_is_evidence_for_means_not_revelation',
    'engineering_is_means_not_normative_authority',
    'model_is_adviser_not_normative_authority',
    'platform_is_host_not_normative_authority',
    'provider_is_tool_not_normative_authority',
    'human_user_is_owner_of_intent_data_and_authorized_decision',
    'hakim_is_single_authorized_agent_inside_its_scope',
    'hakim_does_not_own_the_human',
    'hakim_does_not_claim_ownership_of_android_or_hardware',
    'no_hidden_religious_technical_mechanism',
]:
    require(token in norm, f"P0: قيد المرجعية/الملكية مفقود: {token}")

require('.put("other_normative_revelation_sources", false)' in norm,
        "P0: يوجد مصدر وحي معياري ثالث")
require("HakimQuranicInvariantKernel.requireInherited" in norm,
        "P0: عقد المرجعية لا يرث الجذر القرآني")
require("HakimQuranSunnahMethod.status()" in norm,
        "P0: عقد المرجعية منفصل عن منهج القرآن والسنة")
require("لا نموذج ولا منصة ولا مزود" in norm,
        "P0: منع تحوّل التقنية إلى مرجع معياري غير صريح")
require("التفسير والفقه وأقوال العلماء والاجتهاد أدوات بشرية" in norm,
        "P0: الفصل بين الوحي والفهم البشري غير صريح")
require("العلم والتجربة والهندسة" in norm,
        "P0: الوسائل الدنيوية بلا حد معرفي صريح")

require("HakimNormativeSovereignty.require()" in one,
        "P0: النواة السيادية الواحدة لا تفشل مغلقًا عند غياب المرجعية")
require("normative_authority_quran_sunnah_only" in one,
        "P0: حالة النواة لا تكشف حصرية المرجعية")
require("human_owner_hakim_agent" in one,
        "P0: النواة تخلط بين ملكية الإنسان ووكالة حكيم")
require("normative_sovereignty" in one,
        "P0: المرجعية غير مسجلة ضمن حالة النواة")

for edge in [
    "quran_sunnah_method→normative_sovereignty",
    "normative_sovereignty→constitution",
    "normative_sovereignty→one_sovereign_kernel",
    "normative_sovereignty→sovereign_engine",
]:
    require(edge in fabric, f"P0: وصلة المرجعية مفقودة: {edge}")

require("normative_sovereignty_integrated" in fabric,
        "P0: نسيج التكامل لا يعتبر المرجعية شرط سلامة")
require("الإنسان مالك المقصد والبيانات والقرار المأذون" in selfcheck,
        "P0: الفحص الذاتي لا يحرس ملكية الإنسان")
require("لا ادعاء ملكية Android أو العتاد" in selfcheck,
        "P0: الفحص الذاتي لا يحرس حدود ملكية النظام")
require("لا مصدر وحي معياري ثالث" in selfcheck,
        "P0: الفحص الذاتي لا يمنع المرجع الثالث")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) == 20046, "P0: مرشح المرجعية يجب أن يكون ٢٠٠٤٦")
require(policy["current_candidate_version"] == 20046,
        "P0: سياسة التوقيع لا تسجل ٢٠٠٤٦ كمرشح")
require(policy["current_field_version"] == 20040,
        "P0: تم تغيير خط الميدان بلا دليل مباشر")

print("HAKIM_QURAN_SUNNAH_ONLY_NORMATIVE_AUTHORITY=PASS")
print("HAKIM_HUMAN_OWNER_SINGLE_AGENT_SCOPE=PASS")
print("HAKIM_PLATFORM_HOST_NOT_GOVERNOR=PASS")
