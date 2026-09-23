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
        // A FINAL product must register at least one official/local GENERAL_CHAT engine here.
        // Until then the release is correctly classified as a prototype/candidate.
        return emptyList()
    }

    fun hasFieldUsableGeneralChat(context: Context): Boolean {
        return directEngines(context).any {
            HakimInferenceEngine.Capability.GENERAL_CHAT in it.capabilities
        }
    }
}
