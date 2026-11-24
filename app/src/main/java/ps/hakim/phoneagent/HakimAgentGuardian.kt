package ps.hakim.phoneagent

/** Pure policy gate. Does not execute actions or grant Android permissions. */
object HakimAgentGuardian {
    enum class Effect { READ_LOCAL, WRITE_LOCAL, SEND, PUBLISH, PAY, PERMISSIONS, DEPLOY }
    enum class Decision { ALLOW, REQUIRE_APPROVAL, BLOCK }
    data class Request(
        val idempotencyKey: String,
        val effect: Effect,
        val adapter: String,
        val containsSensitiveData: Boolean = false,
        val targetIsExternal: Boolean = false,
        val explicitApproval: Boolean = false
    )
    data class Result(val decision: Decision, val reason: String)

    /** Caller must persist completed keys and recheck immediately before any side effect. */
    fun evaluate(request: Request, allowedAdapters: Set<String>, completedKeys: Set<String>): Result {
        if (request.idempotencyKey.isBlank()) return Result(Decision.BLOCK, "MISSING_IDEMPOTENCY_KEY")
        if (request.idempotencyKey in completedKeys) return Result(Decision.BLOCK, "ALREADY_COMPLETED")
        if (request.adapter.isBlank() || request.adapter !in allowedAdapters) return Result(Decision.BLOCK, "ADAPTER_NOT_ALLOWED")
        if (request.containsSensitiveData && request.targetIsExternal) return Result(Decision.BLOCK, "SENSITIVE_EXTERNAL_TRANSFER")
        val gated = request.targetIsExternal || request.effect in setOf(
            Effect.SEND, Effect.PUBLISH, Effect.PAY, Effect.PERMISSIONS, Effect.DEPLOY
        )
        if (gated && !request.explicitApproval) return Result(Decision.REQUIRE_APPROVAL, "EXPLICIT_APPROVAL_REQUIRED")
        return Result(Decision.ALLOW, "POLICY_CHECK_PASSED")
    }
}
