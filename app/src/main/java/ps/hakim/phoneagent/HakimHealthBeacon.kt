package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.charset.StandardCharsets
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
        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pairing_disabled_by_user", false)) return false
        val topic = prefs.getString("result_topic", "").orEmpty().trim()
        val key = prefs.getString("auth_key", "").orEmpty().trim()
        if (topic.isBlank() || key.isBlank()) {
            prefs.edit().putString("last_health_beacon_state", "missing_pairing_or_auth").apply()
            return false
        }

        val now = System.currentTimeMillis()
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) { 0L }

        val payload = JSONObject()
            .put("request_id", "health-$now")
            .put("status", "health")
            .put("time", now)
            .put("version_code", version)
            .put("service_running", HakimService.running)
            .put("service_connected", HakimService.connected)
            .put("recovery", HakimConnectionResilience.status(context))
            .put("constitution", HakimConstitution.VERSION)
            .put("reason", reason.take(80))
            .toString()

        val requestId = "health-$now"
        val wrapper = JSONObject()
            .put("request_id", requestId)
            .put("chunk", 1)
            .put("total", 1)
            .put("data", payload)
            .put("sig", hmacHex(key, "$requestId\n1\n1\n$payload"))

        val req = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .header("User-Agent", "HAKIM-Health-Beacon/1")
            .post(wrapper.toString().toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { response ->
                val ok = response.isSuccessful
                prefs.edit()
                    .putString("last_health_beacon_state", if (ok) "sent" else "http_${response.code}")
                    .putLong("last_health_beacon_at", now)
                    .apply()
                ok
            }
        } catch (e: Exception) {
            prefs.edit()
                .putString("last_health_beacon_state", "failed")
                .putString("last_health_beacon_error", e.message.orEmpty().take(300))
                .putLong("last_health_beacon_at", now)
                .apply()
            false
        }
    }

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
