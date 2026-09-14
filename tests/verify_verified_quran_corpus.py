from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def req(cond, msg):
    if not cond:
        raise SystemExit(msg)

corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
policy = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicCorpusPolicy.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
workflow = text(".github/workflows/android.yml")

req("object HakimVerifiedQuranCorpus" in corpus, "P0: خزنة القرآن المتحقق مفقودة")
req("qurancomplex.gov.sa/en/techquran/dev/" in corpus, "P0: المصدر الرسمي للمطورين غير مثبت")
req("53d82b553e5fe919ca1a732e35bf4eb0" in corpus, "P0: MD5 إصدار حفص للأجهزة الذكية غير مثبت")
req("1dbeae3847880b1c21a956dcfdc0a2d9d490e729" in corpus, "P0: SHA-1 إصدار حفص للأجهزة الذكية غير مثبت")
req("cf6841aea5b1d1fd70d032b43ff08278" in corpus.lower(), "P0: MD5 إصدار حفص Unicode غير مثبت")
req("36ea5ab0d7ea1702f17ff43f9b50924cccd77ebf" in corpus.lower(), "P0: SHA-1 إصدار حفص Unicode غير مثبت")
req("EXPECTED_AYA_COUNT = 6236" in corpus, "P0: التغطية الكاملة للآيات غير محروسة")
req("surahs.size == 114" in corpus and "1..114" in corpus, "P0: السور ١١٤ غير محروسة في الاستيراد")
req("official_hash_mismatch" in corpus, "P0: ملف قرآن غير مطابق قد يُعتمد")
req("exact_text_fails_closed_without_verified_source" in corpus, "P0: النص الدقيق لا يفشل مغلقًا دون مصدر متحقق")
req("policy_coverage_is_not_text_coverage" in corpus, "P0: قد يُخلط بين سياسة التغطية وامتلاك النص")
req("whole_corpus_scan_on_comprehensive_request" in policy, "P0: سياسة الاستقراء الشامل للـ١١٤ سورة مفقودة")
req("اعتماد الملف الرسمي" in settings and "فتح المصدر الرسمي" in settings, "P0: لا توجد واجهة لاعتماد المصدر الرسمي")
req("HakimVerifiedQuranCorpus.importOfficialArchive" in settings, "P0: واجهة القرآن غير موصولة بالتحقق الفعلي")
req("verify_verified_quran_corpus.py" in workflow, "P0: بوابة القرآن المتحقق غير موصولة بـCI")
print("HAKIM_VERIFIED_QURAN_CORPUS=PASS")
