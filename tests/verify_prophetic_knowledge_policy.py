from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimPropheticKnowledgePolicy.kt")
method = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranSunnahMethod.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")

required_domains = [
    "identity_names_titles_lineage",
    "birth_childhood_youth_pre_prophethood",
    "revelation_and_beginning_of_prophethood",
    "meccan_period_dawah_and_persecution",
    "isra_miraj_with_source_verification",
    "hijrah_and_medinan_period",
    "worship_prayer_fasting_hajj_dhikr_dua",
    "character_mercy_justice_patience_truthfulness_trust",
    "household_wives_mothers_of_believers_children_family",
    "ahl_al_bayt_and_companions_with_fairness",
    "daily_guidance_food_dress_sleep_travel_social_conduct",
    "teaching_fatwa_judgment_leadership_and_consultation",
    "dawah_delegations_letters_treaties_and_relations",
    "battles_expeditions_and_conflict_context",
    "miracles_signs_and_prophetic_distinctions",
    "shamail_appearance_manners_and_personal_traits",
    "final_hajj_final_illness_death_and_burial",
    "rights_love_obedience_following_sending_blessings",
    "hadith_attribution_grading_takhrij_and_variants",
    "sirah_chronology_and_disputed_reports",
]

for domain in required_domains:
    require(f'"{domain}"' in policy, f"P0: نطاق نبوي مفقود: {domain}")

require("COVERAGE_DOMAINS.size == 20" in policy, "P0: لا توجد بوابة عددية تمنع إسقاط مجال نبوي")
require('put("local_exhaustive_prophetic_corpus_verified", false)' in policy,
        "P0: النظام قد يدّعي corpus نبويًا محليًا شاملًا بلا دليل")
require('put("all_heritage_reports_assumed_authentic", false)' in policy,
        "P0: التراث الروائي قد يُعامل كله كصحيح")
require('put("specific_attribution_requires_verification", true)' in policy,
        "P0: التثبت من النسبة النبوية المحددة غير مقفل")
require('put("hadith_grade_must_be_preserved_when_material", true)' in policy,
        "P0: درجة الحديث قد تضيع عند النقل")
require('put("sirah_reports_require_source_criticism", true)' in policy,
        "P0: السيرة لا تخضع لنقد المصدر")
require('put("disputed_reports_must_remain_disputed", true)' in policy,
        "P0: الروايات المختلف فيها قد تُعرض كيقين")
require('put("weak_or_fabricated_not_presented_as_authentic", true)' in policy,
        "P0: حاجز الضعيف والموضوع مفقود")
require('put("completeness_claim_fail_closed", true)' in policy,
        "P0: ادعاء الاكتمال النبوي لا يفشل مغلقًا")
require("لا تدّع أن السنة أو السيرة «مكتملة محليًا»" in policy,
        "P0: منع ادعاء اكتمال السنة محليًا مفقود")
require("صحيحا البخاري ومسلم" in policy and "ما عداهما لا يُحكم عليه بمجرد اسم الكتاب" in policy,
        "P0: تمييز مراتب مصادر الحديث غير كافٍ")
require("المغازي والنزاعات والخصائص والمعجزات وأشراط الساعة" in policy,
        "P0: مواضع الروايات عالية الحساسية لا تخضع لتشديد التثبت")
require("HakimQuranicInvariantKernel.requireInherited" in policy,
        "P0: سياسة المعرفة النبوية لا ترث القلب الحاكم")

require("HakimPropheticKnowledgePolicy.assess(raw)" in method,
        "P0: منهج القرآن والسنة لا يستدعي سياسة المعرفة النبوية")
require("HakimPropheticKnowledgePolicy.promptContext(raw)" in method,
        "P0: سياق المعرفة النبوية لا يصل إلى القرار")
require('put("prophetic_knowledge_policy", HakimPropheticKnowledgePolicy.status())' in method,
        "P0: حالة المعرفة النبوية غير ظاهرة")
require("propheticKnowledge.requiresVerification" in method,
        "P0: التثبت من الوقائع النبوية لا يدخل بوابة المصدر")

require("السيرة والشمائل والخصائص والهدي" in religious,
        "P0: النزاهة الشرعية لا تغطي السيرة والشمائل والخصائص والهدي")
require("ميّز بين الصحيح والحسن والضعيف والموضوع" in religious,
        "P0: درجات الحديث الأساسية غير مفصولة")

print("PROPHETIC_KNOWLEDGE_POLICY=PASS")
