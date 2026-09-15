from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


whole = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicWholeCorpusEngine.kt")
corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
policy = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicCorpusPolicy.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
framework = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicFramework.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
providers = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProviderRegistry.kt")
invariants = json.loads(text("governance/HAKIM_QURANIC_SOVEREIGN_INVARIANTS.json"))

# القرآن كله: المجال ١١٤ سورة فعلًا، لا مجرد وصف نصي.
require("allSurahNumbers: List<Int> = (1..114).toList()" in policy,
        "P0: مجال السور الـ١١٤ غير مثبت برمجيًا")
require("fun scanAllSurahs" in corpus,
        "P0: لا يوجد مسح محلي فعلي للقرآن كله")
require("ORDER BY sura_no, aya_no" in corpus,
        "P0: مسح القرآن كله لا يثبت المرور المنظم على قاعدة الآيات")
require("inspected == EXPECTED_AYA_COUNT" in corpus and "covered.size == 114" in corpus,
        "P0: اكتمال ٦٢٣٦ آية/١١٤ سورة غير متحقق في نتيجة المسح")
require("(1..114).all { it in covered }" in corpus,
        "P0: اكتمال أرقام السور الـ١١٤ غير مثبت")
require("scan_limit_does_not_stop_coverage" in corpus,
        "P0: حد النتائج قد يتحول إلى انتقاء بدل مسح كامل")
require("whole_corpus_scan_is_lexical_not_tafsir" in corpus,
        "P0: البحث المعجمي المحلي قد يُقدّم خطأً كتفسير")

# محرك التشغيل: لا انتقاء مسبق ولا صلة قسرية ولا بركة تقنية.
require("object HakimQuranicWholeCorpusEngine" in whole,
        "P0: محرك القرآن كله التشغيلي مفقود")
require("wholeCorpusScanComplete" in whole and "inspectedSurahCount == 114" in whole and "inspectedAyahCount == 6236" in whole,
        "P0: المحرك لا يثبت المسح الكامل قبل اعتماده")
require("noForcedSurahRelevance = true" in whole,
        "P0: لا توجد حماية من إجبار كل سورة على كل مسألة")
require("barakahSpiritualNotTechnical = true" in whole,
        "P0: البركة لم تُفصل صراحة عن الادعاء التقني")
require("worldlyMeansEvidenceBased = true" in whole,
        "P0: الوسائل الدنيوية ليست مقفلة على العلم والدليل والخبرة")
require("لا تنتقِ ما يوافق نتيجة مسبقة" in whole,
        "P0: منع الانتقاء التأكيدي من القرآن غير صريح")

# القلب السيادي يجب أن يستدعي المحرك لا أن يتركه كملف معزول.
require("HakimQuranicWholeCorpusEngine.attestMission(context, goal)" in sovereign,
        "P0: محرك القرآن كله غير موصول بمرحلة تقييم المهمة")
require("wholeCorpusIncomplete" in sovereign and "research_then_replan" in sovereign,
        "P0: فشل المسح الكامل لا يعيد المهمة إلى البحث/التخطيط")
require("HakimQuranicWholeCorpusEngine.promptContext(context, goal)" in sovereign,
        "P0: ميزان القرآن كله لا يصل إلى سياق الاستدلال")
require("!whole.wholeCorpusRequested || whole.wholeCorpusScanComplete" in sovereign,
        "P0: يمكن إغلاق طلب الاستقراء الشامل بلا إثبات ١١٤ سورة")
require('put("quranic_whole_corpus_engine"' in sovereign,
        "P0: حالة محرك القرآن كله غير قابلة للتدقيق")

# النزاهة: الوحي لا يتحول إلى سحر تقني أو بديل للسبب.
require("worldly_means_use_reason_science_experience" in framework,
        "P0: اختيار الوسائل الدنيوية بالدليل غير مثبت")
require("no_technical_mystification" in framework,
        "P0: منع الغموض/الأسطرة التقنية بالقرآن غير مثبت")
require("البركة معنى شرعي" in religious and "لا تُحوّل إلى ادعاء قوة خفية" in religious,
        "P0: معنى البركة غير محمي من الادعاء التقني الخفي")

# الاستقلال: القلب المحلي لا يتبع ChatGPT/Gemini/Copilot، مع صدق حدود القدرات.
for token in ["LOCAL_ONLY", "local_deterministic", "core_runtime_vendor_independent", "single_external_provider_is_not_governor"]:
    require(token in providers, f"P0: ضمان الاستقلال المحلي مفقود: {token}")
require("advanced_model_equivalence_offline_not_claimed" in providers,
        "P0: النظام قد يدعي تكافؤ نموذج متقدم محليًا بلا دليل")
require("advanced_reasoning_optional_for_core" in providers,
        "P0: الاستدلال الخارجي ما زال شرطًا للقلب الحاكم")

# العقد الحاكم نفسه يجب أن يطابق التنفيذ ولا يسمح بتوسيع السلطة باللغة العامة.
require(invariants["quran"]["all_surahs_in_scope"] == 114 and invariants["quran"]["verified_ayah_count"] == 6236,
        "P0: أعداد عقد القرآن الحاكم غير صحيحة")
require(invariants["barakah"]["religious_spiritual_meaning"] is True and invariants["barakah"]["technical_metric"] is False,
        "P0: عقد البركة غير منضبط")
require(invariants["barakah"]["hidden_computational_power"] is False and invariants["barakah"]["guaranteed_worldly_outcome"] is False,
        "P0: العقد يسمح بادعاء قوة خفية أو ضمان دنيوي")
require(invariants["sovereignty"]["core_runtime_vendor_independent"] is True,
        "P0: استقلال القلب عن المزودات غير مقفول")
require(invariants["sovereignty"]["offline_advanced_model_equivalence_claimed"] is False,
        "P0: العقد يدعي تكافؤ نموذج متقدم غير مثبت")
require(invariants["sovereignty"]["authority_cannot_expand_from_general_language"] is True and invariants["sovereignty"]["irreversible_actions_require_specific_gate"] is True,
        "P0: اللغة العامة قد توسع سلطة حكيم أو تتجاوز بوابة الأثر العالي")

print("HAKIM_QURANIC_WHOLE_CORPUS_OPERATING_CORE=PASS")
