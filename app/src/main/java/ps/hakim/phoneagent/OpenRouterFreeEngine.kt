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
 * Zero-token-price OpenRouter free-model router.
 * Supports text and image input; output streams back into Hakim.
 */
class OpenRouterFreeEngine(private val context: Context) : HakimInferenceEngine {
    override val id: String = ID
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
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
        .build()

    override fun complete(
        instruction: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        onDelta: (String) -> Unit
    ): HakimInferenceEngine.Result {
        val key = HakimSecretStore.get(context, SECRET_OPENROUTER_KEY)?.trim().orEmpty()
        if (key.isBlank()) {
            return HakimInferenceEngine.Result.NeedsAuthorization(
                "يلزم مفتاح OpenRouter مجاني لإرجاع الإجابة داخل حكيم."
            )
        }

        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", instruction))

        for (attachment in attachments) {
            if (!attachment.mimeType.startsWith("image/")) {
                return HakimInferenceEngine.Result.Unavailable(
                    "المحرك المجاني الحالي يدعم النص والصور فقط لهذا المسار."
                )
            }
            if ((attachment.sizeBytes ?: 0L) > MAX_IMAGE_INLINE) {
                return HakimInferenceEngine.Result.Unavailable(
                    "الصورة أكبر من الحد الآمن للمسار المجاني المباشر."
                )
            }
            val bytes = readAttachmentBounded(attachment, MAX_IMAGE_INLINE)
                ?: return HakimInferenceEngine.Result.Unavailable(
                    "تعذر قراءة الصورة أو تجاوزت الحد الآمن: " + attachment.displayName
                )
            if (bytes.size.toLong() > MAX_IMAGE_INLINE) {
                return HakimInferenceEngine.Result.Unavailable(
                    "الصورة أكبر من الحد الآمن للمسار المجاني المباشر."
                )
            }
            val dataUrl = "data:" + attachment.mimeType + ";base64," +
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUrl))
            )
        }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", content))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("stream", true)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .header("X-Title", "Hakim")
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
                            "رفض OpenRouter مفتاح الوصول."
                        )
                        429 -> HakimInferenceEngine.Result.Failure(
                            "انتهت حصة OpenRouter المجانية الحالية.",
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
                    val piece = delta.optString("content")
                    if (piece.isNotEmpty()) {
                        full.append(piece)
                        onDelta(piece)
                    }
                }

                val text = full.toString().trim()
                if (text.isBlank()) {
                    HakimInferenceEngine.Result.Failure(
                        "اكتملت القناة المجانية دون نص نهائي قابل للعرض.",
                        retryable = true
                    )
                } else {
                    HakimInferenceEngine.Result.Success(
                        text = text,
                        evidence = listOf(
                            "engine=openrouter-free",
                            "model=openrouter/free",
                            "token_price=0",
                            "returned_in_app=true"
                        )
                    )
                }
            }
        } catch (_: java.io.InterruptedIOException) {
            HakimInferenceEngine.Result.Failure("أُلغي الطلب أو انتهت مهلته.", retryable = true)
        } catch (_: Exception) {
            HakimInferenceEngine.Result.Failure("تعذر الاتصال بمحرك OpenRouter المجاني.", retryable = true)
        } finally {
            activeCall = null
        }
    }

    override fun cancel() {
        activeCall?.cancel()
    }

    private fun readAttachmentBounded(
        attachment: HakimAttachmentGateway.Attachment,
        limit: Long
    ): ByteArray? = runCatching {
        context.contentResolver.openInputStream(attachment.uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read.toLong()
                if (total > limit) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    companion object {
        const val ID = "openrouter-free"
        const val SECRET_OPENROUTER_KEY = "openrouter_api_key"
        private const val MODEL = "openrouter/free"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val MAX_IMAGE_INLINE = 8L * 1024L * 1024L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
