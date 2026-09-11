package ps.hakim.phoneagent

import android.app.JobInfo
import android.app.JobScheduler
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private const val PERIOD_MS = 2L * 60L * 60L * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            scheduler.schedule(job)
        } catch (_: Exception) {}
    }

    fun checkAsync(context: Context) {
        Thread {
            try { checkNow(context.applicationContext) } catch (_: Exception) {}
        }.start()
    }

    fun checkNow(context: Context) {
        createUpdateChannel(context)
        val currentVersion = currentVersionCode(context)
        val request = Request.Builder()
            .url("https://ntfy.sh/$UPDATE_TOPIC/json?poll=1&since=3h")
            .header("User-Agent", "HAKIM-AutoUpdater/1")
            .build()
        val response = client.newCall(request).execute()
        response.use { r ->
            if (!r.isSuccessful) return
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
            val update = chosen ?: return
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

        val req = Request.Builder().url(url).header("User-Agent", "HAKIM-AutoUpdater/1").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return
            val body = r.body ?: return
            val length = body.contentLength()
            if (length > MAX_APK_BYTES || (length > 0 && expectedSize > 0 && length != expectedSize)) return
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
                            return
                        }
                        out.write(buffer, 0, n)
                    }
                }
            }
        }

        if (target.length() != expectedSize) { target.delete(); return }
        if (sha256(target) != expectedSha) { target.delete(); return }
        if (!verifyApkIdentity(context, target, expectedVersion)) { target.delete(); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            notifyInstallPermission(context)
            return
        }
        stageInstall(context, target)
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

    private fun stageInstall(context: Context, apk: File) {
        try {
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
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
                .edit().putString("last_update_error", e.message.orEmpty().take(300)).apply()
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
            .setContentTitle("تفعيل التحديث التلقائي لحكيم")
            .setContentText("اضغط مرة واحدة واسمح لحكيم بتثبيت تحديثاته")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(4402, notification)
    }

    fun notifyConfirmation(context: Context, confirmIntent: Intent) {
        createUpdateChannel(context)
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, 4403, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث حكيم جاهز")
            .setContentText("أندرويد يطلب تأكيد التثبيت")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(4403, notification)
    }

    private fun createUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تحديثات حكيم", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
