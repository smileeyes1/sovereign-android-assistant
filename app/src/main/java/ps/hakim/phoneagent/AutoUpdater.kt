package ps.hakim.phoneagent

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * مُحضّر تحديثات حكيم الآمن.
 *
 * يكتشف وينزّل ويتحقق من تحديث حكيم، ثم يصدّر APK المتحقق إلى مجلد التنزيلات.
 * لا يملك هذا المكوّن قدرة تثبيت الحزم ولا يطلب صلاحية "مصادر غير معروفة".
 * التثبيت نفسه يبقى فعلًا صريحًا للمستخدم عبر أندرويد/مدير الملفات.
 */
object AutoUpdater {
    private const val UPDATE_TOPIC = "hakim-stable-updates-4f9c92e6a1b74d8d8b372e9e69fdc2c1"
    private const val UPDATE_TITLE = "HAKIM_UPDATE"
    private const val CHANNEL_ID = "hakim_updates"
    private const val JOB_ID = 771204
    private const val MAX_APK_BYTES = 32L * 1024L * 1024L
    private const val FIELD_CERT_SHA256 = "d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3"
    private const val PERIOD_MS = 15L * 60L * 1000L
    private const val PREFS = "hakim"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var updateSocket: WebSocket? = null
    @Volatile private var realtimeStarting = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            val result = scheduler.schedule(job)
            state(context, if (result == JobScheduler.RESULT_SUCCESS) "scheduled" else "schedule_failed")
        } catch (e: Exception) {
            state(context, "schedule_failed", e.message.orEmpty(), true)
        }
    }

    fun checkAsync(context: Context) {
        Thread {
            try {
                checkNow(context.applicationContext)
            } catch (e: Exception) {
                state(context.applicationContext, "check_exception", e.message.orEmpty(), true)
            }
        }.start()
    }

    fun checkNow(context: Context) {
        createUpdateChannel(context)
        state(context, "checking")
        prefs(context).edit().putLong("last_update_check_at", System.currentTimeMillis()).apply()

        val currentVersion = currentVersionCode(context)
        val request = Request.Builder()
            .url("https://ntfy.sh/$UPDATE_TOPIC/json?poll=1&since=6h")
            .header("User-Agent", "HAKIM-SafeUpdate/3")
            .build()

        val response = try { client.newCall(request).execute() } catch (e: Exception) {
            state(context, "check_network_failed", e.message.orEmpty(), true)
            return
        }

        response.use { r ->
            if (!r.isSuccessful) {
                state(context, "check_http_failed", "HTTP ${r.code}", true)
                return
            }
            val body = r.body?.string().orEmpty()
            var chosen: JSONObject? = null
            var chosenTime = 0L
            for (line in body.lineSequence()) {
                if (line.isBlank()) continue
                val event = try { JSONObject(line) } catch (_: Exception) { continue }
                if (event.optString("event") != "message") continue
                if (event.optString("title") != UPDATE_TITLE) continue
                val attachment = event.optJSONObject("attachment") ?: continue
                val url = attachment.optString("url")
                if (!url.startsWith("https://")) continue
                val meta = try { JSONObject(event.optString("message", "{}")) } catch (_: Exception) { continue }
                val version = meta.optLong("version_code", -1L)
                val sha = meta.optString("sha256").lowercase()
                val size = meta.optLong("size", attachment.optLong("size", -1L))
                val at = event.optLong("time", 0L)
                if (version <= currentVersion || sha.length != 64 || size !in 1..MAX_APK_BYTES) continue
                if (at >= chosenTime) {
                    chosenTime = at
                    chosen = JSONObject()
                        .put("url", url)
                        .put("version_code", version)
                        .put("sha256", sha)
                        .put("size", size)
                }
            }

            val update = chosen
            if (update == null) {
                state(context, "up_to_date")
                return
            }

            prefs(context).edit()
                .putLong("last_update_discovered_at", System.currentTimeMillis())
                .putLong("last_update_discovered_version", update.optLong("version_code", -1L))
                .apply()
            state(context, "update_found", "v=${update.optLong("version_code")}")
            downloadVerifyAndPrepare(context, update)
        }
    }

    private fun downloadVerifyAndPrepare(context: Context, update: JSONObject) {
        val url = update.getString("url")
        val expectedSha = update.getString("sha256")
        val expectedVersion = update.getLong("version_code")
        val expectedSize = update.getLong("size")
        val target = File(context.cacheDir, "hakim-update-$expectedVersion.apk")
        if (target.exists()) target.delete()

        state(context, "downloading", "v=$expectedVersion")
        val req = Request.Builder().url(url).header("User-Agent", "HAKIM-SafeUpdate/3").build()
        val response = try { client.newCall(req).execute() } catch (e: Exception) {
            state(context, "download_failed", e.message.orEmpty(), true)
            return
        }

        response.use { r ->
            if (!r.isSuccessful) {
                state(context, "download_http_failed", "HTTP ${r.code}", true)
                return
            }
            val body = r.body ?: run {
                state(context, "download_empty", "", true)
                return
            }
            val length = body.contentLength()
            if (length > MAX_APK_BYTES || (length > 0 && expectedSize > 0 && length != expectedSize)) {
                state(context, "download_size_mismatch", "$length/$expectedSize", true)
                return
            }
            try {
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_APK_BYTES) {
                                target.delete()
                                state(context, "download_too_large", total.toString(), true)
                                return
                            }
                            out.write(buffer, 0, n)
                        }
                    }
                }
            } catch (e: Exception) {
                target.delete()
                state(context, "download_write_failed", e.message.orEmpty(), true)
                return
            }
        }

        prefs(context).edit().putLong("last_update_download_at", System.currentTimeMillis()).apply()

        if (target.length() != expectedSize) {
            target.delete()
            state(context, "verify_size_failed", "${target.length()}/$expectedSize", true)
            return
        }
        if (sha256(target) != expectedSha) {
            target.delete()
            state(context, "verify_sha_failed", "", true)
            return
        }
        if (!verifyApkIdentity(context, target, expectedVersion)) {
            target.delete()
            state(context, "verify_identity_failed", "v=$expectedVersion", true)
            return
        }

        prefs(context).edit().putLong("last_update_verified_at", System.currentTimeMillis()).apply()
        state(context, "verified", "v=$expectedVersion")

        val exported = exportVerifiedUpdate(context, target, expectedVersion)
        if (!exported) {
            state(context, "export_failed", "v=$expectedVersion", true)
            return
        }

        target.delete()
        prefs(context).edit()
            .putLong("last_update_exported_at", System.currentTimeMillis())
            .putLong("last_update_exported_version", expectedVersion)
            .apply()
        state(context, "ready_in_downloads", "v=$expectedVersion")
        notifyVerifiedUpdate(context, expectedVersion)
    }

    fun startRealtimeListener(context: Context) {
        val app = context.applicationContext
        if (updateSocket != null || realtimeStarting) return
        realtimeStarting = true
        val req = Request.Builder()
            .url("wss://ntfy.sh/$UPDATE_TOPIC/ws")
            .header("User-Agent", "HAKIM-SafeUpdate-Realtime/2")
            .build()
        updateSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                realtimeStarting = false
                state(app, "realtime_connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { JSONObject(text) } catch (_: Exception) { return }
                if (event.optString("event") != "message") return
                if (event.optString("title") != UPDATE_TITLE) return
                prefs(app).edit().putLong("last_update_push_at", System.currentTimeMillis()).apply()
                state(app, "push_received")
                checkAsync(app)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                updateSocket = null
                realtimeStarting = false
                state(app, "realtime_disconnected", t.message.orEmpty(), false)
                mainHandler.postDelayed({ startRealtimeListener(app) }, 10_000L)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                updateSocket = null
                realtimeStarting = false
                state(app, "realtime_closed", "$code $reason", false)
                mainHandler.postDelayed({ startRealtimeListener(app) }, 10_000L)
            }
        })
    }

    fun stopRealtimeListener() {
        updateSocket?.cancel()
        updateSocket = null
        realtimeStarting = false
    }

    fun diagnostics(context: Context): JSONObject {
        val p = prefs(context)
        return JSONObject()
            .put("current_version", currentVersionCode(context))
            .put("installer_capability", false)
            .put("state", p.getString("last_update_state", "unknown"))
            .put("detail", p.getString("last_update_detail", ""))
            .put("error", p.getString("last_update_error", ""))
            .put("last_check_at", p.getLong("last_update_check_at", 0L))
            .put("last_push_at", p.getLong("last_update_push_at", 0L))
            .put("last_discovered_version", p.getLong("last_update_discovered_version", -1L))
            .put("last_verified_at", p.getLong("last_update_verified_at", 0L))
            .put("last_exported_at", p.getLong("last_update_exported_at", 0L))
            .put("last_exported_version", p.getLong("last_update_exported_version", -1L))
    }

    fun statusSummary(context: Context): String {
        val d = diagnostics(context)
        val version = d.optLong("current_version", 0L)
        val state = when (d.optString("state", "unknown")) {
            "checking" -> "يجري فحص التحديث"
            "up_to_date" -> "أنت على أحدث إصدار معروف"
            "downloading" -> "ينزّل تحديثًا جديدًا للتحقق منه"
            "verified" -> "تم التحقق من التحديث"
            "ready_in_downloads" -> "تحديث موثّق جاهز في التنزيلات"
            "update_found" -> "وُجد تحديث جديد"
            else -> d.optString("state", "unknown")
        }
        return "التحديث الآمن — الإصدار $version\nالحالة: $state"
    }

    private fun exportVerifiedUpdate(context: Context, apk: File, expectedVersion: Long): Boolean {
        val name = "HAKIM-$expectedVersion-D1-VERIFIED.apk"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hakim")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                val copied = runCatching {
                    resolver.openOutputStream(uri, "w")?.use { out ->
                        apk.inputStream().use { input -> input.copyTo(out) }
                    } ?: return@runCatching false
                    true
                }.getOrElse {
                    runCatching { resolver.delete(uri, null, null) }
                    false
                }
                if (!copied) return false
                val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, ready, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) return false
                apk.copyTo(File(dir, name), overwrite = true)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyVerifiedUpdate(context: Context, expectedVersion: Long) {
        createUpdateChannel(context)
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context,
            4410,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث حكيم $expectedVersion موثّق وجاهز")
            .setContentText("افتح التنزيلات ثم اضغط ملف HAKIM-$expectedVersion-D1-VERIFIED.apk لتثبيته عبر أندرويد.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(4410, notification)
    }

    private fun verifyApkIdentity(context: Context, apk: File, expectedVersion: Long): Boolean {
        val pm = context.packageManager
        val archive = packageArchive(pm, apk.absolutePath) ?: return false
        if (archive.packageName != context.packageName) return false
        if (versionCode(archive) != expectedVersion) return false
        if (expectedVersion <= currentVersionCode(context)) return false
        val current = installedPackage(pm, context.packageName) ?: return false
        val archiveCerts = certDigests(archive)
        val currentCerts = certDigests(current)
        val expectedCerts = setOf(FIELD_CERT_SHA256)
        return archiveCerts == expectedCerts && currentCerts == expectedCerts
    }

    @Suppress("DEPRECATION")
    private fun packageArchive(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun installedPackage(pm: PackageManager, name: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(
                name,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun certDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(context: Context): Long = try {
        versionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (_: Exception) {
        0L
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تحديثات حكيم",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context, value: String, detail: String = "", error: Boolean = false) {
        val e = prefs(context).edit()
            .putString("last_update_state", value)
            .putString("last_update_detail", detail.take(300))
            .putLong("last_update_state_at", System.currentTimeMillis())
        if (error) {
            e.putString("last_update_error", "$value ${detail.take(240)}")
        } else if (value in setOf("up_to_date", "verified", "ready_in_downloads")) {
            e.remove("last_update_error")
        }
        e.apply()
    }
}
