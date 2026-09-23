package ps.hakim.phoneagent

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity

/**
 * Fully on-device general chat engine.
 *
 * No API key, no subscription, no network after the model is downloaded.
 * First version is text-only and CPU-safe; router may still choose cloud engines
 * for unsupported modalities when explicitly configured.
 */
class LiteRtLocalEngine(private val context: Context) : HakimInferenceEngine {
    override val id: String = "litert-local-gemma4-e2b"
    override val displayName: String = "حكيم المحلي — Gemma 4 E2B"
    override val capabilities: Set<HakimInferenceEngine.Capability> =
        setOf(HakimInferenceEngine.Capability.GENERAL_CHAT)

    @Volatile
    private var cancelled = false

    override fun complete(
        instruction: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        onDelta: (String) -> Unit
    ): HakimInferenceEngine.Result {
        if (attachments.isNotEmpty()) {
            return HakimInferenceEngine.Result.Unavailable(
                "المحرك المحلي الحالي نصي؛ سيستخدم حكيم محركًا آخر للمرفقات إن كان متاحًا."
            )
        }
        if (HakimLocalModelManager.state(context) != HakimLocalModelManager.State.READY) {
            return HakimInferenceEngine.Result.NeedsAuthorization(
                "الذكاء المحلي المجاني غير جاهز بعد؛ افتح «إدارة» لتنزيل النموذج والتحقق منه."
            )
        }

        cancelled = false
        return try {
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            val config = EngineConfig(
                modelPath = HakimLocalModelManager.modelFile(context).absolutePath,
                backend = Backend.CPU()
            )
            Engine(config).use { engine ->
                engine.initialize()
                if (cancelled) {
                    return@use HakimInferenceEngine.Result.Failure("أُلغي التنفيذ المحلي.", true)
                }
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "أنت محرك محلي يعمل تحت إشراف حكيم. أجب بالعربية افتراضيًا. " +
                            "نفّذ مقصد المستخدم بأفضل جودة ممكنة ولا تدّعِ أفعالًا خارجية لم تحدث."
                    )
                )
                engine.createConversation(conversationConfig).use { conversation ->
                    val response = conversation.sendMessage(instruction).toString().trim()
                    if (cancelled) {
                        HakimInferenceEngine.Result.Failure("أُلغي التنفيذ المحلي.", true)
                    } else if (response.isBlank()) {
                        HakimInferenceEngine.Result.Failure("لم ينتج المحرك المحلي جوابًا صالحًا.", true)
                    } else {
                        onDelta(response)
                        HakimInferenceEngine.Result.Success(
                            text = response,
                            evidence = listOf(
                                "engine=litert-local",
                                "network_required=false",
                                "api_key_required=false",
                                "returned_in_app=true"
                            )
                        )
                    }
                }
            }
        } catch (_: OutOfMemoryError) {
            HakimInferenceEngine.Result.Unavailable(
                "ذاكرة الهاتف لم تكفِ لهذا النموذج؛ يلزم نموذج محلي أخف."
            )
        } catch (_: Throwable) {
            HakimInferenceEngine.Result.Failure(
                "تعذر تشغيل الذكاء المحلي على هذا الجهاز بهذه التهيئة.",
                retryable = true
            )
        }
    }

    override fun cancel() {
        cancelled = true
    }
}
