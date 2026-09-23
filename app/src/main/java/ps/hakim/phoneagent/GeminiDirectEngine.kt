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
 * Official Gemini Developer API direct engine.
 * Replies stream back into Hakim instead of opening another app.
 * The API key is read from HakimSecretStore and never logged.
 */
class GeminiDirectEngine(private val context: Context) : HakimInferenceEngine {
    override val id: String = "gemini-direct"
    override val displayName: String = "Gemini مباشر"
    override val capabilities: Set<HakimInferenceEngine.Capability> = setOf(
        HakimInferenceEngine.Capability.GENERAL_CHAT,
        HakimInferenceEngine.Capability.IMAGES,
        HakimInferenceEngine.Capability.FILES,
        HakimInferenceEngine.Capability.AUDIO,
        HakimInferenceEngine.Capability.VIDEO
    )

    @Volatile
    private var activeCall: Call? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
        .build()

    override fun complete(
        instruction: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        onDelta: (String) -> Unit
    ): HakimInferenceEngine.Result {
        val key = HakimSecretStore.get(context, SECRET_GEMINI_KEY)?.trim().orEmpty()
        if (key.isBlank()) {
            return HakimInferenceEngine.Result.NeedsAuthorization(
                "يلزم مفتاح Gemini API رسمي لإرجاع الإجابة داخل حكيم."
            )
        }

        val model = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, DEFAULT_MODEL)
            .orEmpty()
            .ifBlank { DEFAULT_MODEL }

        val input = JSONArray()
        input.put(JSONObject().put("type", "text").put("text", instruction))

        var totalInline = 0L
        for (attachment in attachments) {
            val bytes = readAttachment(attachment)
                ?: return HakimInferenceEngine.Result.Unavailable(
                    "تعذر قراءة المرفق: " + attachment.displayName
                )
            totalInline += bytes.size.toLong()
            if (bytes.size.toLong() > MAX_SINGLE_INLINE || totalInline > MAX_TOTAL_INLINE) {
                return HakimInferenceEngine.Result.Unavailable(
                    "المرفق كبير للمسار المباشر الحالي؛ يلزم مسار رفع ملفات أكبر قبل الإرسال."
                )
            }
            input.put(
                JSONObject()
                    .put("type", interactionType(attachment.mimeType))
                    .put("mime_type", attachment.mimeType)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("input", input)
            .put("stream", true)

        previousInteractionId()?.takeIf { attachments.isEmpty() }?.let {
            body.put("previous_interaction_id", it)
        }

        val request = Request.Builder()
            .url(INTERACTIONS_URL)
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
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
                            "رفض Gemini بيانات الاعتماد أو الصلاحية."
                        )
                        429 -> HakimInferenceEngine.Result.Failure(
                            "بلغ Gemini حد الاستخدام الحالي. يمكن لحكيم تجربة محرك آخر عند توفره.",
                            retryable = true
                        )
                        else -> HakimInferenceEngine.Result.Failure(
                            "فشل Gemini برمز HTTP " + code,
                            retryable = code >= 500
                        )
                    }
                }

                val source = response.body?.source()
                    ?: return@use HakimInferenceEngine.Result.Failure("استجابة Gemini فارغة.", true)

                val full = StringBuilder()
                var interactionId: String? = null

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") continue

                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    when (json.optString("event_type")) {
                        "interaction.created", "interaction.completed" -> {
                            val interaction = json.optJSONObject("interaction")
                            val value = interaction?.optString("id").orEmpty()
                            if (value.isNotBlank()) interactionId = value
                        }
                        "step.delta" -> {
                            val delta = json.optJSONObject("delta") ?: continue
                            if (delta.optString("type") == "text") {
                                val text = delta.optString("text")
                                if (text.isNotEmpty()) {
                                    full.append(text)
                                    onDelta(text)
                                }
                            }
                        }
                    }
                }

                interactionId?.let { savePreviousInteractionId(it) }
                val text = full.toString().trim()
                if (text.isBlank()) {
                    HakimInferenceEngine.Result.Failure(
                        "اكتملت القناة المباشرة دون نص نهائي قابل للعرض.",
                        retryable = true
                    )
                } else {
                    HakimInferenceEngine.Result.Success(
                        text = text,
                        evidence = listOf(
                            "engine=gemini-direct",
                            "model=" + model,
                            "returned_in_app=true"
                        )
                    )
                }
            }
        } catch (_: java.io.InterruptedIOException) {
            HakimInferenceEngine.Result.Failure("أُلغي الطلب أو انتهت مهلته.", retryable = true)
        } catch (_: Exception) {
            HakimInferenceEngine.Result.Failure("تعذر الاتصال بمحرك Gemini المباشر.", retryable = true)
        } finally {
            activeCall = null
        }
    }

    override fun cancel() {
        activeCall?.cancel()
    }

    fun clearConversation() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PREVIOUS_INTERACTION).apply()
    }

    private fun readAttachment(attachment: HakimAttachmentGateway.Attachment): ByteArray? =
        runCatching {
            context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
        }.getOrNull()

    private fun interactionType(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("audio/") -> "audio"
        mime.startsWith("video/") -> "video"
        else -> "document"
    }

    private fun previousInteractionId(): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREVIOUS_INTERACTION, null)

    private fun savePreviousInteractionId(id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREVIOUS_INTERACTION, id).apply()
    }

    companion object {
        const val SECRET_GEMINI_KEY = "gemini_api_key"
        const val PREFS = "hakim_direct_models"
        const val KEY_MODEL = "gemini_model"
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        private const val KEY_PREVIOUS_INTERACTION = "gemini_previous_interaction_id"
        private const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val MAX_SINGLE_INLINE = 8L * 1024L * 1024L
        private const val MAX_TOTAL_INLINE = 16L * 1024L * 1024L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
