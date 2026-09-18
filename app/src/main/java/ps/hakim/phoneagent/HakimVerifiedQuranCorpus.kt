package ps.hakim.phoneagent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * خزنة محلية للنص القرآني المتحقق. لا تعتبر سياسة تغطية السور الـ١١٤ بديلًا عن امتلاك النص نفسه.
 * لا تقبل corpus على أنه رسمي إلا إذا تطابقت بصمة الأرشيف مع بصمة منشورة من مجمع الملك فهد،
 * ثم اجتاز المحتوى فحص البنية والتغطية الكاملة قبل الاستبدال الذري للبيانات السابقة.
 * بعد الاعتماد تحفظ نسخة المصدر الرسمية نفسها داخل مساحة التطبيق حتى يمكن تصديرها واستعادتها
 * دون اعتماد إلزامي على الشبكة؛ وعند التصدير تعاد مطابقة بصمتها مع المصدر المقبول قبل إخراجها.
 */
object HakimVerifiedQuranCorpus {
    const val OFFICIAL_SOURCE_PAGE = "https://qurancomplex.gov.sa/en/techquran/dev/"
    private const val PREFS = "hakim_verified_quran_corpus"
    private const val EXPECTED_AYA_COUNT = 6236
    private const val DB_NAME = "hakim_verified_quran.db"
    private const val PRESERVED_ARCHIVE_NAME = "hakim-quran-official-source.zip"
    private const val STAGED_ARCHIVE_NAME = "hakim-quran-official-source.new"
    private const val BACKUP_ARCHIVE_NAME = "hakim-quran-official-source.bak"
    private const val MAX_OFFICIAL_ARCHIVE_BYTES = 32L * 1024L * 1024L

    data class SourceSpec(
        val id: String,
        val title: String,
        val md5: String,
        val sha1: String,
        val update: String
    )

