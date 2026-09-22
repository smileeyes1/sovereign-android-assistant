package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import kotlin.math.max

/**
 * محرك الاستمرارية والقيمة.
 * الاستدامة = بقاء المقصد والحالة وقابلية الاستئناف، لا إبقاء المعالج/الشبكة مشغولين.
 */
object HakimValueContinuityEngine {
    private const val PREFS = "hakim_value_continuity"

    enum class Mode { EXECUTE, VERIFY, REPAIR, REROUTE, WAIT, COMPLETE, GATE }

    data class Signal(
        val goalId: String,
        val gap: Double,
        val expectedBenefit: Double,
        val evidence: Double,
        val cost: Double,
        val risk: Double,
        val resourceCost: Double,
        val newInformation: Boolean,
        val sameAttemptCount: Int,
        val acceptanceMet: Boolean,
        val authorized: Boolean,
        val reversible: Boolean
    )

    fun decide(context: Context, s: Signal): JSONObject {
        val benefit = max(0.0, s.expectedBenefit) * s.evidence.coerceIn(0.0, 1.0)
        val burden = max(0.0, s.cost) + max(0.0, s.risk) + max(0.0, s.resourceCost)
        val net = benefit - burden

        val mode = when {
            s.acceptanceMet && s.gap <= 0.0 -> Mode.COMPLETE
            !s.authorized || !s.reversible -> Mode.GATE
            s.sameAttemptCount >= 2 && !s.newInformation -> Mode.WAIT
            s.gap > 0.0 && net > 0.0 -> Mode.EXECUTE
            else -> Mode.WAIT
        }
        val reason = when (mode) {
            Mode.COMPLETE -> "acceptance_proven"
            Mode.GATE -> "authorization_or_irreversibility_gate"
            Mode.EXECUTE -> "positive_marginal_value"
            Mode.WAIT -> if (s.sameAttemptCount >= 2 && !s.newInformation) "anti_loop_no_new_evidence" else "no_positive_action_now"
            else -> "reassessment_required"
        }
        return persist(context, s.goalId, mode, reason, net, s.gap)
    }

    fun checkpoint(context: Context, goalId: String, state: String, nextCondition: String, verifiedBaseline: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("goal_id", goalId.take(120))
            .putString("checkpoint_state", state.take(4000))
            .putString("resume_condition", nextCondition.take(1000))
            .putString("verified_baseline", verifiedBaseline.take(500))
            .putLong("checkpoint_at", System.currentTimeMillis())
            .apply()
    }

    fun resumePending(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val goalId = p.getString("goal_id", "").orEmpty()
        val state = p.getString("checkpoint_state", "").orEmpty()
        val condition = p.getString("resume_condition", "").orEmpty()
        if (goalId.isBlank() || state.isBlank()) {
            return JSONObject().put("resumed", false).put("reason", "no_checkpoint")
        }
        p.edit()
            .putLong("resume_attempt_at", System.currentTimeMillis())
            .putString("last_mode", Mode.VERIFY.name)
            .putString("last_reason", "checkpoint_recovered_verify_before_continue")
            .apply()
        return JSONObject()
            .put("resumed", true)
            .put("goal_id", goalId)
            .put("checkpoint_state", state)
            .put("resume_condition", condition)
            .put("mode", Mode.VERIFY.name)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("closed_loop_value_control", true)
            .put("continuity_is_not_busy_loop", true)
            .put("positive_marginal_value_required", true)
            .put("anti_loop", true)
            .put("checkpoint_resume", true)
            .put("verified_baseline_protected", true)
            .put("autonomous_resume", true)
            .put("resume_requires_verify", true)
            .put("last_mode", p.getString("last_mode", ""))
            .put("last_reason", p.getString("last_reason", ""))
            .put("last_net_value", p.getString("last_net_value", ""))
            .put("resume_condition", p.getString("resume_condition", ""))
            .put("verified_baseline", p.getString("verified_baseline", ""))
            .put("last_decision_at", p.getLong("last_decision_at", 0L))
    }

    private fun persist(context: Context, goalId: String, mode: Mode, reason: String, net: Double, gap: Double): JSONObject {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("goal_id", goalId.take(120))
            .putString("last_mode", mode.name)
            .putString("last_reason", reason)
            .putString("last_net_value", "%.6f".format(java.util.Locale.US, net))
            .putLong("last_decision_at", now)
            .apply()
        return JSONObject()
            .put("mode", mode.name)
            .put("reason", reason)
            .put("net_value", net)
            .put("gap", gap)
            .put("time", now)
    }
}
