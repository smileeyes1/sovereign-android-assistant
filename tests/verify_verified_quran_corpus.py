from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def req(cond, msg):
    if not cond:
        raise SystemExit(msg)

corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
bootstrap = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranBootstrap.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
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

# الاستقلال المحلي: نحفظ المصدر الرسمي نفسه لا نسخة نصية غير قابلة لإثبات الأصل.
req("PRESERVED_ARCHIVE_NAME" in corpus and "installArchiveAndDatabase" in corpus,
    "P0: المصدر الرسمي لا يُحفظ محليًا مع القاعدة المتحققة")
req("exportPreservedOfficialArchive" in corpus and "verifiedPreservedArchive" in corpus,
    "P0: لا يوجد تصدير محلي يعيد التحقق من بصمة المصدر قبل الإخراج")
req("restorePreservedOfficialArchive" in corpus and "importOfficialArchive(context, uri)" in corpus,
    "P0: الاستعادة لا تعود لمسار التحقق الرسمي الكامل")
req('fileDigest(staged, "MD5")' in corpus and 'fileDigest(staged, "SHA-1")' in corpus,
    "P0: النسخة المحلية للمصدر لا تُفحص بعد الحفظ")
req("offline_reimport_source_available" in corpus and "export_reverifies_official_hashes" in corpus,
    "P0: حالة الاستقلال القرآني لا تعرض توفر إعادة الاستيراد دون شبكة")
req("MAX_OFFICIAL_ARCHIVE_BYTES" in corpus,
    "P0: استيراد المصدر القرآني بلا حد حجم وقائي")

# التأسيس التلقائي: الرابط قناة فقط؛ الاعتماد يبقى للبصمة الرسمية وفحص النص كاملًا.
req("object HakimQuranBootstrap" in bootstrap, "P0: تأسيس القرآن المحلي التلقائي مفقود")
req("download.qurancomplex.gov.sa" in bootstrap, "P0: مسار الجلب التلقائي لا يبدأ من نطاق المجمع")
req("official_hash_is_authority_not_url" in bootstrap,
    "P0: قد يصبح رابط التنزيل نفسه مصدر ثقة بدل البصمة الرسمية")
req("HakimVerifiedQuranCorpus.importOfficialArchive" in bootstrap,
    "P0: الجلب التلقائي يتجاوز بوابة التحقق الرسمية")
req("HakimVerifiedQuranCorpus.isReady" in bootstrap,
    "P0: الجلب التلقائي لا يثبت جاهزية القاعدة بعد الاستيراد")
req("MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L" in bootstrap,
    "P0: تنزيل قاعدة القرآن بلا حد حجم")
req("snapshot.meteredNetwork" in bootstrap,
    "P0: حكيم قد يستهلك شبكة محسوبة تلقائيًا لتنزيل قاعدة القرآن")
req("Mode.PRESSURE" in bootstrap and "snapshot.powerSave" in bootstrap,
    "P0: تأسيس القرآن التلقائي لا يحترم ضغط الموارد/توفير الطاقة")
req("RETRY_MS = 24L * 60L * 60L * 1000L" in bootstrap,
    "P0: فشل المصدر قد يسبب حلقة تنزيل متكررة")
req("finalHost == \"qurancomplex.gov.sa\"" in bootstrap and "endsWith(\".qurancomplex.gov.sa\")" in bootstrap,
    "P0: إعادة توجيه التنزيل قد تغادر نطاق المصدر المعتمد")
req("runCatching { tmp.delete() }" in bootstrap,
    "P0: ملف القرآن المؤقت قد يبقى بلا تنظيف")
req("HakimQuranBootstrap.syncIfNeeded(app)" in app,
    "P0: التأسيس التلقائي موجود لكنه غير موصول بدورة صيانة حكيم")
req("HakimResourceGovernor.canRunNonEssentialBackground" in app,
    "P0: تأسيس القرآن قد ينافس المهمة الحالية تحت ضغط الموارد")

req("اعتماد الملف الرسمي" in settings and "فتح المصدر الرسمي" in settings, "P0: لا توجد واجهة لاعتماد المصدر الرسمي")
req("HakimVerifiedQuranCorpus.importOfficialArchive" in settings, "P0: واجهة القرآن غير موصولة بالتحقق الفعلي")
req("تصدير المصدر الموثق" in settings and "استعادة المصدر الموثق" in settings,
    "P0: المستخدم لا يستطيع حفظ المصدر القرآني الموثق واستعادته دون شبكة")
req("HakimVerifiedQuranCorpus.exportPreservedOfficialArchive" in settings,
    "P0: زر تصدير المصدر القرآني غير موصول بإعادة فحص البصمة")
req("HakimVerifiedQuranCorpus.restorePreservedOfficialArchive" in settings,
    "P0: زر الاستعادة لا يعيد التحقق من المصدر كاملًا")
req("verify_verified_quran_corpus.py" in workflow, "P0: بوابة القرآن المتحقق غير موصولة بـCI")
print("HAKIM_VERIFIED_QURAN_CORPUS=PASS")
