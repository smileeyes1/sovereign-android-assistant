package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * جسر الاستدلال المحلي المتقدم.
 *
 * لا يقبل إلا loopback داخل الهاتف، ولا يقرأ مفتاح API ولا يضيف Authorization.
 * الغرض: جعل نموذج محلي (مثل llama.cpp server أو أي واجهة OpenAI-compatible محلية)
 * مزودًا أصيلًا يمكن استخدامه بلا شبكة وبلا شركة خارجية.
 */
object HakimLocalReasoningBridge {
    const val VERSION = "HAKIM-LOCAL-REASONING-BRIDGE-2026-09-18-v1"
    private const val PREFS = "hakim_local_reasoning"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"
    private const val KEY_LAST_READY_AT = "last_ready_at"
    private const val KEY_LAST_LATENCY = "last_latency_ms"
    private const val KEY_LAST_ERROR = "last_error"
    private const val READY_TTL_MS = 5L * 60L * 1000L
    private const val MAX_PROMPT_CHARS = 24000
    private const val MAX_RESPONSE_CHARS = 48000

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val defaultCandidates = listOf(
        "http://127.0.0.1:8080/v1/chat/completions",
        "http://localhost:8080/v1/chat/completions"
    )

    data class Completion(
        val ok: Boolean,
        val text: String,
        val endpoint: String,
        val model: String,
        val latencyMs: Long,
        val error: String = ""
    )

    fun configure(context: Context, endpoint: String, model: String = "local-model"): Boolean {
        val clean = endpoint.trim()
        if (clean.isBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_ENDPOINT)
                .remove(KEY_MODEL)
                .remove(KEY_LAST_READY_AT)
                .apply()
            return true
        }
        if (!isLoopbackEndpoint(clean)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENDPOINT, clean)
            .putString(KEY_MODEL, model.trim().ifBlank { "local-model" }.take(160))
            .apply()
        return true
    }

    fun endpoint(context: Context): String? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, null)
            ?.trim()
            .orEmpty()
        return raw.takeIf(::isLoopbackEndpoint)
    }

    fun model(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, "local-model")
            .orEmpty()
            .ifBlank { "local-model" }
            .take(160)

    /**
     * فحص loopback فقط. أي استجابة HTTP تعني أن خدمة محلية موجودة، حتى إن رفضت HEAD.
     */
    fun probe(context: Context): Boolean {
        val configured = endpoint(context)
        val candidates = buildList {
            configured?.let { add(it) }
            defaultCandidates.forEach { if (it != configured) add(it) }
        }
        for (candidate in candidates) {
            if (!isLoopbackEndpoint(candidate)) continue
            val started = System.currentTimeMillis()
            val req = Request.Builder().url(candidate).head().build()
            try {
                client.newCall(req).execute().use {
                    val latency = System.currentTimeMillis() - started
                    rememberReady(context, candidate, latency)
                    return true
                }
            } catch (_: Exception) {
                // جرّب loopback التالي فقط؛ لا تنتقل إلى عنوان خارجي.
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, "LOCAL_MODEL_NOT_REACHABLE")
            .apply()
        return false
    }

    fun readyNow(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = System.currentTimeMillis() - p.getLong(KEY_LAST_READY_AT, 0L) <= READY_TTL_MS
        return endpoint(context) != null && recent
    }

    /**
     * واجهة OpenAI-compatible محلية فقط، مع temperature=0 لتقليل التباين.
     * لا يُدّعى أن خرج النموذج حتمي؛ الحتمية للحاكمية والاختيار والتحقق، لا لاحتمالات النموذج.
     */
    fun complete(context: Context, systemPrompt: String, userPrompt: String): Completion {
        val ep = endpoint(context) ?: return Completion(
            false, "", "", model(context), 0L, "LOCAL_ENDPOINT_NOT_CONFIGURED"
        )
        if (!isLoopbackEndpoint(ep)) return Completion(
            false, "", ep, model(context), 0L, "NON_LOOPBACK_ENDPOINT_REJECTED"
        )

        val m = model(context)
        val body = JSONObject()
            .put("model", m)
            .put("temperature", 0)
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt.take(MAX_PROMPT_CHARS)))
                    .put(JSONObject().put("role", "user").put("content", userPrompt.take(MAX_PROMPT_CHARS)))
            )
            .toString()

        val started = System.currentTimeMillis()
        val req = Request.Builder()
            .url(ep)
            .header("User-Agent", "HAKIM-Local-Reasoning/1")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = "HTTP_" + response.code
                    rememberFailure(context, err)
                    Completion(false, "", ep, m, latency, err)
                } else {
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    val text = json
                        ?.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                        .take(MAX_RESPONSE_CHARS)
                    if (text.isBlank()) {
                        rememberFailure(context, "EMPTY_OR_UNSUPPORTED_RESPONSE")
                        Completion(false, "", ep, m, latency, "EMPTY_OR_UNSUPPORTED_RESPONSE")
                    } else {
                        rememberReady(context, ep, latency)
                        Completion(true, text, ep, m, latency)
                    }
                }
            }
        } catch (e: Exception) {
            val error = e.javaClass.simpleName.take(80)
            rememberFailure(context, error)
            Completion(false, "", ep, m, System.currentTimeMillis() - started, error)
        }
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ep = endpoint(context)
        return JSONObject()
            .put("version", VERSION)
            .put("local_advanced_reasoning", true)
            .put("loopback_only", true)
            .put("external_network_forbidden", true)
            .put("api_key_required", false)
            .put("authorization_header_used", false)
            .put("openai_compatible_local_protocol", true)
            .put("configured", ep != null)
            .put("endpoint_host", ep?.let { Uri.parse(it).host }.orEmpty())
            .put("model", model(context))
            .put("ready_now", readyNow(context))
            .put("last_ready_at", p.getLong(KEY_LAST_READY_AT, 0L))
            .put("last_latency_ms", p.getLong(KEY_LAST_LATENCY, 0L))
            .put("last_error", p.getString(KEY_LAST_ERROR, ""))
            .put("temperature_zero", true)
            .put("model_output_probabilistic", true)
            .put("deterministic_governance_outside_model", true)
    }

    fun isLoopbackEndpoint(raw: String): Boolean = runCatching {
        val uri = Uri.parse(raw)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val port = uri.port
        (scheme == "http" || scheme == "https") &&
            (host == "127.0.0.1" || host == "localhost" || host == "::1") &&
            (port == -1 || port in 1024..65535) &&
            raw.length <= 1000
    }.getOrDefault(false)

    private fun rememberReady(context: Context, endpoint: String, latency: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENDPOINT, endpoint)
            .putLong(KEY_LAST_READY_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_LATENCY, latency.coerceAtLeast(0L))
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun rememberFailure(context: Context, error: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, error.take(160))
            .apply()
    }
}
