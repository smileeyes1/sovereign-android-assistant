package ps.hakim.phoneagent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
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
 */
object HakimVerifiedQuranCorpus {
    const val OFFICIAL_SOURCE_PAGE = "https://qurancomplex.gov.sa/en/techquran/dev/"
    private const val PREFS = "hakim_verified_quran_corpus"
    private const val EXPECTED_AYA_COUNT = 6236
    private const val DB_NAME = "hakim_verified_quran.db"

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
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        md5.update(buffer, 0, n)
                        sha1.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
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
            replaceDatabaseAtomically(app, ayat)
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("verified", true)
                .putString("source_id", source.id)
                .putString("source_title", source.title)
                .putString("source_update", source.update)
                .putString("md5", md5Hex)
                .putString("sha1", sha1Hex)
                .putInt("ayah_count", ayat.size)
                .putInt("surah_count", ayat.map { it.surah }.distinct().size)
                .putLong("verified_at", System.currentTimeMillis())
                .apply()
            HakimFaultLedger.resolve(app, "quran_corpus_import", "verified:${source.id}:${ayat.size}")
            ImportResult(true, "تم اعتماد قاعدة القرآن المحلية بعد مطابقة البصمة الرسمية وفحص السور والآيات.", source.id, ayat.size)
        } catch (t: Throwable) {
            HakimFaultLedger.record(app, "quran_corpus_import", t, severity = HakimFaultLedger.Severity.MATERIAL)
            ImportResult(false, "تعذر اعتماد قاعدة القرآن: ${t.message.orEmpty().take(160)}")
        } finally {
            runCatching { temp.delete() }
        }
    }

    fun ayah(context: Context, surah: Int, ayah: Int): Ayah? {
        if (!isReady(context) || surah !in 1..114 || ayah <= 0) return null
        return Db(context.applicationContext).readableDatabase.rawQuery(
            "SELECT sura_name_ar, aya_text, aya_text_emlaey FROM aya WHERE sura_no=? AND aya_no=? LIMIT 1",
            arrayOf(surah.toString(), ayah.toString())
        ).use { c ->
            if (!c.moveToFirst()) null else Ayah(surah, ayah, c.getString(0), c.getString(1), c.getString(2).orEmpty())
        }
    }

    fun isReady(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("verified", false) || p.getInt("ayah_count", 0) != EXPECTED_AYA_COUNT || p.getInt("surah_count", 0) != 114) return false
        return runCatching {
            Db(context.applicationContext).readableDatabase.rawQuery("SELECT COUNT(*), COUNT(DISTINCT sura_no) FROM aya", null).use { c ->
                c.moveToFirst() && c.getInt(0) == EXPECTED_AYA_COUNT && c.getInt(1) == 114
            }
        }.getOrDefault(false)
    }

    fun status(context: Context): JSONObject {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ready = isReady(context)
        return JSONObject()
            .put("verified_local_quran_corpus", true)
            .put("ready", ready)
            .put("all_114_surahs_text_locally_verified", ready)
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

    private class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE aya (sura_no INTEGER NOT NULL, aya_no INTEGER NOT NULL, sura_name_ar TEXT NOT NULL, aya_text TEXT NOT NULL, aya_text_emlaey TEXT NOT NULL DEFAULT '', PRIMARY KEY(sura_no, aya_no))")
            db.execSQL("CREATE INDEX idx_aya_sura ON aya(sura_no)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
