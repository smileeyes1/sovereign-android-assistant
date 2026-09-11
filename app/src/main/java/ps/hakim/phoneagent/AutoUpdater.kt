package ps.hakim.phoneagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AutoUpdater {
    private const val UPDATE_TOPIC = "hakim-stable-updates-4f9c92e6a1b74d8d8b372e9e69fdc2c1"
    private const val UPDATE_TITLE = "HAKIM_UPDATE"
    private const val CHANNEL_ID = "hakim_updates"
    private const val JOB_ID = 771204
    private const val MAX_APK_BYTES = 1_900_000L
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
            .header("User-Agent", "HAKIM-AutoUpdater/2")
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
            downloadVerifyAndInstall(context, update)
        }
    }

    private fun downloadVerifyAndInstall(context: Context, update: JSONObject) {
        val url = update.getString("url")
        val expectedSha = update.getString("sha256")
        val expectedVersion = update.getLong("version_code")
        val expectedSize = update.getLong("size")
        val target = File(context.cacheDir, "hakim-update.apk")
        if (target.exists()) target.delete()

        state(context, "downloading", "v=$expectedVersion")
        val req = Request.Builder().url(url).header("User-Agent", "HAKIM-AutoUpdater/2").build()
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
            target.delete(); state(context, "verify_size_failed", "${target.length()}/$expectedSize", true); return
        }
        if (sha256(target) != expectedSha) {
            target.delete(); state(context, "verify_sha_failed", "", true); return
        }
        if (!verifyApkIdentity(context, target, expectedVersion)) {
            target.delete(); state(context, "verify_identity_failed", "v=$expectedVersion", true); return
        }
        prefs(context).edit().putLong("last_update_verified_at", System.currentTimeMillis()).apply()
        state(context, "verified", "v=$expectedVersion")

        if (!canInstallPackages(context)) {
            state(context, "install_permission_required")
            notifyInstallPermission(context)
            return
        }
        stageInstall(context, target, expectedVersion)
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            prefs(context).edit().putLong("last_install_permission_opened_at", System.currentTimeMillis()).apply()
            state(context, "awaiting_install_permission")
        } catch (e: Exception) {
            state(context, "permission_settings_failed", e.message.orEmpty(), true)
        }
    }

    fun startRealtimeListener(context: Context) {
        val app = context.applicationContext
        if (updateSocket != null || realtimeStarting) return
        realtimeStarting = true
        val req = Request.Builder()
            .url("wss://ntfy.sh/$UPDATE_TOPIC/ws")
            .header("User-Agent", "HAKIM-Update-Realtime/1")
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
            .put("can_install_packages", canInstallPackages(context))
            .put("state", p.getString("last_update_state", "unknown"))
            .put("detail", p.getString("last_update_detail", ""))
            .put("error", p.getString("last_update_error", ""))
            .put("last_check_at", p.getLong("last_update_check_at", 0L))
            .put("last_push_at", p.getLong("last_update_push_at", 0L))
            .put("last_discovered_version", p.getLong("last_update_discovered_version", -1L))
            .put("last_verified_at", p.getLong("last_update_verified_at", 0L))
            .put("last_commit_at", p.getLong("last_update_commit_at", 0L))
            .put("last_success_at", p.getLong("last_update_success_at", 0L))
    }

    fun statusSummary(context: Context): String {
        val d = diagnostics(context)
        val permission = if (d.optBoolean("can_install_packages")) "إذن التثبيت: جاهز" else "إذن التثبيت: يحتاج تفعيل مرة واحدة"
        val state = d.optString("state", "unknown")
        val version = d.optLong("current_version", 0L)
        return "التحديث التلقائي — الإصدار $version\n$permission\nالحالة: $state"
    }

    private fun verifyApkIdentity(context: Context, apk: File, expectedVersion: Long): Boolean {
        val pm = context.packageManager
        val archive = packageArchive(pm, apk.absolutePath) ?: return false
        if (archive.packageName != context.packageName) return false
        if (versionCode(archive) != expectedVersion) return false
        if (expectedVersion <= currentVersionCode(context)) return false
        val current = installedPackage(pm, context.packageName) ?: return false
        val archiveCert = certDigest(archive) ?: return false
        val currentCert = certDigest(current) ?: return false
        return archiveCert == currentCert
    }

    @Suppress("DEPRECATION")
    private fun packageArchive(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun installedPackage(pm: PackageManager, name: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun certDigest(info: PackageInfo): String? {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(context: Context): Long = try {
        versionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (_: Exception) { 0L }

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

    private fun stageInstall(context: Context, apk: File, expectedVersion: Long) {
        try {
            state(context, "install_staging", "v=$expectedVersion")
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("hakim-update.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val resultIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_RESULT)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, 4401, resultIntent, flags)
                prefs(context).edit()
                    .putLong("last_update_commit_at", System.currentTimeMillis())
                    .putLong("last_update_commit_version", expectedVersion)
                    .apply()
                state(context, "install_committed", "session=$id v=$expectedVersion")
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            state(context, "install_exception", e.message.orEmpty(), true)
        }
    }

    private fun notifyInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        val pi = PendingIntent.getActivity(
            context, 4402, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("خطوة واحدة لتفعيل تحديث حكيم تلقائيًا")
            .setContentText("اضغط وفعّل السماح من هذا المصدر؛ بعدها يحاول حكيم تثبيت تحديثاته تلقائيًا")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(4402, notification)
    }

    fun notifyConfirmation(context: Context, confirmIntent: Intent) {
        createUpdateChannel(context)
        state(context, "pending_android_confirmation")
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, 4403, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث حكيم جاهز")
            .setContentText("أندرويد يطلب تأكيد التثبيت لهذه المرة")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(4403, notification)
    }

    fun recordInstallSuccess(context: Context) {
        prefs(context).edit()
            .putLong("last_update_success_at", System.currentTimeMillis())
            .remove("last_update_error")
            .apply()
        state(context, "installed")
    }

    fun recordInstallFailure(context: Context, status: Int, message: String) {
        state(context, "install_failed", "status=$status ${message.take(220)}", true)
    }

    private fun createUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تحديثات حكيم", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context, value: String, detail: String = "", error: Boolean = false) {
        val e = prefs(context).edit()
            .putString("last_update_state", value)
            .putString("last_update_detail", detail.take(300))
            .putLong("last_update_state_at", System.currentTimeMillis())
        if (error) e.putString("last_update_error", "$value ${detail.take(240)}")
        else if (value == "installed" || value == "up_to_date" || value == "verified") e.remove("last_update_error")
        e.apply()
    }
}
