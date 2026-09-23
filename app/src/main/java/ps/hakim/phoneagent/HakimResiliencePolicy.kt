package ps.hakim.phoneagent

import android.content.Context

/**
 * Prevents a single free provider from trapping Hakim in long "thinking" states.
 *
 * The policy is deliberately bounded: first visible output, no-progress, total call,
 * and repeated-failure circuit breaker all have finite limits.
 */
object HakimResiliencePolicy {
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_STALL_TIMEOUT_SECONDS = 35L
    const val WRITE_TIMEOUT_SECONDS = 45L
    const val CALL_TIMEOUT_SECONDS = 120L
    const val FIRST_VISIBLE_OUTPUT_MS = 25_000L
    const val NO_VISIBLE_PROGRESS_MS = 45_000L

    private const val PREFS = "hakim_engine_resilience"
    private const val FAILURE_THRESHOLD = 2
    private const val COOLDOWN_MS = 10L * 60L * 1000L

    fun isAvailable(context: Context, engineId: String): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(engineId + "_cooldown_until", 0L)
        return System.currentTimeMillis() >= until
    }

    fun remainingCooldownMs(context: Context, engineId: String): Long {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(engineId + "_cooldown_until", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun recordSuccess(context: Context, engineId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(engineId + "_consecutive_failures", 0)
            .putLong(engineId + "_cooldown_until", 0L)
            .remove(engineId + "_last_failure_reason")
            .apply()
    }

    fun recordFailure(
        context: Context,
        engineId: String,
        retryable: Boolean,
        reason: String
    ) {
        if (!retryable) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = p.getInt(engineId + "_consecutive_failures", 0) + 1
        val e = p.edit()
            .putInt(engineId + "_consecutive_failures", next)
            .putLong(engineId + "_last_failure_at", System.currentTimeMillis())
            .putString(engineId + "_last_failure_reason", reason.take(300))
        if (next >= FAILURE_THRESHOLD) {
            e.putLong(engineId + "_cooldown_until", System.currentTimeMillis() + COOLDOWN_MS)
        }
        e.apply()
    }

    fun describe(context: Context, engineId: String): String {
        val remaining = remainingCooldownMs(context, engineId)
        return if (remaining <= 0L) {
            "متاح"
        } else {
            "تبريد مؤقت " + ((remaining + 59_999L) / 60_000L) + " د"
        }
    }
}
