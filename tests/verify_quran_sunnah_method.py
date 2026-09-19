from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


method = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranSunnahMethod.kt")
quran_corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicCorpusPolicy.kt")
prophetic = text("app/src/main/java/ps/hakim/phoneagent/HakimPropheticKnowledgePolicy.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
one = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
integration = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")

require("HakimQuranicInvariantKernel.requireInherited" in method, "P0: منهج القرآن والسنة لا يرث الجذر القرآني")
require("quran_is_highest_normative_source" in method, "P0: القرآن غير مثبت كمصدر معياري أعلى")
require("authentic_sunnah_is_authoritative_explanation_and_guidance" in method, "P0: السنة الصحيحة غير مثبتة كبيان وهدي")
require("prophetic_example_applies_to_method_and_conduct" in method, "P0: الاقتداء النبوي غير مدمج في طريقة العمل")
require("exact_attribution_requires_verification" in method, "P0: التثبت قبل نسبة النصوص مفقود")
require("revelation_distinct_from_tafsir_fiqh_sirah_and_ijtihad" in method, "P0: الفصل بين الوحي والفهم البشري مفقود")
require("worldly_facts_and_means_require_domain_evidence" in method, "P0: الوسائل الدنيوية لا تُحكم بالدليل المتخصص")
require("no_religious_technical_mystification" in method, "P0: منع تحويل الدين إلى آلية تقنية مفقود")
require("لا تنسب حديثًا أو سنة أو قصة أو فضيلة أو وعدًا" in method, "P0: منع النسبة النبوية غير المتحققة مفقود")
require("لا تحوّل القرآن أو السنة أو البركة أو الدعاء إلى خوارزمية تقنية أو ضمان نتيجة مادية" in method, "P0: حاجز الخلط التقني/الغَيبي مفقود")

# «القرآن كاملًا بكل ما يخصه»: النص الكامل شيء، ونطاق علوم القرآن شيء آخر؛ كلاهما محروس بلا ادعاء زائد.
require("QURAN_KNOWLEDGE_DOMAINS.size == 18" in quran_corpus, "P0: نطاق علوم القرآن الموسع غير مقفل")
for layer in [
    "REVELATION_TEXT", "RASM_DABT_AYAH_NUMBERING", "QIRAAT", "TAJWEED_WAQF_IBTIDA",
    "TAFSIR", "ASBAB_AL_NUZUL", "MAKKI_MADANI_REVELATION_CHRONOLOGY",
    "SURAH_METADATA_THEMES_MAQASID", "MUHKAM_MUTASHABIH", "NASIKH_MANSUKH_CLAIMS",
    "GHARIB_LEXICON", "IRAB_BALAGHA_NAZM", "AHKAM_FIQH_INFERENCE",
    "MUNASABAT_SURAH_AYAH_COHERENCE", "MUSHAF_COMPILATION_TRANSMISSION_HISTORY",
    "TRANSLATIONS_AND_EXPLANATORY_RENDERINGS", "SCHOLARLY_INFERENCE", "WORLDLY_SCIENCE_BOUNDARY",
]:
    require(layer in quran_corpus, f"P0: مجال من علوم القرآن مفقود: {layer}")
require('put("local_exhaustive_secondary_quranic_sciences_corpus_verified", false)' in quran_corpus,
        "P0: قد يُدعى اكتمال corpus علوم القرآن محليًا بلا دليل")
require('put("secondary_sciences_completeness_claim_fail_closed", true)' in quran_corpus,
        "P0: ادعاء اكتمال علوم القرآن لا يفشل مغلقًا")
require('put("translation_is_not_quran_text", true)' in quran_corpus,
        "P0: الترجمة قد تختلط بنص القرآن")
require('put("nasikh_mansukh_claims_require_documented_scholarly_source", true)' in quran_corpus,
        "P0: دعاوى النسخ قد تُقبل بلا مصدر علمي")
require("العقد الأساسي المحفوظ: نص الوحي ≠ القراءات ≠ التفسير ≠ أسباب النزول ≠ بيانات السورة ≠ الاستنباط البشري" in quran_corpus,
        "P0: عقد الفصل القرآني السابق انحدر أثناء التوسعة")
require("لا تدّع أن كل علوم القرآن «مكتملة محليًا»" in quran_corpus,
        "P0: منع ادعاء اكتمال علوم القرآن محليًا مفقود")

# «الرسول محمد ﷺ وكل ما يخصه»: اكتمال نطاق مع فشل مغلق عند الرواية غير الموثقة.
require("HakimPropheticKnowledgePolicy.assess(raw)" in method, "P0: المعرفة النبوية الموثقة غير مدمجة في المنهج")
require("HakimPropheticKnowledgePolicy.promptContext(raw)" in method, "P0: سياق المعرفة النبوية لا يصل إلى القرار")
require('put("prophetic_knowledge_policy", HakimPropheticKnowledgePolicy.status())' in method, "P0: حالة المعرفة النبوية غير ظاهرة")
require("propheticKnowledge.requiresVerification" in method, "P0: الوقائع النبوية المحددة لا تفرض تحقق المصدر")
require("COVERAGE_DOMAINS.size == 20" in prophetic, "P0: نطاق كل ما يخص النبي ﷺ غير مقفل على مجالاته")
require('put("local_exhaustive_prophetic_corpus_verified", false)' in prophetic, "P0: ادعاء corpus نبوي محلي شامل بلا دليل")
require('put("all_heritage_reports_assumed_authentic", false)' in prophetic, "P0: التراث الروائي قد يُعامل كله كصحيح")
require('put("weak_or_fabricated_not_presented_as_authentic", true)' in prophetic, "P0: حاجز الضعيف والموضوع مفقود")
require('put("completeness_claim_fail_closed", true)' in prophetic, "P0: ادعاء الاكتمال النبوي لا يفشل مغلقًا")
require("لا تدّع أن السنة أو السيرة «مكتملة محليًا»" in prophetic, "P0: منع ادعاء اكتمال السنة محليًا مفقود")
require("صحيحا البخاري ومسلم" in prophetic, "P0: مرجعا الصحيحين غير مثبتين في سلم التحقق")

require("HakimSovereignOneKernel.frame" in sovereign and "HakimQuranSunnahMethod.assess(cleanGoal)" in one, "P0: المحرك السيادي لا يمر على منهج القرآن والسنة عبر النواة الواحدة")
require("HakimQuranSunnahMethod.promptContext(goal)" in sovereign, "P0: منهج القرآن والسنة لا يدخل سياق القرار")
require('put("quran_sunnah_method", HakimQuranSunnahMethod.status())' in sovereign, "P0: حالة المنهج غير ظاهرة في المحرك السيادي")
require("افحص الهدي النبوي الصحيح ذي الصلة" in sovereign, "P0: الهدي النبوي غير موجود في سلسلة الاستقلالية")

require('"quran_sunnah_method"' in integration, "P0: منهج القرآن والسنة ليس عقدة في نسيج التكامل")
require('"quranic_kernel→quran_sunnah_method"' in integration, "P0: الجذر القرآني غير موصول بالمنهج")
require('"quran_sunnah_method→sovereign_engine"' in integration, "P0: المنهج غير موصول بالمحرك السيادي")
require("quran_sunnah_method_integrated" in integration, "P0: سلامة دمج المنهج غير مقاسة")
require("methodOk" in integration and "&& methodOk &&" in integration, "P0: فقد المنهج لا يُسقط سلامة القلب")

require("القرآن الكريم هو المصدر الأعلى للهداية" in governance, "P0: النواة الافتراضية لا تصرح بمكانة القرآن الحاكمة")
require("السنة الصحيحة عن سيدنا محمد ﷺ بيان وهدي وقدوة عملية" in governance, "P0: الهدي النبوي غير حاضر في النواة الافتراضية")
require("افصل الوحي عن التفسير والفقه والسيرة والاجتهاد" in governance, "P0: الفصل المعرفي مفقود من النواة الافتراضية")
require("الوقائع والوسائل الدنيوية للعلم والدليل والخبرة الموثوقة" in governance, "P0: الدليل الدنيوي مفقود من النواة الافتراضية")
require("و؟ → و؟ → و؟ → لِمَ؟ → و؟ → و؟ → اعتمد → أصلح → أكمل → هَيّا" in governance, "P0: مسار حكيم الحاكم انحدر")
require("أعلى رفعة مشروعة مثبتة" in governance, "P0: طلب الرفعة غير مقيد بالحقيقة والمشروعية والدليل")

print("QURAN_SUNNAH_METHOD=PASS")
