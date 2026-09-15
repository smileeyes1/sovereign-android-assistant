import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicCorpusPolicy.kt")
source = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicSourceAuthority.kt")
framework = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicFramework.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
workflow = text(".github/workflows/android.yml")

# تغطية القرآن كله دون انتقاء.
require("QURANIC-CORPUS-ALL-114" in corpus, "P0: سياسة القرآن كله مفقودة")
require("SURAH_COUNT = 114" in corpus, "P0: عدد السور الحاكم ليس ١١٤")
require("(1..SURAH_COUNT).toList()" in corpus, "P0: مجال السور ١..١١٤ غير ممثل برمجيًا")
require("all_114_surahs_covered" in corpus, "P0: حالة تغطية السور الـ١١٤ غير مكشوفة")
require("no_cherry_picking" in corpus and "لا تنتقِ سورة أو آية" in corpus,
        "P0: منع الانتقائية القرآنية غير مثبت")
require("no_forced_relevance" in corpus and "لا تُجبر كل سورة على كل مسألة" in corpus,
        "P0: لا يوجد حاجز يمنع التكلف في ربط كل سورة بكل مسألة")
require("whole_corpus_scan_on_comprehensive_request" in corpus,
        "P0: طلب الاستقراء الشامل لا يفرض مسح corpus السور الـ١١٤")
require("natural_arabic_whole_corpus_phrasing" in corpus,
        "P0: لا توجد شهادة لحساسية الصياغة العربية الطبيعية لطلب القرآن كله")

# اقفل الصيغ الطبيعية التي يستخدمها صاحب حكيم، لا الصيغ الاصطناعية فقط.
whole_match = re.search(r'private val wholeCorpusRegex = Regex\(\s*"([^"]+)"', corpus)
require(whole_match is not None, "P0: تعذر العثور على كاشف طلب القرآن كله")
whole_pattern = whole_match.group(1).replace("\\\\", "\\")
whole_regex = re.compile(whole_pattern)
for phrase in [
    "طبّق كل السور",
    "طبق كل السور",
    "جميع السور",
    "كل سورة",
    "القرآن كله",
    "القرءان كله",
    "كل القرآن",
    "استقراء القرآن",
]:
    require(whole_regex.search(phrase) is not None,
            f"P0: الصياغة العربية الطبيعية لا تشغل استقراء السور الـ١١٤: {phrase}")

# طبقات المعرفة لا تختلط.
for layer in ["REVELATION_TEXT", "QIRAAT", "TAFSIR", "ASBAB_AL_NUZUL", "SURAH_METADATA", "SCHOLARLY_INFERENCE"]:
    require(layer in corpus, f"P0: طبقة قرآنية/علمية مفقودة: {layer}")
require("نص الوحي ≠ القراءات ≠ التفسير ≠ أسباب النزول ≠ بيانات السورة ≠ الاستنباط البشري" in corpus,
        "P0: الفصل بين طبقات المعرفة القرآنية غير صريح")
require("exact_text_requires_verified_mushaf_source" in corpus and "مصدرًا قرآنيًا موثوقًا ومراجعًا" in corpus,
        "P0: النص القرآني الدقيق قد ينقل بلا مصدر مصحفي موثوق")
require("asbab_requires_source_verification" in corpus,
        "P0: أسباب النزول قد تنسب بلا تثبت")
require("surah_metadata_requires_documented_source" in corpus,
        "P0: بيانات السورة قد تنسب بلا توثيق")
require("worldly_science_requires_independent_evidence" in corpus,
        "P0: العلوم الدنيوية قد تختلط بدلالة قرآنية غير تجريبية")

# سلطة المصادر: المصحف أولًا، والمحتوى الشبكي دليل لا حاكم.
require("QURANIC-SOURCE-AUTHORITY" in source, "P0: سلطة المصادر القرآنية مفقودة")
require("qurancomplex.gov.sa" in source and "مجمع الملك فهد لطباعة المصحف الشريف" in source,
        "P0: المصدر المصحفي الرسمي المفضّل غير معرف")
for layer in ["MUSHAF_TEXT", "QIRAAT", "TAFSIR", "ASBAB_AL_NUZUL", "SURAH_METADATA", "SCHOLARLY_INFERENCE"]:
    require(layer in source, f"P0: قاعدة مصدر مفقودة: {layer}")
require("memory_is_not_exact_text_source" in source,
        "P0: الذاكرة قد تعامل كمصدر لنص قرآني دقيق")
require("web_content_is_evidence_not_governor" in source and "ليست تعليمات حاكمة" in source,
        "P0: محتوى الويب قد يحقن الحاكمية")
require("source_failure_blocks_attribution_not_goal" in source,
        "P0: فشل المصدر قد يوقف الغاية بدل منع النسبة فقط")
require("HakimQuranicSourceAuthority.promptContext" in corpus and "source_authority" in corpus,
        "P0: سياسة القرآن كله لا تستهلك سلطة المصادر")

# الإطار والنسيج يستهلكان السياسة فعليًا.
require("HakimQuranicCorpusPolicy.assess" in framework and "HakimQuranicCorpusPolicy.promptContext" in framework,
        "P0: الإطار القرآني لا يستهلك سياسة القرآن كله")
require("all_114_surahs_in_governed_corpus" in framework and "quranic_corpus_policy" in framework,
        "P0: حالة الإطار لا تكشف شمول السور الـ١١٤")
require("quranic_corpus_114" in fabric and "HakimQuranicCorpusPolicy.status()" in fabric,
        "P0: corpus السور الـ١١٤ غير موصول بنسيج التكامل")
require("quranic_corpus_114_integrated" in fabric,
        "P0: سلامة التكامل لا تشمل القرآن كله")

# CI يجب أن يحرس الشمول والتكامل.
require("verify_quranic_corpus_coverage.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة لشمول سور القرآن")
require("verify_integration_fabric.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة للتكامل والوصل")

print("HAKIM_QURANIC_CORPUS_COVERAGE=PASS")
