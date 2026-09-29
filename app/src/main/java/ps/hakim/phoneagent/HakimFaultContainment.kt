package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * حاجز أعطال مركزي لحكيم.
 *
 * الهدف ليس الادعاء باستحالة الخطأ، بل منع الخطأ الصامت والتكرار الأعمى وانتشار
 * الأثر: تسجيل آمن محدود، قاطع دائرة للفشل المتكرر، وحجب الأفعال عالية الأثر
 * عند وجود فشل حرج حتى ينجح نفس المسار من جديد.
 *
 * لا تُحفظ رسائل الاستثناءات أو المدخلات أو الأسرار؛ فقط اسم المكوّن/العملية
 * ونوع الاستثناء وأعداد/أزمنة محدودة.
 */
object HakimFaultContainment {
    const val VERSION = "FAULT-CONTAINMENT-2026-09-29-v1"
    private const val PREFS = "hakim_fault_containment"
    private const val MAX_EVENTS = 48
    private const val FAILURE_WINDOW_MS = 5L * 60L * 1000L
    private const val CIRCUIT_COOLDOWN_MS = 10L * 60L * 1000L
    private const val MAX_SAME_FAILURES = 3

    fun guard(
        context: Context,
        component: String,
        operation: String,
        critical: Boolean = false,
        block: () -> Unit
    ): Boolean {
        if (!shouldAttempt(context, component, operation)) {
            recordBlocked(context, component, operation)
            return false
        }
        return try {
            block()
            recordSuccess(context, component, operation)
            true
        } catch (e: Exception) {
            recordFailure(context, component, operation, e, critical)
            false
        }
    }

    @Synchronized
    fun shouldAttempt(context: Context, component: String, operation: String): Boolean {
        val p = prefs(context)
        val key = key(component, operation)
        val now = System.currentTimeMillis()
        val openUntil = p.getLong("open_until_$key", 0L)
        if (openUntil <= 0L) return true
        if (now >= openUntil) {
            p.edit().remove("open_until_$key").apply()
            return true
        }
        return false
    }

    @Synchronized
    fun recordFailure(
        context: Context,
        component: String,
        operation: String,
        error: Exception,
        critical: Boolean = false
    ) {
        val p = prefs(context)
        val key = key(component, operation)
        val now = System.currentTimeMillis()
        val lastAt = p.getLong("last_failure_at_$key", 0L)
        val previousCount = p.getInt("failure_count_$key", 0)
        val count = if (lastAt > 0L && now - lastAt <= FAILURE_WINDOW_MS) previousCount + 1 else 1
        val errorType = error.javaClass.simpleName.take(64).ifBlank { "Exception" }

        val edit = p.edit()
            .putInt("failure_count_$key", count)
            .putLong("last_failure_at_$key", now)
            .putString("last_fault_component", safe(component))
            .putString("last_fault_operation", safe(operation))
            .putString("last_fault_type", errorType)
            .putLong("last_fault_at", now)
            .putBoolean("recovery_required", true)

        if (count >= MAX_SAME_FAILURES) {
            edit.putLong("open_until_$key", now + CIRCUIT_COOLDOWN_MS)
        }
        if (critical) {
            edit.putBoolean("critical_$key", true)
        }
        edit.apply()
        appendEvent(context, "failure", component, operation, errorType)
    }

    @Synchronized
    fun recordSuccess(context: Context, component: String, operation: String) {
        val p = prefs(context)
        val key = key(component, operation)
        p.edit()
            .remove("failure_count_$key")
            .remove("last_failure_at_$key")
            .remove("open_until_$key")
            .remove("critical_$key")
            .putLong("last_success_at_$key", System.currentTimeMillis())
            .putBoolean("recovery_required", hasAnyActiveFault(p, excluding = key))
            .apply()
    }

    fun canExecuteHighImpact(context: Context): Boolean {
        val p = prefs(context)
        return !p.all.any { (k, v) -> k.startsWith("critical_") && v == true }
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val openCircuits = p.all.count { (k, v) ->
            k.startsWith("open_until_") && (v as? Long ?: 0L) > now
        }
        val criticalBlocks = p.all.count { (k, v) -> k.startsWith("critical_") && v == true }
        return JSONObject()
            .put("fault_containment", true)
            .put("version", VERSION)
            .put("critical_path_silent_failures_forbidden", true)
            .put("bounded_retry", true)
            .put("circuit_breaker", true)
            .put("high_impact_fail_closed", true)
            .put("raw_exception_message_persisted", false)
            .put("max_same_failures", MAX_SAME_FAILURES)
            .put("failure_window_ms", FAILURE_WINDOW_MS)
            .put("circuit_cooldown_ms", CIRCUIT_COOLDOWN_MS)
            .put("open_circuits", openCircuits)
            .put("critical_blocks", criticalBlocks)
            .put("high_impact_blocked", criticalBlocks > 0)
            .put("recovery_required", p.getBoolean("recovery_required", false))
            .put("last_fault_component", p.getString("last_fault_component", ""))
            .put("last_fault_operation", p.getString("last_fault_operation", ""))
            .put("last_fault_type", p.getString("last_fault_type", ""))
            .put("last_fault_at", p.getLong("last_fault_at", 0L))
            .put("last_blocked_at", p.getLong("last_blocked_at", 0L))
            .put("event_count", safeEvents(p.getString("events", "[]").orEmpty()).length())
    }

    @Synchronized
    private fun recordBlocked(context: Context, component: String, operation: String) {
        prefs(context).edit()
            .putLong("last_blocked_at", System.currentTimeMillis())
            .putString("last_blocked_component", safe(component))
            .putString("last_blocked_operation", safe(operation))
            .apply()
        appendEvent(context, "circuit_block", component, operation, null)
    }

    @Synchronized
    private fun appendEvent(
        context: Context,
        kind: String,
        component: String,
        operation: String,
        errorType: String?
    ) {
        val p = prefs(context)
        val events = safeEvents(p.getString("events", "[]").orEmpty())
        val event = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("kind", kind.take(24))
            .put("component", safe(component))
            .put("operation", safe(operation))
        if (!errorType.isNullOrBlank()) event.put("error_type", errorType.take(64))
        events.put(event)
        while (events.length() > MAX_EVENTS) events.remove(0)
        p.edit().putString("events", events.toString()).apply()
    }

    private fun hasAnyActiveFault(p: android.content.SharedPreferences, excluding: String): Boolean =
        p.all.any { (k, v) ->
            (k.startsWith("critical_") && !k.endsWith(excluding) && v == true) ||
                (k.startsWith("open_until_") && !k.endsWith(excluding) &&
                    (v as? Long ?: 0L) > System.currentTimeMillis())
        }

    private fun key(component: String, operation: String): String =
        (safe(component) + "__" + safe(operation)).take(96)

    private fun safe(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_").take(48)

    private fun safeEvents(raw: String): JSONArray =
        try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
