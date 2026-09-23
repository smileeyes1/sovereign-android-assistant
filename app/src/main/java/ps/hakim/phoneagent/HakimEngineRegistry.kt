package ps.hakim.phoneagent

import android.content.Context

/**
 * Product-level registry.
 *
 * External app/browser handoff is NOT a GENERAL_CHAT engine because
 * it cannot return a model answer into Hakim by itself.
 */
object HakimEngineRegistry {
    fun directEngines(context: Context): List<HakimInferenceEngine> {
        val out = mutableListOf<HakimInferenceEngine>()

        if (HakimSecretStore.has(context, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)) {
            out += OpenRouterFreeEngine(context)
        }

        if (
            HakimSecretStore.has(context, GeminiDirectEngine.SECRET_GEMINI_KEY) &&
            HakimFreePolicy.allows("gemini-direct", context)
        ) {
            out += GeminiDirectEngine(context)
        }

        return out
    }

    fun bestGeneralChat(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): HakimInferenceEngine? =
        HakimWisdomMatrix.choose(context, prompt, attachments, excluded)?.engine

    fun hasConfiguredGeneralChat(context: Context): Boolean =
        bestGeneralChat(context, "", emptyList()) != null

    fun attachmentsSupported(
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
