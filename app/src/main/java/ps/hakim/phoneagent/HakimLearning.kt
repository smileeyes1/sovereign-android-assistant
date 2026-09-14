package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HakimLearning {
    private const val PREFS = "hakim_learning"
    private const val MAX_EVENTS = 40

    fun initialize(context: Context) {
        HakimQuranicInvariantKernel.requireInherited("learning_initialize")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("created_at")) {
            p.edit()
                .putLong("created_at", System.currentTimeMillis())
                .putLong("attempts", 0L)
                .putLong("successes", 0L)
                .putLong("failures", 0L)
                .putInt("consecutive_failures", 0)
                .putBoolean("improvement_needed", false)
                .putString("events", "[]")
                .apply()
        }
    }

    fun recordAttempt(context: Context, action: String) {
        HakimQuranicInvariantKernel.requireInherited("learning_attempt")
        val safeAction = sanitizeAction(action)
        if (safeAction.isBlank()) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putLong("attempts", p.getLong("attempts", 0L) + 1L).apply()
        appendEvent(context, "attempt", safeAction, null)
    }

    fun recordResult(context: Context, action: String, success: Boolean) {
        HakimQuranicInvariantKernel.requireInherited("learning_result")
        val safeAction = sanitizeAction(action)
        if (safeAction.isBlank()) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit()
        if (success) {
            edit.putLong("successes", p.getLong("successes", 0L) + 1L)
            edit.putInt("consecutive_failures", 0)
            edit.putBoolean("improvement_needed", false)
            edit.putLong("action_${safeAction}_success", p.getLong("action_${safeAction}_success", 0L) + 1L)
        } else {
            val streak = p.getInt("consecutive_failures", 0) + 1
            edit.putLong("failures", p.getLong("failures", 0L) + 1L)
            edit.putInt("consecutive_failures", streak)
            edit.putBoolean("improvement_needed", streak >= 3)
            edit.putLong("action_${safeAction}_failure", p.getLong("action_${safeAction}_failure", 0L) + 1L)
        }
        edit.putLong("last_result_at", System.currentTimeMillis()).apply()
        appendEvent(context, if (success) "success" else "failure", safeAction, null)
    }

    fun recordHealth(context: Context, health: JSONObject) {
        HakimQuranicInvariantKernel.requireInherited("learning_health")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putString("last_health", health.toString().take(8000))
            .putLong("last_health_at", System.currentTimeMillis())
            .apply()
        appendEvent(context, "health", "self_check", health.optString("status", "unknown"))
    }

    fun snapshot(context: Context): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("learning_snapshot")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val attempts = p.getLong("attempts", 0L)
        val successes = p.getLong("successes", 0L)
        val failures = p.getLong("failures", 0L)
        return JSONObject()
            .put("mode", "تعلم تشغيلي محلي محكوم — لا تدريب نموذج ولا تعديل كود تلقائي عشوائي")
            .put("quranic_kernel_inherited", true)
            .put("attempts", attempts)
            .put("successes", successes)
            .put("failures", failures)
            .put("success_rate", if (successes + failures > 0) successes.toDouble() / (successes + failures).toDouble() else JSONObject.NULL)
            .put("consecutive_failures", p.getInt("consecutive_failures", 0))
            .put("improvement_needed", p.getBoolean("improvement_needed", false))
            .put("last_health_at", p.getLong("last_health_at", 0L))
            .put("events", safeEvents(p.getString("events", "[]").orEmpty()))
    }

    fun maintenance(context: Context) {
        HakimQuranicInvariantKernel.requireInherited("learning_maintenance")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = safeEvents(p.getString("events", "[]").orEmpty())
        while (events.length() > MAX_EVENTS) events.remove(0)
        p.edit().putString("events", events.toString()).apply()
    }

    private fun appendEvent(context: Context, kind: String, action: String, note: String?) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = safeEvents(p.getString("events", "[]").orEmpty())
        val event = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("kind", kind.take(24))
            .put("action", sanitizeAction(action))
        if (!note.isNullOrBlank()) event.put("note", note.take(120))
        events.put(event)
        while (events.length() > MAX_EVENTS) events.remove(0)
        p.edit().putString("events", events.toString()).apply()
    }

    private fun safeEvents(raw: String): JSONArray = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

    private fun sanitizeAction(action: String): String = action
        .lowercase()
        .replace(Regex("[^a-z0-9_\\-]"), "_")
        .take(48)
}
