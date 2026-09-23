package ps.hakim.phoneagent

import android.content.Context

/**
 * Product-level registry.
 *
 * External app/browser handoff is deliberately NOT registered as a GENERAL_CHAT engine because
 * it cannot return a model answer into Hakim by itself.
 */
object HakimEngineRegistry {
    fun directEngines(context: Context): List<HakimInferenceEngine> {
        if (!HakimSecretStore.has(context, GeminiDirectEngine.SECRET_GEMINI_KEY)) return emptyList()
        return HakimFreeOnlyPolicy.allowedGeminiModels(context)
            .map { model -> GeminiDirectEngine(context, model) }
    }

    fun bestGeneralChat(
        context: Context,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): HakimInferenceEngine? {
        return directEngines(context).firstOrNull { engine ->
            HakimInferenceEngine.Capability.GENERAL_CHAT in engine.capabilities &&
                attachmentsSupported(engine, attachments)
        }
    }

    fun fallbackGeneralChat(
        context: Context,
        failedEngineId: String,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): List<HakimInferenceEngine> {
        return directEngines(context).filter { engine ->
            engine.id != failedEngineId &&
                HakimInferenceEngine.Capability.GENERAL_CHAT in engine.capabilities &&
                attachmentsSupported(engine, attachments)
        }
    }

    fun hasConfiguredGeneralChat(context: Context): Boolean =
        bestGeneralChat(context, emptyList()) != null

    private fun attachmentsSupported(
        engine: HakimInferenceEngine,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): Boolean {
        return attachments.all { a ->
            when {
                a.mimeType.startsWith("image/") ->
                    HakimInferenceEngine.Capability.IMAGES in engine.capabilities
                a.mimeType.startsWith("audio/") ->
                    HakimInferenceEngine.Capability.AUDIO in engine.capabilities
                a.mimeType.startsWith("video/") ->
                    HakimInferenceEngine.Capability.VIDEO in engine.capabilities
                else ->
                    HakimInferenceEngine.Capability.FILES in engine.capabilities
            }
        }
    }
}
