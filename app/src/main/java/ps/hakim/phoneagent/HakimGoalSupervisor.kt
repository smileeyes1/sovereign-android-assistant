package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

object HakimGoalSupervisor {
    private const val PREFS = "hakim_goal_supervisor"
    enum class State { EXECUTE, VERIFY_EFFECT, DIAGNOSE, REROUTE, WAIT, PROVEN_GATE, EFFECT_VERIFIED }
    enum class Recovery { RETRY_CHANGED, REROUTE, WAIT_RESUMABLE, PROVEN_GATE }
    enum class EvidenceStage { REQUESTED, DISPATCHED, OS_ACCEPTED, OS_INSTALLED, UI_OBSERVED, USER_CONFIRMED }

    fun begin(context: Context, goalId: String, acceptance: String, baseline: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("goal_id", goalId.take(120)).putString("acceptance", acceptance.take(4000))
            .putString("baseline", baseline.take(500)).putString("state", State.EXECUTE.name)
            .putBoolean("effect_verified", false).putBoolean("gate_proven", false)
            .putLong("updated_at", System.currentTimeMillis()).apply()
        HakimValueContinuityEngine.checkpoint(context, goalId, "supervisor:EXECUTE", "continue_until_effect_or_proven_gate", baseline)
    }

    fun recordEvidence(context: Context, stage: String, evidence: String, effectVerified: Boolean = false) {
        val known = EvidenceStage.entries.any { it.name == stage }
        require(known) { "unknown_evidence_stage" }
        if (effectVerified) require(stage == EvidenceStage.UI_OBSERVED.name || stage == EvidenceStage.USER_CONFIRMED.name || stage == EvidenceStage.OS_INSTALLED.name) { "effect_requires_observed_evidence" }
        val next = if (effectVerified) State.EFFECT_VERIFIED else State.VERIFY_EFFECT
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("evidence_stage", stage.take(120)).putString("evidence", evidence.take(4000))
            .putBoolean("effect_verified", effectVerified).putString("state", next.name)
            .putLong("evidence_at", System.currentTimeMillis())
            .putLong("updated_at", System.currentTimeMillis()).apply()
    }

    fun proveGate(context: Context, evidence: String, noAuthorizedReroute: Boolean): Boolean {
        if (evidence.isBlank() || !noAuthorizedReroute) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("gate_proven", true).putString("gate_evidence", evidence.take(4000))
            .putString("state", State.PROVEN_GATE.name).putLong("updated_at", System.currentTimeMillis()).apply()
        return true
    }

    fun toolFailed(context: Context, evidence: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("failure_evidence", evidence.take(4000)).putString("state", State.DIAGNOSE.name)
            .putBoolean("effect_verified", false).putLong("updated_at", System.currentTimeMillis()).apply()
    }

    fun recover(context: Context, mode: Recovery, evidence: String, resumeCondition: String = ""): State {
        require(evidence.isNotBlank()) { "recovery_requires_evidence" }
        if (mode == Recovery.PROVEN_GATE) {
            require(resumeCondition.isNotBlank()) { "gate_requires_resume_condition" }
            proveGate(context, evidence, true)
            return State.PROVEN_GATE
        }
        val next = when (mode) {
            Recovery.RETRY_CHANGED -> State.EXECUTE
            Recovery.REROUTE -> State.REROUTE
            Recovery.WAIT_RESUMABLE -> State.WAIT
            Recovery.PROVEN_GATE -> State.PROVEN_GATE
        }
        require(mode != Recovery.WAIT_RESUMABLE || resumeCondition.isNotBlank()) { "wait_requires_resume_condition" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", next.name).putString("recovery_evidence", evidence.take(4000))
            .putString("resume_condition", resumeCondition.take(1000)).putLong("updated_at", System.currentTimeMillis()).apply()
        return next
    }

    fun reroute(context: Context, route: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("reroute", route.take(1000)).putString("state", State.REROUTE.name)
            .putLong("updated_at", System.currentTimeMillis()).apply()
    }

    fun resume(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val goal = p.getString("goal_id", "").orEmpty()
        if (goal.isBlank()) return JSONObject().put("active", false)
        val effect = p.getBoolean("effect_verified", false)
        val gate = p.getBoolean("gate_proven", false)
        val current = p.getString("state", State.EXECUTE.name).orEmpty()
        val next = when {
            effect -> State.EFFECT_VERIFIED
            gate -> State.PROVEN_GATE
            current == State.DIAGNOSE.name -> State.REROUTE
            else -> State.EXECUTE
        }
        p.edit().putString("state", next.name).putLong("resume_at", System.currentTimeMillis()).apply()
        return JSONObject().put("active", true).put("goal_id", goal).put("state", next.name)
            .put("acceptance", p.getString("acceptance", "")).put("effect_verified", effect).put("gate_proven", gate)
    }

    fun canClose(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("effect_verified", false)

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().put("goal_is_unit_of_closure", true).put("tool_success_is_not_goal_success", true)
            .put("hypothetical_gate_forbidden", true).put("failure_requires_reroute", true)
            .put("resume_after_restart", true).put("no_normal_stop_state", true)
            .put("failure_of_means_never_closes_goal", true).put("wait_must_be_resumable", true).put("state", p.getString("state", ""))
            .put("goal_id", p.getString("goal_id", "")).put("effect_verified", p.getBoolean("effect_verified", false))
            .put("gate_proven", p.getBoolean("gate_proven", false))
    }
}
