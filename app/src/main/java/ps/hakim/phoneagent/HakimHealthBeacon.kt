package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HakimHealthBeacon {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun sendAsync(context: Context, reason: String) {
        val app = context.applicationContext
        Thread {
            try { sendNow(app, reason) } catch (_: Exception) {}
        }.start()
    }

    fun sendNow(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pairing_disabled_by_user", false)) return false

        val now = System.currentTimeMillis()
        var versionCode = 0L
        var versionName = ""
        try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
            versionName = info.versionName.orEmpty()
        } catch (_: Exception) {}
        val installedApkSha256 = apkSha256(app)

        val payload = JSONObject()
            .put("request_id", "health-$now")
            .put("status", "health")
            .put("time", now)
            .put("package", app.packageName)
            .put("version_code", versionCode)
            .put("version_name", versionName)
            .put("apk_sha256", installedApkSha256)
            .put("service_running", HakimService.running)
            .put("service_connected", HakimService.connected)
            .put("secure_relay_configured", HakimUnifiedRelay.isConfigured(app))
            .put("recovery", HakimConnectionResilience.status(app))
            .put("constitution", HakimConstitution.VERSION)
            .put("reason", reason.take(80))
            .toString()

        // HC1 result webhook is the primary health path. It is independent from the legacy result topic.
        val secureUrl = prefs.getString(HakimUnifiedRelay.KEY_RESULT_URL, "").orEmpty().trim()
        val secureKey = prefs.getString(HakimUnifiedRelay.KEY_RELAY_KEY, "").orEmpty().trim()
        if (secureUrl.startsWith("https://") && secureKey.isNotBlank()) {
            val requestId = "health-$now"
            val signature = hmacHex(secureKey, "$requestId\nhealth\n$payload")
            val wrapper = JSONObject()
                .put("request_id", requestId)
                .put("status", "health")
                .put("received_at_ms", now)
                .put("result", JSONObject(payload))
                .put("sig", signature)
            if (postJson(secureUrl, wrapper.toString())) {
                prefs.edit()
                    .putString("last_health_beacon_state", "sent")
                    .putString("last_health_beacon_transport", "secure_webhook")
                    .putLong("last_health_beacon_at", now)
                    .putString("installed_apk_sha256", installedApkSha256)
                    .remove("last_health_beacon_error")
                    .apply()
                return true
            }
            prefs.edit()
                .putString("last_health_beacon_state", "secure_failed_fallback")
                .putString("last_health_beacon_transport", "secure_webhook")
                .apply()
        }

        // Compatibility fallback only: older installations may still have the signed ntfy result channel.
        val topic = prefs.getString("result_topic", "").orEmpty().trim()
        val key = prefs.getString("auth_key", "").orEmpty().trim()
        if (topic.isBlank() || key.isBlank()) {
            prefs.edit()
                .putString("last_health_beacon_state", if (secureUrl.isBlank()) "missing_pairing_or_auth" else "secure_failed_no_legacy")
                .putString("last_health_beacon_transport", if (secureUrl.isBlank()) "none" else "secure_webhook")
                .putLong("last_health_beacon_at", now)
                .putString("installed_apk_sha256", installedApkSha256)
                .apply()
            return false
        }

        val requestId = "health-$now"
        val wrapper = JSONObject()
            .put("request_id", requestId)
            .put("chunk", 1)
            .put("total", 1)
            .put("data", payload)
            .put("sig", hmacHex(key, "$requestId\n1\n1\n$payload"))

        val req = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .header("User-Agent", "HAKIM-Health-Beacon/3")
            .post(wrapper.toString().toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { response ->
                val ok = response.isSuccessful
                prefs.edit()
                    .putString("last_health_beacon_state", if (ok) "sent" else "http_${response.code}")
                    .putString("last_health_beacon_transport", "legacy_ntfy")
                    .putLong("last_health_beacon_at", now)
                    .putString("installed_apk_sha256", installedApkSha256)
                    .apply()
                ok
            }
        } catch (e: Exception) {
            prefs.edit()
                .putString("last_health_beacon_state", "failed")
                .putString("last_health_beacon_transport", "legacy_ntfy")
                .putString("last_health_beacon_error", e.message.orEmpty().take(300))
                .putLong("last_health_beacon_at", now)
                .apply()
            false
        }
    }

    private fun postJson(url: String, body: String): Boolean {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "HAKIM-Health-Beacon/3")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun apkSha256(context: Context): String = try {
        val file = File(context.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    private fun hmacHex(keyText: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes(keyText), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun keyBytes(keyText: String): ByteArray {
        val hex = keyText.trim()
        return if (hex.length % 2 == 0 && hex.matches(Regex("^[0-9a-fA-F]+$"))) {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            hex.toByteArray(StandardCharsets.UTF_8)
        }
    }
}