    data class Ayah(
        val surah: Int,
        val ayah: Int,
        val surahNameAr: String,
        val text: String,
        val imlaey: String
    )

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val sourceId: String = "",
        val ayahCount: Int = 0
    )

    data class TransferResult(
        val success: Boolean,
        val message: String,
        val bytes: Long = 0L
    )

    /**
     * نتيجة استقراء كامل للنص المحلي الموثق.
     * المرشحات لفظية/استرجاعية فقط وليست تفسيرًا ولا حكمًا ولا إثبات صلة شرعية بذاتها.
     */
    data class CorpusCandidate(
        val surah: Int,
        val ayah: Int,
        val surahNameAr: String,
        val text: String,
        val lexicalScore: Int
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("surah", surah)
            .put("ayah", ayah)
            .put("surah_name_ar", surahNameAr)
            .put("text", text)
            .put("lexical_score", lexicalScore)
    }

    data class FullCorpusScan(
        val ready: Boolean,
        val coverageComplete: Boolean,
        val scannedAyahCount: Int,
        val scannedSurahCount: Int,
        val candidates: List<CorpusCandidate>,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("ready", ready)
            .put("coverage_complete", coverageComplete)
            .put("scanned_ayah_count", scannedAyahCount)
            .put("scanned_surah_count", scannedSurahCount)
            .put("all_114_surahs_scanned", coverageComplete && scannedSurahCount == 114)
            .put("all_6236_ayat_scanned", coverageComplete && scannedAyahCount == EXPECTED_AYA_COUNT)
            .put("retrieval_is_lexical_not_tafsir", true)
            .put("forced_relevance_forbidden", true)
            .put("candidate_count", candidates.size)
            .put("reason", reason)
            .put("candidates", JSONArray(candidates.map { it.toJson() }))
    }

    // بصمات منشورة في منصة المطورين الرسمية. لا تقبل بصمة جديدة تلقائيًا دون تحديث موثق.
    val acceptedSources = listOf(
        SourceSpec(
            id = "KFGQPC_HAFS_SMART_V6",
            title = "خط الرسم العثماني — رواية حفص — للأجهزة الذكية",
            md5 = "53d82b553e5fe919ca1a732e35bf4eb0",
            sha1 = "1dbeae3847880b1c21a956dcfdc0a2d9d490e729",
            update = "6.0"
        ),
        SourceSpec(
            id = "KFGQPC_HAFS_UNICODE_V13",
            title = "خط الرسم العثماني — رواية حفص — Unicode",
            md5 = "cf6841aea5b1d1fd70d032b43ff08278",
            sha1 = "36ea5ab0d7ea1702f17ff43f9b50924cccd77ebf",
            update = "13.0"
        )
    )

    fun importOfficialArchive(context: Context, uri: Uri): ImportResult {
        HakimQuranicInvariantKernel.requireInherited("verified_quran_import")
        val app = context.applicationContext
        val temp = File(app.cacheDir, "hakim_quran_import_${System.currentTimeMillis()}.bin")
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            var total = 0L
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        total += n
                        check(total <= MAX_OFFICIAL_ARCHIVE_BYTES) { "ملف المصدر أكبر من الحد الآمن" }
                        md5.update(buffer, 0, n)
                        sha1.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            } ?: return ImportResult(false, "تعذر قراءة الملف المحدد")

            val md5Hex = md5.digest().hex()
            val sha1Hex = sha1.digest().hex()
            val source = acceptedSources.firstOrNull {
                it.md5.equals(md5Hex, ignoreCase = true) && it.sha1.equals(sha1Hex, ignoreCase = true)
            } ?: run {
                HakimFaultLedger.record(app, "quran_corpus_hash", message = "official_hash_mismatch", severity = HakimFaultLedger.Severity.WARNING)
                return ImportResult(false, "لم تتطابق بصمة الملف مع أي إصدار حفص موثّق حاليًا من المصدر الرسمي. لم يتم اعتماد النص.")
            }

            val ayat = readCsvFromOfficialArchive(temp)
            validate(ayat)
            installArchiveAndDatabase(app, temp, ayat, source)
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("verified", true)
                .putString("source_id", source.id)
                .putString("source_title", source.title)
                .putString("source_update", source.update)
                .putString("md5", md5Hex)
                .putString("sha1", sha1Hex)
                .putBoolean("preserved_official_archive", true)
                .putLong("preserved_archive_bytes", File(app.filesDir, PRESERVED_ARCHIVE_NAME).length())
                .putInt("ayah_count", ayat.size)
                .putInt("surah_count", ayat.map { it.surah }.distinct().size)
                .putLong("verified_at", System.currentTimeMillis())
                .apply()
            HakimFaultLedger.resolve(app, "quran_corpus_import", "verified:${source.id}:${ayat.size}")
            ImportResult(
                true,
                "تم اعتماد قاعدة القرآن المحلية وحفظ نسخة المصدر الرسمية محليًا للاستعادة دون شبكة بعد مطابقة البصمة وفحص السور والآيات.",
                source.id,
                ayat.size
            )
        } catch (t: Throwable) {
            HakimFaultLedger.record(app, "quran_corpus_import", t, severity = HakimFaultLedger.Severity.MATERIAL)
            ImportResult(false, "تعذر اعتماد قاعدة القرآن: ${t.message.orEmpty().take(160)}")
        } finally {
            runCatching { temp.delete() }
            runCatching { File(app.filesDir, STAGED_ARCHIVE_NAME).delete() }
        }
    }

    /**
     * يصدّر نسخة المصدر الرسمية الأصلية لا قاعدة مشتقة منها. قبل الإخراج يعاد حساب MD5 وSHA-1
     * ومطابقتهما مع البصمات الصلبة المقبولة؛ لذلك لا يمكن لملف محلي معدل أن يخرج باعتباره مصدرًا موثقًا.
     */
    fun exportPreservedOfficialArchive(context: Context, uri: Uri): TransferResult {
        HakimQuranicInvariantKernel.requireInherited("verified_quran_export")
        val app = context.applicationContext
        if (!isReady(app)) return TransferResult(false, "النص القرآني المحلي غير مثبت بعد")
        val pair = verifiedPreservedArchive(app)
            ?: return TransferResult(false, "نسخة المصدر الرسمية المحلية غير متاحة أو لم تعد تطابق البصمة الموثقة")
        val file = pair.first
        return try {
            app.contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return TransferResult(false, "تعذر فتح وجهة الحفظ")
            TransferResult(true, "تم تصدير نسخة المصدر القرآني الرسمية المتحققة دون أسرار أو بيانات حسابات.", file.length())
        } catch (t: Throwable) {
            HakimFaultLedger.record(app, "quran_corpus_export", t, severity = HakimFaultLedger.Severity.WARNING)
            TransferResult(false, "تعذر تصدير مصدر القرآن: ${t.message.orEmpty().take(160)}")
        }
    }

    /** الاستعادة تمر بالمسار نفسه تمامًا: بصمة رسمية → بنية → ١١٤ سورة → ٦٢٣٦ آية → استبدال محكوم. */
    fun restorePreservedOfficialArchive(context: Context, uri: Uri): ImportResult = importOfficialArchive(context, uri)

    fun ayah(context: Context, surah: Int, ayah: Int): Ayah? {
        if (!isReady(context) || surah !in 1..114 || ayah <= 0) return null
        return Db(context.applicationContext).readableDatabase.rawQuery(
            "SELECT sura_name_ar, aya_text, aya_text_emlaey FROM aya WHERE sura_no=? AND aya_no=? LIMIT 1",
            arrayOf(surah.toString(), ayah.toString())
        ).use { c ->
            if (!c.moveToFirst()) null else Ayah(surah, ayah, c.getString(0), c.getString(1), c.getString(2).orEmpty())
        }
    }

    /**
     * يمسح فعليًا كل السجلات المحلية الموثقة عند طلب الاستقراء الشامل.
     * لا يفسر الآيات ولا يحكم على صلتها الشرعية؛ بل ينتج مرشحات لفظية من ألفاظ المقصد نفسه فقط.
     * لا توجد بذور قيمية مخفية ولا تُفرض صلة شرعية لمجرد أن الآية تتناول قيمة عامة.
     */
    fun fullCorpusScan(context: Context, rawQuery: String, limit: Int = 28): FullCorpusScan {
        HakimQuranicInvariantKernel.requireInherited("verified_quran_full_scan")
        val app = context.applicationContext
        if (!isReady(app)) {
            return FullCorpusScan(false, false, 0, 0, emptyList(), "قاعدة القرآن المحلية الكاملة غير متحققة")
        }

        val queryTerms = retrievalTerms(rawQuery)
        val matches = ArrayList<CorpusCandidate>()
        val visitedSurahs = HashSet<Int>(114)
        var scanned = 0

        Db(app).readableDatabase.rawQuery(
            "SELECT sura_no, aya_no, sura_name_ar, aya_text, aya_text_emlaey FROM aya ORDER BY sura_no, aya_no",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val surah = c.getInt(0)
                val ayah = c.getInt(1)
                val name = c.getString(2)
                val text = c.getString(3)
                val imlaey = c.getString(4).orEmpty()
                scanned++
                visitedSurahs += surah

                val normalized = normalizeArabic(if (imlaey.isNotBlank()) imlaey else text)
                val score = queryTerms.sumOf { term ->
                    if (normalized.contains(term)) 10 + term.length.coerceAtMost(10) else 0
                }
                if (score > 0) matches += CorpusCandidate(surah, ayah, name, text, score)
            }
        }

        val coverage = scanned == EXPECTED_AYA_COUNT && visitedSurahs.size == 114 && (1..114).all { it in visitedSurahs }
        val safeLimit = limit.coerceIn(1, 64)
        val candidates = matches
            .sortedWith(compareByDescending<CorpusCandidate> { it.lexicalScore }.thenBy { it.surah }.thenBy { it.ayah })
            .take(safeLimit)
        val reason = when {
            !coverage -> "الفحص لم يثبت المرور على القرآن المحلي كاملًا؛ لا يجوز ادعاء الاستقراء الشامل"
            queryTerms.isEmpty() -> "تم فحص السور الـ١١٤ والآيات الـ٦٢٣٦ كاملة، لكن المقصد لم ينتج ألفاظ بحث كافية؛ لا تُفرض آيات عامة قسرًا، ويلزم تحليل دلالي موثوق عند الحاجة"
            candidates.isEmpty() -> "تم فحص السور الـ١١٤ والآيات الـ٦٢٣٦ كاملة، لكن لم تنتج المطابقة اللفظية مرشحات؛ يلزم بحث/تفسير موثوق بدل اختلاق صلة"
            else -> "تم فحص السور الـ١١٤ والآيات الـ٦٢٣٦ كاملة؛ المرشحات الناتجة استرجاع لفظي من ألفاظ المقصد فقط وتحتاج فحص الدلالة والسياق والمصدر قبل الاستنباط"
        }
        return FullCorpusScan(true, coverage, scanned, visitedSurahs.size, candidates, reason)
    }

    fun isReady(context: Context): Boolean {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("verified", false) || p.getInt("ayah_count", 0) != EXPECTED_AYA_COUNT || p.getInt("surah_count", 0) != 114) return false
        return runCatching {
            Db(app).readableDatabase.rawQuery("SELECT COUNT(*), COUNT(DISTINCT sura_no) FROM aya", null).use { c ->
                c.moveToFirst() && c.getInt(0) == EXPECTED_AYA_COUNT && c.getInt(1) == 114
            }
        }.getOrDefault(false)
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ready = isReady(app)
        val preserved = File(app.filesDir, PRESERVED_ARCHIVE_NAME)
        val sourceKnown = acceptedSources.any { it.id == p.getString("source_id", "") }
        val archivePresent = p.getBoolean("preserved_official_archive", false) && preserved.isFile && preserved.length() > 0 && sourceKnown
        return JSONObject()
            .put("verified_local_quran_corpus", true)
            .put("ready", ready)
            .put("all_114_surahs_text_locally_verified", ready)
            .put("full_corpus_scan_available", ready)
            .put("full_corpus_scan_requires_verified_local_text", true)
            .put("full_corpus_scan_is_lexical_retrieval_not_tafsir", true)
            .put("full_corpus_scan_forced_relevance_forbidden", true)
            .put("policy_coverage_is_not_text_coverage", true)
            .put("exact_text_fails_closed_without_verified_source", true)
            .put("official_source_page", OFFICIAL_SOURCE_PAGE)
            .put("accepted_source_count", acceptedSources.size)
            .put("source_id", p.getString("source_id", ""))
            .put("source_title", p.getString("source_title", ""))
            .put("source_update", p.getString("source_update", ""))
            .put("ayah_count", p.getInt("ayah_count", 0))
            .put("surah_count", p.getInt("surah_count", 0))
            .put("verified_at", p.getLong("verified_at", 0L))
            .put("preserved_official_archive", archivePresent)
            .put("preserved_archive_bytes", if (archivePresent) preserved.length() else 0L)
            .put("offline_reimport_source_available", ready && archivePresent)
            .put("export_reverifies_official_hashes", true)
            .put("restore_reuses_full_official_verification", true)
    }

    private fun retrievalTerms(raw: String): List<String> {
        val normalized = normalizeArabic(raw)
        val stop = setOf(
            "القران", "القرءان", "كل", "جميع", "سوره", "سور", "شيء", "شي", "قم", "طبق", "تطبيق", "وكل", "هذا", "هذه", "من", "الى", "على", "في", "عن", "مع", "ثم", "او", "ما", "هو", "هي", "ان", "بكل", "كامل", "كامله"
        )
        return normalized
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stop }
            .distinct()
            .take(24)
            .toList()
    }

    private fun normalizeArabic(value: String): String = value
        .lowercase()
        .replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06EDـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي').replace('ة', 'ه')

    private fun installArchiveAndDatabase(context: Context, archive: File, ayat: List<Ayah>, source: SourceSpec) {
        val target = File(context.filesDir, PRESERVED_ARCHIVE_NAME)
        val staged = File(context.filesDir, STAGED_ARCHIVE_NAME)
        val backup = File(context.filesDir, BACKUP_ARCHIVE_NAME)
        runCatching { staged.delete() }
        runCatching { backup.delete() }
        archive.copyTo(staged, overwrite = true)
        check(staged.length() == archive.length() && staged.length() > 0L) { "تعذر تثبيت نسخة المصدر القرآني كاملة" }
        check(fileDigest(staged, "MD5").equals(source.md5, ignoreCase = true)) { "فشل تحقق MD5 بعد حفظ المصدر محليًا" }
        check(fileDigest(staged, "SHA-1").equals(source.sha1, ignoreCase = true)) { "فشل تحقق SHA-1 بعد حفظ المصدر محليًا" }

        val hadOld = target.exists()
        if (hadOld) check(target.renameTo(backup)) { "تعذر حماية نسخة المصدر السابقة قبل الاستبدال" }
        try {
            check(staged.renameTo(target)) { "تعذر اعتماد نسخة المصدر المحلية الجديدة" }
            replaceDatabaseAtomically(context, ayat)
            runCatching { backup.delete() }
        } catch (t: Throwable) {
            runCatching { target.delete() }
            if (hadOld && backup.exists()) runCatching { backup.renameTo(target) }
            throw t
        } finally {
            runCatching { staged.delete() }
        }
    }

    private fun verifiedPreservedArchive(context: Context): Pair<File, SourceSpec>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = acceptedSources.firstOrNull { it.id == p.getString("source_id", "") } ?: return null
        val file = File(context.filesDir, PRESERVED_ARCHIVE_NAME)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_OFFICIAL_ARCHIVE_BYTES) return null
        val md5 = runCatching { fileDigest(file, "MD5") }.getOrNull() ?: return null
        val sha1 = runCatching { fileDigest(file, "SHA-1") }.getOrNull() ?: return null
        if (!source.md5.equals(md5, ignoreCase = true) || !source.sha1.equals(sha1, ignoreCase = true)) {
            HakimFaultLedger.record(context, "quran_preserved_archive_hash", message = "preserved_source_hash_mismatch", severity = HakimFaultLedger.Severity.MATERIAL)
            return null
        }
        return file to source
    }

    private fun readCsvFromOfficialArchive(file: File): List<Ayah> {
        val candidates = ArrayList<Ayah>(EXPECTED_AYA_COUNT)
        var foundCsv = false
        ZipInputStream(FileInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".csv")) {
                    val name = entry.name.lowercase()
                    if (name.contains("hafs") || name.contains("uthmanic")) {
                        val parsed = parseCsv(BufferedReader(InputStreamReader(zip, Charsets.UTF_8)))
                        if (parsed.size >= EXPECTED_AYA_COUNT) {
                            candidates.clear()
                            candidates.addAll(parsed)
                            foundCsv = true
                            break
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        check(foundCsv) { "لم أجد ملف CSV قرآنيًا كاملًا بالحقول الرسمية داخل الأرشيف" }
        return candidates
    }

    private fun parseCsv(reader: BufferedReader): List<Ayah> {
        val out = ArrayList<Ayah>(EXPECTED_AYA_COUNT)
        val headerLine = reader.readLine() ?: return out
        val header = parseCsvLine(headerLine).map { it.removePrefix("\uFEFF").trim().lowercase() }
        fun index(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val suraI = index("sura_no", "surah_no")
        val ayaI = index("aya_no", "ayah_no")
        val nameI = index("sura_name_ar", "surah_name_ar")
        val textI = index("aya_text", "ayah_text")
        val imlaeyI = index("aya_text_emlaey", "ayah_text_emlaey")
        check(suraI >= 0 && ayaI >= 0 && nameI >= 0 && textI >= 0) { "حقول قاعدة القرآن الرسمية المطلوبة غير موجودة" }
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val row = parseCsvLine(line ?: continue)
            val requiredMax = maxOf(suraI, ayaI, nameI, textI, imlaeyI)
            if (row.size <= requiredMax) continue
            val sura = row[suraI].trim().toIntOrNull() ?: continue
            val aya = row[ayaI].trim().toIntOrNull() ?: continue
            val text = row[textI].trim()
            if (sura !in 1..114 || aya <= 0 || text.isBlank()) continue
            out.add(Ayah(sura, aya, row[nameI].trim(), text, if (imlaeyI >= 0) row[imlaeyI].trim() else ""))
        }
        return out
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out.add(current.toString()); current.setLength(0) }
                else -> current.append(ch)
            }
            i++
        }
        out.add(current.toString())
        return out
    }

    private fun validate(ayat: List<Ayah>) {
        check(ayat.size == EXPECTED_AYA_COUNT) { "عدد الآيات غير مطابق للقاعدة المتوقعة: ${ayat.size}" }
        val keys = HashSet<String>(EXPECTED_AYA_COUNT)
        val surahs = HashSet<Int>(114)
        for (a in ayat) {
            check(a.surah in 1..114 && a.ayah > 0 && a.text.isNotBlank()) { "سجل آية غير صالح" }
            check(keys.add("${a.surah}:${a.ayah}")) { "تكرار في رقم سورة/آية" }
            surahs += a.surah
        }
        check(surahs.size == 114 && (1..114).all { it in surahs }) { "التغطية لا تشمل السور الـ١١٤ كاملة" }
    }

    private fun replaceDatabaseAtomically(context: Context, ayat: List<Ayah>) {
        val db = Db(context).writableDatabase
        db.beginTransaction()
        try {
            db.delete("aya", null, null)
            val values = ContentValues()
            for (a in ayat) {
                values.clear()
                values.put("sura_no", a.surah)
                values.put("aya_no", a.ayah)
                values.put("sura_name_ar", a.surahNameAr)
                values.put("aya_text", a.text)
                values.put("aya_text_emlaey", a.imlaey)
                check(db.insertOrThrow("aya", null, values) != -1L)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun fileDigest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().hex()
    }

    private class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE aya (sura_no INTEGER NOT NULL, aya_no INTEGER NOT NULL, sura_name_ar TEXT NOT NULL, aya_text TEXT NOT NULL, aya_text_emlaey TEXT NOT NULL DEFAULT '', PRIMARY KEY(sura_no, aya_no))")
            db.execSQL("CREATE INDEX idx_aya_sura ON aya(sura_no)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
