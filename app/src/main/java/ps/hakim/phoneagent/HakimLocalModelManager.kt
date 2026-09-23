package ps.hakim.phoneagent

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Manages the fully local, zero-API model file.
 * Download is Wi-Fi/unmetered-only by default because the reference model is ~2.6 GB.
 */
object HakimLocalModelManager {
    const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
    const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val MODEL_SIZE_BYTES = 2_590_000_000L
    const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"

    private const val PREFS = "hakim_local_model"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_VERIFIED_SHA = "verified_sha"

    enum class State {
        MISSING,
        DOWNLOADING,
        DOWNLOADED_UNVERIFIED,
        READY,
        FAILED
    }

    fun modelFile(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(root, "models"), MODEL_FILE)
    }

    fun state(context: Context): State {
        val file = modelFile(context)
        if (file.exists()) {
            val verified = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_VERIFIED_SHA, "")
                .orEmpty()
            return if (verified.equals(MODEL_SHA256, ignoreCase = true)) {
                State.READY
            } else {
                State.DOWNLOADED_UNVERIFIED
            }
        }

        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0) {
            val dm = context.getSystemService(DownloadManager::class.java)
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    return when (status) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED -> State.DOWNLOADING
                        DownloadManager.STATUS_SUCCESSFUL -> State.DOWNLOADED_UNVERIFIED
                        DownloadManager.STATUS_FAILED -> State.FAILED
                        else -> State.MISSING
                    }
                }
            }
        }
        return State.MISSING
    }

    fun enqueueUnmeteredDownload(context: Context): Long {
        val target = modelFile(context)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("حكيم — الذكاء المحلي المجاني")
            .setDescription("تنزيل نموذج Gemma 4 E2B للتشغيل داخل الهاتف دون API")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(target))

        val dm = context.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .remove(KEY_VERIFIED_SHA)
            .apply()
        return id
    }

    fun verify(context: Context): Boolean {
        val file = modelFile(context)
        if (!file.exists() || file.length() < 100_000_000L) return false
        val actual = sha256(file)
        val ok = actual.equals(MODEL_SHA256, ignoreCase = true)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (ok) putString(KEY_VERIFIED_SHA, actual) else remove(KEY_VERIFIED_SHA)
            }
            .apply()
        return ok
    }

    fun delete(context: Context) {
        modelFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_VERIFIED_SHA)
            .apply()
    }

    fun statusArabic(context: Context): String = when (state(context)) {
        State.MISSING -> "الذكاء المحلي: غير منزّل"
        State.DOWNLOADING -> "الذكاء المحلي: جارٍ التنزيل عبر شبكة غير محدودة"
        State.DOWNLOADED_UNVERIFIED -> "الذكاء المحلي: تم التنزيل ويلزم تحقق البصمة"
        State.READY -> "✓ الذكاء المحلي المجاني: جاهز ويعمل دون API"
        State.FAILED -> "الذكاء المحلي: فشل التنزيل ويمكن إعادة المحاولة"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
