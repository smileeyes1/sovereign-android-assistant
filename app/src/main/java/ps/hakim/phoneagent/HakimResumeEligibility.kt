package ps.hakim.phoneagent

/** Explicit allow-list: unknown and terminal states fail closed. */
internal object HakimResumeEligibility {
    fun canResume(state: String, resumable: Boolean): Boolean =
        resumable && state in setOf("QUEUED", "RUNNING", "VERIFYING", "WAITING", "BLOCKED", "PAUSED", "FAILED")
}
