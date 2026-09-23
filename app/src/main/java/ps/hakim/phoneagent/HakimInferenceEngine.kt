package ps.hakim.phoneagent

/**
 * Stable product boundary between Hakim's conversation/executive layer and any inference backend.
 *
 * A GENERAL_CHAT engine must return content to Hakim. Launching another app is not an engine result.
 */
interface HakimInferenceEngine {
    val id: String
    val displayName: String
    val capabilities: Set<Capability>

    enum class Capability {
        GENERAL_CHAT,
        IMAGES,
        FILES,
        AUDIO,
        VIDEO,
        FRESH_WEB,
        TOOL_CALLING
    }

    sealed class Result {
        data class Success(
            val text: String,
            val evidence: List<String> = emptyList()
        ) : Result()

        data class NeedsAuthorization(
            val reason: String
        ) : Result()

        data class Unavailable(
            val reason: String
        ) : Result()

        data class Failure(
            val reason: String,
            val retryable: Boolean
        ) : Result()
    }

    suspend fun complete(
        instruction: String,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): Result
}
