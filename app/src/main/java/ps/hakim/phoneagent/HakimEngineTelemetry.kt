package ps.hakim.phoneagent

import android.content.Context
import kotlin.math.roundToInt

/** Learns only operational quality: success/failure and latency. */
object HakimEngineTelemetry {
    private const val PREFS = "hakim_engine_telemetry"

    data class Snapshot(
        val successes: Int,
        val failures: Int,
        val latencyMs: Long,
        val consecutiveFailures: Int,
        val lastOutcomeAt: Long
    ) {
        val attempts: Int get() = successes + failures

        /**
         * موثوقية ملساء بقبلي محافظ ٧/١٠ بدل القفز إلى ١٠٠٪ بعد نجاح واحد.
         */
        val reliability: Int
            get() = (((successes + 7.0) / (attempts + 10.0)) * 100.0)
                .roundToInt().coerceIn(0, 100)

        /**
         * ثقة حجم العينة منفصلة عن نسبة النجاح؛ لا تورّث العينة الصغيرة يقينًا زائفًا.
         */
        val confidence: Int
            get() = ((attempts.toDouble() / (attempts + 8.0)) * 100.0)
                .roundToInt().coerceIn(0, 100)
    }

    fun snapshot(context: Context, engineId: String): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            successes = p.getInt(engineId + "_ok", 0),
            failures = p.getInt(engineId + "_fail", 0),
            latencyMs = p.getLong(engineId + "_latency", 0L),
            consecutiveFailures = p.getInt(engineId + "_consecutive_failures", 0),
            lastOutcomeAt = p.getLong(engineId + "_last_outcome_at", 0L)
        )
    }

    fun record(context: Context, engineId: String, success: Boolean, latencyMs: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val okKey = engineId + "_ok"
        val failKey = engineId + "_fail"
        val oldLatency = p.getLong(engineId + "_latency", 0L)
        val nextLatency = if (oldLatency <= 0L) latencyMs else ((oldLatency * 3L) + latencyMs) / 4L
        val oldConsecutive = p.getInt(engineId + "_consecutive_failures", 0)
        p.edit()
            .putInt(okKey, p.getInt(okKey, 0) + if (success) 1 else 0)
            .putInt(failKey, p.getInt(failKey, 0) + if (success) 0 else 1)
            .putLong(engineId + "_latency", nextLatency.coerceAtLeast(0L))
            .putInt(engineId + "_consecutive_failures", if (success) 0 else (oldConsecutive + 1).coerceAtMost(100))
            .putLong(engineId + "_last_outcome_at", System.currentTimeMillis())
            .apply()
    }
}
