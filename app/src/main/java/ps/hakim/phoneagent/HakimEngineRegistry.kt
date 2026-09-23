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
        val out = mutableListOf<HakimInferenceEngine>()
        if (HakimSecretStore.has(context, GeminiDirectEngine.SECRET_GEMINI_KEY)) {
            out += GeminiDirectEngine(context)
        }
        return out
    }

    fun bestGeneralChat(
        context: Context,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): HakimInferenceEngine? {
        val engines = directEngines(context)
            .filter { engine ->
                HakimInferenceEngine.Capability.GENERAL_CHAT in engine.capabilities &&
                    attachmentsSupported(engine, attachments)
            }

        val ranked = engines.associateBy { it.id }
        val fieldVerified = context.getSharedPreferences(
            GeminiDirectEngine.PREFS,
            Context.MODE_PRIVATE
        ).getBoolean("gemini_direct_field_verified", false)

        val candidates = engines.map { engine ->
            HakimWisdomMatrix.Candidate(
                id = engine.id,
                supported = true,
                authorized = true,
                directReturn = true,
                officialChannel = true,
                fieldVerified = if (engine.id == "gemini-direct") fieldVerified else false,
                quality = 85,
                reliability = if (engine.id == "gemini-direct" && fieldVerified) 90 else 70,
                privacy = 55,
                costEfficiency = 75,
                latency = 75,
                reversibility = 95
            )
        }

        return HakimWisdomMatrix.choose(candidates)?.let { ranked[it.id] }
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
