package ps.hakim.phoneagent

import android.content.Context
import android.util.Base64
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct OpenRouter free-model engine.
 *
 * Uses only model "openrouter/free" by default so Hakim does not silently spend
 * credits. OpenRouter routes among currently available free models and streams
 * the final answer back into Hakim.
 */
class OpenRouterDirectEngine(private val context: Context) : HakimInferenceEngine {
    override val id: String = "openrouter-free-direct"
    override val displayName: String = "OpenRouter المجاني"
    override val capabilities: Set<HakimInferenceEngine.Capability> = setOf(
        HakimInferenceEngine.Capability.GENERAL_CHAT,
        HakimInferenceEngine.Capability.IMAGES
    )

    @Volatile
    private var activeCall: Call? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
        .build()

    override fun complete(
        instruction: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        onDelta: (String) -> Unit
    ): HakimInferenceEngine.Result {
        val key = HakimSecretStore.get(context, OpenRouterPkceAuth.SECRET_OPENROUTER_KEY)
            ?.trim().orEmpty()
        if (key.isBlank()) {
            return HakimInferenceEngine.Result.NeedsAuthorization(
                "يلزم ربط OpenRouter المجاني مرة واحدة من «إدارة»."
            )
        }

        if (attachments.any { !it.mimeType.startsWith("image/") }) {
            return HakimInferenceEngine.Result.Unavailable(
                "المسار المجاني المباشر الحالي يدعم النص والصور؛ هذا النوع يحتاج أداة أخرى."
            )
        }

        val messages = loadHistory()
        val current = JSONObject().put("role", "user")
        if (attachments.isEmpty()) {
            current.put("content", instruction)
        } else {
            val parts = JSONArray()
            parts.put(JSONObject().put("type", "text").put("text", instruction))
            for (a in attachments) {
                val bytes = readBounded(a, MAX_IMAGE_BYTES)
                    ?: return HakimInferenceEngine.Result.Unavailable(
                        "تعذر قراءة الصورة أو تجاوزت الحد الآمن: " + a.displayName
                    )
                val dataUrl = "data:" + a.mimeType + ";base64," +
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", dataUrl))
                )
            }
            current.put("content", parts)
        }
        messages.put(current)

        val body = JSONObject()
            .put("model", DEFAULT_MODEL)
            .put("messages", messages)
            .put("stream", true)

        val request = Request.Builder()
            .url(CHAT_URL)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .header("X-Title", "Hakim Android")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return try {
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    response.body?.close()
                    return@use when (code) {
                        401, 403 -> HakimInferenceEngine.Result.NeedsAuthorization(
                            "رفض OpenRouter مفتاح الربط؛ أعد الربط من «إدارة»."
                        )
                        402 -> HakimInferenceEngine.Result.Failure(
                            "المسار المجاني لم يُقبل لهذه المهمة؛ لم يُسمح لحكيم بالتحول إلى نموذج مدفوع.",
                            retryable = false
                        )
                        429 -> HakimInferenceEngine.Result.Failure(
                            "بلغت حصة OpenRouter المجانية الحالية. سيحتاج حكيم محركًا مجانيًا بديلًا أو انتظار تجدد الحصة.",
                            retryable = true
                        )
                        else -> HakimInferenceEngine.Result.Failure(
                            "فشل OpenRouter برمز HTTP " + code,
                            retryable = code >= 500
                        )
                    }
                }

                val source = response.body?.source()
                    ?: return@use HakimInferenceEngine.Result.Failure("استجابة OpenRouter فارغة.", true)
                val full = StringBuilder()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") continue
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    val choices = json.optJSONArray("choices") ?: continue
                    val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    val content = delta.optString("content")
                    if (content.isNotEmpty()) {
                        full.append(content)
                        onDelta(content)
                    }
                }

                val text = full.toString().trim()
                if (text.isBlank()) {
                    HakimInferenceEngine.Result.Failure(
                        "اكتملت القناة المجانية دون جواب نصي قابل للعرض.",
                        retryable = true
                    )
                } else {
                    saveTurn(instruction, text)
                    HakimInferenceEngine.Result.Success(
                        text = text,
                        evidence = listOf(
                            "engine=openrouter-free-direct",
                            "model=openrouter/free",
                            "returned_in_app=true",
                            "paid_fallback=false"
                        )
                    )
                }
            }
        } catch (_: java.io.InterruptedIOException) {
            HakimInferenceEngine.Result.Failure("أُلغي الطلب أو انتهت مهلته.", true)
        } catch (_: Exception) {
            HakimInferenceEngine.Result.Failure("تعذر الاتصال بـOpenRouter المجاني.", true)
        } finally {
            activeCall = null
        }
    }

    override fun cancel() {
        activeCall?.cancel()
    }

    fun clearConversation() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistory(): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]").orEmpty()
        val all = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val kept = JSONArray()
        val start = (all.length() - MAX_HISTORY_MESSAGES).coerceAtLeast(0)
        for (i in start until all.length()) kept.put(all.optJSONObject(i))
        return kept
    }

    private fun saveTurn(user: String, assistant: String) {
        val history = loadHistory()
        history.put(JSONObject().put("role", "user").put("content", user.take(6000)))
        history.put(JSONObject().put("role", "assistant").put("content", assistant.take(12000)))
        val kept = JSONArray()
        val start = (history.length() - MAX_HISTORY_MESSAGES).coerceAtLeast(0)
        for (i in start until history.length()) kept.put(history.optJSONObject(i))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, kept.toString()).apply()
    }

    private fun readBounded(
        attachment: HakimAttachmentGateway.Attachment,
        limit: Long
    ): ByteArray? = runCatching {
        context.contentResolver.openInputStream(attachment.uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n.toLong()
                if (total > limit) return@use null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

    companion object {
        const val PREFS = "hakim_openrouter_direct"
        private const val KEY_HISTORY = "openrouter_history"
        const val DEFAULT_MODEL = "openrouter/free"
        private const val CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val MAX_HISTORY_MESSAGES = 10
        private const val MAX_IMAGE_BYTES = 6L * 1024L * 1024L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
