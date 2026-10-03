package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * نبضة الاستمرار الذاتي المحكومة.
 *
 * لا تنفذ صلاحيات جديدة ولا أفعالًا عالية الأثر. مهمتها استعادة الهدف المحفوظ،
 * تحريك التشخيص/إعادة التوجيه/التحقق، وتجهيز الاستئناف المرئي عند توفر نافذة آمنة.
 */
object HakimAutonomousContinuation {
    const val VERSION = "AUTONOMOUS-CONTINUATION-2026-10-03-v1"
    private const val PREFS = "hakim_autonomous_continuation"
    private const val MIN_PULSE_MS = 2_000L
    private const val REQUEUE_COOLDOWN_MS = 30_000L

    @Synchronized
    fun pulse(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val p = prefs(app)
        val lastPulse = p.getLong("last_pulse_at", 0L)
        if (now - lastPulse < MIN_PULSE_MS) {
            return status(app).put("throttled", true)
        }

        val continuity = HakimValueContinuityEngine.resumePending(app)
        val supervisor = HakimGoalSupervisor.resume(app)
        val executor = HakimGoalExecutor.tick(app)
        val action = executor.optString("action")

        if (supervisor.optBoolean("active")) {
            when (action) {
                "REROUTE", "DIAGNOSE" -> {
                    HakimExecutionFabric.recover(app, "goal_" + reason.take(48))
                    HakimConstraintDoctor.runAsync(app, "goal_" + reason.take(48))
                }
                "VERIFY" -> HakimSelfCheck.runAsync(app)
            }
        }

        var queued = false
        val task = HakimTaskManager.nextAutoResume(app)
        val pending = HakimTaskManager.pendingResumeRequest(app)
        val lastQueuedId = p.getString("last_queued_task_id", "").orEmpty()
        val lastQueuedAt = p.getLong("last_queued_at", 0L)
        val canQueue = task != null &&
            pending?.id != task.id &&
            action !in setOf("WAIT", "GATED", "COMPLETE") &&
            (task.id != lastQueuedId || now - lastQueuedAt >= REQUEUE_COOLDOWN_MS)

        if (canQueue && task != null) {
            queued = HakimTaskManager.requestResume(app, task.id) != null
            if (queued) {
                p.edit()
                    .putString("last_queued_task_id", task.id)
                    .putLong("last_queued_at", now)
                    .apply()
            }
        }

        p.edit()
            .putString("version", VERSION)
            .putString("last_reason", reason.take(80))
            .putString("last_action", action.take(40))
            .putBoolean("last_goal_active", supervisor.optBoolean("active"))
            .putBoolean("last_resume_queued", queued)
            .putLong("last_pulse_at", now)
            .apply()

        return JSONObject()
            .put("version", VERSION)
            .put("goal_active", supervisor.optBoolean("active"))
            .put("action", action)
            .put("resume_queued", queued)
            .put("continuity", continuity)
            .put("supervisor", supervisor)
            .put("executor", executor)
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        return JSONObject()
            .put("autonomous_continuation", true)
            .put("version", p.getString("version", VERSION))
            .put("event_driven_resume", true)
            .put("periodic_resume_backup", true)
            .put("no_busy_loop", true)
            .put("high_impact_still_gated", true)
            .put("last_reason", p.getString("last_reason", ""))
            .put("last_action", p.getString("last_action", ""))
            .put("last_goal_active", p.getBoolean("last_goal_active", false))
            .put("last_resume_queued", p.getBoolean("last_resume_queued", false))
            .put("last_pulse_at", p.getLong("last_pulse_at", 0L))
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
