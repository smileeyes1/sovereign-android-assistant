package ps.hakim.phoneagent

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * جسر Android إلى بيئة حكيم السيادية المستقلة في userland (Termux).
 *
 * البيئة هنا مخزن/طابور/سجل محلي دائم، وليست مرجعًا شرعيًا ولا حاكم قرار.
 * النواة السيادية الواحدة تبقى الحاكم، وكل اتصال محصور في loopback.
 */
object HakimSovereignEnvironmentBridge {
    const val VERSION = "HAKIM-SOVEREIGN-ENV-BRIDGE-20050-v1"
    private const val PREFS = "hakim_sovereign_environment"
    private const val KEY_TOKEN = "token"
    private const val KEY_LAST_READY_AT = "last_ready_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val ENDPOINT = "http://127.0.0.1:8765"
    private const val MAX_INTENT = 24000

    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun configureToken(context: Context, token: String): Boolean {
        val clean = token.trim()
        if (clean.isBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_TOKEN).remove(KEY_LAST_READY_AT).apply()
            return true
        }
        if (clean.length !in 32..256 || clean.any { it.isWhitespace() }) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, clean).apply()
        return true
    }

    fun probe(context: Context): Boolean {
        val req = Request.Builder()
            .url("$ENDPOINT/health")
            .header("User-Agent", "HAKIM-Sovereign-Environment/1")
            .get().build()
        return try {
            client.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                val ok = response.isSuccessful &&
                    json?.optBoolean("ok") == true &&
                    json.optBoolean("loopback_only") &&
                    !json.optBoolean("external_provider_required", true)
                if (ok) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_LAST_READY_AT, System.currentTimeMillis())
                        .remove(KEY_LAST_ERROR).apply()
                } else rememberFailure(context, "INVALID_RUNTIME_HEALTH")
                ok
            }
        } catch (e: Exception) {
            rememberFailure(context, e.javaClass.simpleName)
            false
        }
    }

    fun queueIntent(
        context: Context,
        intent: String,
        params: JSONObject = JSONObject(),
        idempotencyKey: String = ""
    ): JSONObject {
        val token = token(context)
        if (token.isBlank()) return JSONObject()
            .put("ok", false).put("error", "LOCAL_ENV_NOT_PAIRED")
        val cleanIntent = intent.trim().take(MAX_INTENT)
        if (cleanIntent.isBlank()) return JSONObject()
            .put("ok", false).put("error", "EMPTY_INTENT")

        val body = JSONObject()
            .put("intent", cleanIntent)
            .put("params", params)
            .put("idempotency_key", idempotencyKey.take(240))
            .toString()
        val req = Request.Builder()
            .url("$ENDPOINT/v1/commands")
            .header("X-Hakim-Token", token)
            .header("User-Agent", "HAKIM-Sovereign-Environment/1")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrElse {
                    JSONObject().put("ok", false).put("error", "INVALID_RUNTIME_RESPONSE")
                }
                if (!response.isSuccessful) json.put("ok", false)
                json
            }
        } catch (e: Exception) {
            rememberFailure(context, e.javaClass.simpleName)
            JSONObject().put("ok", false).put("error", e.javaClass.simpleName.take(80))
        }
    }

    fun recordEvidence(context: Context, kind: String, source: String, value: String): Boolean {
        val token = token(context)
        if (token.isBlank()) return false
        val body = JSONObject()
            .put("kind", kind.take(80))
            .put("source", source.take(120))
            .put("value", value.take(24000))
            .toString()
        val req = Request.Builder()
            .url("$ENDPOINT/v1/events")
            .header("X-Hakim-Token", token)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", VERSION)
            .put("endpoint", ENDPOINT)
            .put("loopback_only", true)
            .put("token_configured", token(context).isNotBlank())
            .put("ready_now", probe(context))
            .put("last_ready_at", p.getLong(KEY_LAST_READY_AT, 0L))
            .put("last_error", p.getString(KEY_LAST_ERROR, "").orEmpty())
            .put("human_user_owns_state", true)
            .put("durable_substrate_not_normative_governor", true)
            .put("one_sovereign_kernel_remains_governor", true)
            .put("external_providers_are_optional_adapters", true)
            .put("runtime_network_required", false)
            .put("android_is_interface_not_owner", true)
    }

    private fun token(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "").orEmpty().trim()

    private fun rememberFailure(context: Context, error: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, error.take(120)).apply()
    }
}
