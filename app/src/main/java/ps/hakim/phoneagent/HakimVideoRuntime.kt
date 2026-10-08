package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * منفذ الرندر المحايد R31.
 *
 * لا يحمل نموذجًا ولا مزودًا داخل التطبيق. يتحدث مع runtime يملكه المستخدم
 * ببروتوكول ثابت، ويحفظ رمز الوصول في AndroidKeyStore. HTTP مسموح للـloopback
 * فقط؛ أي مضيف آخر يحتاج HTTPS.
 */
object HakimVideoRuntime {
    const val VERSION = "VIDEO-RUNTIME-2026-10-08-r31"
    const val PROTOCOL = "hakim-video-runtime-v1"
    private const val PREFS = "hakim_video_runtime"
    private const val SECRET_TOKEN = "hakim-video-runtime-token-v1"

    fun configure(context: Context, endpoint: String, executorName: String, token: String?): JSONObject {
        val normalized = normalizeEndpoint(endpoint)
            ?: return error("invalid_or_insecure_endpoint")
        val name = executorName.trim().take(120).ifBlank { "self-hosted-video-runtime" }
        if (!token.isNullOrBlank()) HakimSecretStore.put(context, SECRET_TOKEN, token.trim())
        else HakimSecretStore.remove(context, SECRET_TOKEN)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", normalized)
            .putString("executor_name", name)
            .putLong("configured_at", System.currentTimeMillis())
            .apply()
        return status(context)
    }

    fun clear(context: Context) {
        HakimSecretStore.remove(context, SECRET_TOKEN)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endpoint = p.getString("endpoint", "").orEmpty()
        return JSONObject()
            .put("ok", true)
            .put("video_runtime", true)
            .put("version", VERSION)
            .put("protocol", PROTOCOL)
            .put("configured", endpoint.isNotBlank())
            .put("executor_name", p.getString("executor_name", "").orEmpty())
            .put("endpoint_sha256", if (endpoint.isBlank()) "" else sha256(endpoint))
            .put("token_present", HakimSecretStore.has(context, SECRET_TOKEN))
            .put("arbitrary_shell_exposed", false)
            .put("model_download_automatic", false)
            .put("paid_execution_automatic", false)
            .put("last_probe_ok", p.getBoolean("last_probe_ok", false))
            .put("last_probe_at", p.getLong("last_probe_at", 0L))
            .put("last_protocol", p.getString("last_protocol", "").orEmpty())
            .put("last_cost_class", p.getString("last_cost_class", "").orEmpty())
    }

    fun probe(context: Context): JSONObject {
        val base = endpoint(context) ?: return error("runtime_not_configured")
        val result = request(context, "GET", base + "/hakim/video/v1/capabilities", null)
        val valid = result.optBoolean("ok", false) &&
            result.optString("protocol") == PROTOCOL &&
            result.optBoolean("available", false)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("last_probe_ok", valid)
            .putLong("last_probe_at", System.currentTimeMillis())
            .putString("last_protocol", result.optString("protocol").take(80))
            .putString("last_cost_class", result.optString("cost_class").take(80))
            .apply()
        return result.put("verified_runtime", valid)
    }

    /**
     * التنفيذ المتغير لا يمر تلقائيًا: يحتاج قرار نواة send_external.
     * وجود runtime أو نجاح probe لا يعني تفويض الرندر.
     */
    fun submit(context: Context, plan: JSONObject, idempotencyKey: String = UUID.randomUUID().toString()): JSONObject {
        val base = endpoint(context) ?: return error("runtime_not_configured")
        val caps = probe(context)
        if (!caps.optBoolean("verified_runtime", false)) return error("runtime_probe_failed")

        val costClass = caps.optString("cost_class", "unknown").lowercase()
        if (costClass == "paid") return error("paid_runtime_requires_explicit_external_approval")

        val auth = HakimCapabilityKernel.authorize(
            context,
            "send_external",
            base,
            "video_render_job"
        )
        if (auth.optString("verdict") != "allow") {
            return JSONObject()
                .put("ok", false)
                .put("error", "runtime_submission_requires_explicit_gate")
                .put("authorization", auth)
        }

        val payload = JSONObject()
            .put("protocol", PROTOCOL)
            .put("idempotency_key", idempotencyKey.take(128))
            .put("plan", plan)
        return request(context, "POST", base + "/hakim/video/v1/jobs", payload)
    }

    fun job(context: Context, jobId: String): JSONObject {
        val base = endpoint(context) ?: return error("runtime_not_configured")
        if (!Regex("^[A-Za-z0-9._:-]{8,128}$").matches(jobId)) return error("invalid_job_id")
        return request(context, "GET", base + "/hakim/video/v1/jobs/" + jobId, null)
    }

    private fun request(context: Context, method: String, target: String, body: JSONObject?): JSONObject {
        val conn = runCatching { URL(target).openConnection() as HttpURLConnection }
            .getOrElse { return error("runtime_connection_open_failed") }
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json")
            HakimSecretStore.get(context, SECRET_TOKEN)?.takeIf { it.isNotBlank() }?.let {
                conn.setRequestProperty("Authorization", "Bearer " + it)
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse {
                JSONObject().put("ok", false).put("error", "runtime_non_json_response")
            }
            parsed.put("http_status", code)
            if (code !in 200..299) parsed.put("ok", false)
            parsed
        } catch (_: Exception) {
            error("runtime_request_failed")
        } finally {
            conn.disconnect()
        }
    }

    private fun endpoint(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("endpoint", null)
            ?.let { normalizeEndpoint(it) }

    private fun normalizeEndpoint(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase() ?: return@runCatching null
        val host = uri.host?.lowercase() ?: return@runCatching null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return@runCatching null
        val loopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
        if (scheme != "https" && !(scheme == "http" && loopback)) return@runCatching null
        val port = if (uri.port > 0) ":" + uri.port else ""
        val path = uri.path?.trimEnd('/').orEmpty()
        scheme + "://" + host + port + path
    }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun error(code: String): JSONObject = JSONObject()
        .put("ok", false)
        .put("video_runtime", true)
        .put("version", VERSION)
        .put("protocol", PROTOCOL)
        .put("error", code)
}
