package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * منفذ الاستمرارية: يحول حالة المشرف إلى فعل آمن قابل للتحقق.
 * لا ينفذ فعلاً عالي الأثر أو مجهولاً؛ تلك الأفعال تبقى خلف نواة القدرات.
 */
object HakimGoalExecutor {
    private const val PREFS = "hakim_goal_executor"

    fun tick(context: Context): JSONObject {
        val s = HakimGoalSupervisor.resume(context)
        if (!s.optBoolean("active")) return result("IDLE", "no_active_goal")
        val state = s.optString("state")
        val goal = s.optString("goal_id")
        val action = when (state) {
            HakimGoalSupervisor.State.EFFECT_VERIFIED.name -> "COMPLETE"
            HakimGoalSupervisor.State.PROVEN_GATE.name -> "GATED"
            HakimGoalSupervisor.State.WAIT.name -> "WAIT"
            HakimGoalSupervisor.State.REROUTE.name -> "REROUTE"
            HakimGoalSupervisor.State.VERIFY_EFFECT.name -> "VERIFY"
            HakimGoalSupervisor.State.DIAGNOSE.name -> "DIAGNOSE"
            else -> "EXECUTE"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("goal_id", goal.take(120))
            .putString("last_action", action)
            .putLong("heartbeat_at", System.currentTimeMillis())
            .apply()
        return result(action, state).put("goal_id", goal)
    }

    fun heartbeat(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getLong("heartbeat_at", 0L)
        return JSONObject()
            .put("executor", true)
            .put("heartbeat_at", last)
            .put("last_action", p.getString("last_action", ""))
            .put("goal_id", p.getString("goal_id", ""))
    }

    private fun result(action: String, reason: String) =
        JSONObject().put("action", action).put("reason", reason).put("time", System.currentTimeMillis())
}
