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
release_gate = text("docs/HAKIM_QURANIC_SOVEREIGN_RELEASE_GATE.md")

require("allSurahNumbers: List<Int> = (1..114).toList()" in policy, "P0: مجال السور الـ١١٤ غير مثبت برمجيًا")
require("fun scanAllSurahs" in corpus, "P0: لا يوجد مسح محلي فعلي للقرآن كله")
require("ORDER BY sura_no, aya_no" in corpus, "P0: المسح لا يثبت المرور المنظم على قاعدة الآيات")
require("inspected == EXPECTED_AYA_COUNT" in corpus and "covered.size == 114" in corpus, "P0: اكتمال ٦٢٣٦ آية/١١٤ سورة غير متحقق")
require("(1..114).all { it in covered }" in corpus, "P0: اكتمال أرقام السور الـ١١٤ غير مثبت")
require("scan_limit_does_not_stop_coverage" in corpus, "P0: حد النتائج قد يتحول إلى انتقاء")
require("whole_corpus_scan_is_lexical_not_tafsir" in corpus, "P0: البحث المعجمي قد يُقدّم خطأً كتفسير")

require("object HakimQuranicWholeCorpusEngine" in whole, "P0: محرك القرآن كله مفقود")
require("wholeCorpusScanComplete" in whole and "inspectedSurahCount == 114" in whole and "inspectedAyahCount == 6236" in whole, "P0: المحرك لا يثبت المسح الكامل")
require("noForcedSurahRelevance = true" in whole, "P0: حماية الصلة غير القسرية مفقودة")
require("barakahSpiritualNotTechnical = true" in whole, "P0: البركة لم تُفصل عن الادعاء التقني")
require("worldlyMeansEvidenceBased = true" in whole, "P0: الوسائل الدنيوية ليست مقفلة على الدليل")
require("لا تنتقِ ما يوافق نتيجة مسبقة" in whole, "P0: منع الانتقاء التأكيدي غير صريح")

require("HakimQuranicWholeCorpusEngine.attestMission(context, goal)" in sovereign, "P0: المحرك غير موصول بتقييم المهمة")
require("wholeCorpusIncomplete" in sovereign and "research_then_replan" in sovereign, "P0: فشل المسح لا يعيد التخطيط")
require("HakimQuranicWholeCorpusEngine.promptContext(context, goal)" in sovereign, "P0: ميزان القرآن كله لا يصل للاستدلال")
require("!whole.wholeCorpusRequested || whole.wholeCorpusScanComplete" in sovereign, "P0: يمكن إغلاق الاستقراء بلا إثبات")
require('put("quranic_whole_corpus_engine"' in sovereign, "P0: حالة المحرك غير قابلة للتدقيق")

require("worldly_means_use_reason_science_experience" in framework, "P0: الوسائل الدنيوية بالدليل غير مثبتة")
require("no_technical_mystification" in framework, "P0: منع الأسطرة التقنية غير مثبت")
require("البركة معنى شرعي" in religious and "لا تُحوّل إلى ادعاء قوة خفية" in religious, "P0: معنى البركة غير محمي")

for token in ["LOCAL_ONLY", "local_deterministic", "core_runtime_vendor_independent", "single_external_provider_is_not_governor"]:
    require(token in providers, f"P0: ضمان الاستقلال المحلي مفقود: {token}")
require("advanced_model_equivalence_offline_not_claimed" in providers, "P0: ادعاء تكافؤ نموذج متقدم غير منضبط")
require("advanced_reasoning_optional_for_core" in providers, "P0: الاستدلال الخارجي ما زال شرطًا للقلب")

require(invariants["quran"]["all_surahs_in_scope"] == 114 and invariants["quran"]["verified_ayah_count"] == 6236, "P0: أعداد العقد غير صحيحة")
require(invariants["barakah"]["religious_spiritual_meaning"] is True and invariants["barakah"]["technical_metric"] is False, "P0: عقد البركة غير منضبط")
require(invariants["barakah"]["hidden_computational_power"] is False and invariants["barakah"]["guaranteed_worldly_outcome"] is False, "P0: العقد يسمح بقوة خفية/ضمان دنيوي")
require(invariants["sovereignty"]["core_runtime_vendor_independent"] is True, "P0: استقلال القلب غير مقفول")
require(invariants["sovereignty"]["offline_advanced_model_equivalence_claimed"] is False, "P0: العقد يدعي تكافؤًا غير مثبت")
require(invariants["sovereignty"]["authority_cannot_expand_from_general_language"] is True and invariants["sovereignty"]["irreversible_actions_require_specific_gate"] is True, "P0: اللغة العامة قد توسع السلطة")

for item in ["١١٤ سورة و٦٢٣٦ آية", "مسح كامل فعلي", "FIELD_VERIFIED", "NO-GO"]:
    require(item in release_gate, f"P0: بوابة الإصدار تفتقد: {item}")

print("HAKIM_QURANIC_WHOLE_CORPUS_OPERATING_CORE=PASS")
