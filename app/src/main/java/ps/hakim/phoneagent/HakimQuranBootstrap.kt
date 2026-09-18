package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * تأسيس محلي منخفض العبء لقاعدة القرآن المتحققة.
 *
 * الرابط مجرد قناة جلب ولا يُمنح الثقة بذاته؛ الاعتماد النهائي لا يحدث إلا داخل
 * HakimVerifiedQuranCorpus بعد مطابقة البصمات المنشورة من مجمع الملك فهد ثم
 * فحص ٦٢٣٦ آية والسور الـ١١٤ كاملة. أي فشل يبقي الحالة السابقة كما هي.
 */
object HakimQuranBootstrap {
    private const val PREFS = "hakim_quran_bootstrap"
    private const val RETRY_MS = 24L * 60L * 60L * 1000L
    private const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L

    // مسار تنزيل مرشح على نطاق المجمع؛ الثقة في المحتوى تأتي من البصمة الرسمية لا من الرابط.
    const val OFFICIAL_ARCHIVE_CANDIDATE =
        "https://download.qurancomplex.gov.sa/resources_dev/UthmanicHafs_v2-0.zip"

    data class Result(
        val success: Boolean,
        val deferred: Boolean,
        val message: String
    )

    fun syncIfNeeded(context: Context): Result {
        HakimQuranicInvariantKernel.requireInherited("quran_autonomous_bootstrap")
        val app = context.applicationContext
        if (HakimVerifiedQuranCorpus.isReady(app)) {
            return Result(true, false, "قاعدة القرآن المحلية متحققة بالفعل؛ لا حاجة لإعادة التنزيل")
        }

        val snapshot = HakimResourceGovernor.snapshot(app)
        if (snapshot.mode == HakimResourceGovernor.Mode.PRESSURE || snapshot.powerSave) {
            return Result(false, true, "أُجّل تثبيت قاعدة القرآن لحماية موارد الهاتف")
        }

        // المسار السيادي الأول: corpus مضمّن ومثبت البصمة داخل APK، بلا شبكة وقت التشغيل.
        val bundled = HakimVerifiedQuranCorpus.installBundledMirrorIfNeeded(app)
        if (bundled.success && HakimVerifiedQuranCorpus.isReady(app)) {
            return Result(true, false, bundled.message)
        }

        // الشبكة ليست شرطًا لوجود القرآن؛ تستخدم فقط لمحاولة ترقية المصدر إلى الأرشيف الرسمي.
        if (snapshot.meteredNetwork) {
            return Result(false, true, "القرآن المضمّن غير متاح/مرفوض، وأُجّل جلب الأرشيف الرسمي لتجنب شبكة محسوبة")
        }
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (cm?.activeNetwork == null) {
            return Result(false, true, "القرآن المضمّن غير متاح/مرفوض ولا توجد شبكة للأرشيف الرسمي؛ بقي النظام fail-closed")
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong("last_attempt_at", 0L)
        if (now - lastAttempt < RETRY_MS) {
            return Result(false, true, "محاولة التأسيس مؤجلة حتى نافذة الصيانة التالية")
        }
        prefs.edit().putLong("last_attempt_at", now).apply()

        val tmp = File.createTempFile("hakim_quran_verified_", ".zip", app.cacheDir)
        return try {
            downloadCandidate(tmp)
            val imported = HakimVerifiedQuranCorpus.importOfficialArchive(app, Uri.fromFile(tmp))
            val ready = imported.success && HakimVerifiedQuranCorpus.isReady(app)
            if (!ready) {
                prefs.edit()
                    .putString("last_result", "rejected:${imported.message.take(120)}")
                    .apply()
                HakimFaultLedger.record(
                    app,
                    "quran_autonomous_bootstrap",
                    message = "downloaded_archive_rejected",
                    severity = HakimFaultLedger.Severity.WARNING
                )
                Result(false, false, imported.message)
            } else {
                prefs.edit()
                    .putLong("last_success_at", now)
                    .putString("last_result", "verified:${imported.sourceId}:${imported.ayahCount}")
                    .apply()
                HakimFaultLedger.resolve(
                    app,
                    "quran_autonomous_bootstrap",
                    "verified:${imported.sourceId}:${imported.ayahCount}"
                )
                Result(true, false, imported.message)
            }
        } catch (t: Throwable) {
            prefs.edit().putString("last_result", "error:${t.message.orEmpty().take(120)}").apply()
            HakimFaultLedger.record(
                app,
                "quran_autonomous_bootstrap",
                t,
                severity = HakimFaultLedger.Severity.WARNING
            )
            Result(false, false, "تعذر الجلب التلقائي المتحقق؛ لم تتغير قاعدة القرآن المحلية")
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun downloadCandidate(destination: File) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val request = Request.Builder()
            .url(OFFICIAL_ARCHIVE_CANDIDATE)
            .header("User-Agent", "Hakim-Quran-Bootstrap/1")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val finalHost = response.request.url.host.lowercase()
            check(finalHost == "qurancomplex.gov.sa" || finalHost.endsWith(".qurancomplex.gov.sa")) {
                "رفض تحويل تنزيل القرآن إلى نطاق غير معتمد"
            }
            val body = response.body ?: error("استجابة المصدر الرسمي فارغة")
            val declared = body.contentLength()
            check(declared <= 0L || declared <= MAX_ARCHIVE_BYTES) { "ملف القرآن أكبر من الحد الآمن" }

            var total = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        total += n
                        check(total <= MAX_ARCHIVE_BYTES) { "تجاوز تنزيل القرآن الحد الآمن" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            check(total > 0L) { "ملف القرآن الذي تم تنزيله فارغ" }
        }
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("autonomous_verified_quran_bootstrap", true)
            .put("ready", HakimVerifiedQuranCorpus.isReady(app))
            .put("official_hash_is_authority_not_url", true)
            .put("bundled_verified_quran_precedes_network", true)
            .put("runtime_network_required_for_quran", false)
            .put("official_network_path_is_optional_upgrade", true)
            .put("all_114_surahs_required", true)
            .put("expected_ayah_count", 6236)
            .put("metered_background_download_forbidden", true)
            .put("max_archive_bytes", MAX_ARCHIVE_BYTES)
            .put("retry_ms", RETRY_MS)
            .put("last_attempt_at", p.getLong("last_attempt_at", 0L))
            .put("last_success_at", p.getLong("last_success_at", 0L))
            .put("last_result", p.getString("last_result", ""))
    }
}
