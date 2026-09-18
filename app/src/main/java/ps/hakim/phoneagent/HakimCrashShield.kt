package ps.hakim.phoneagent

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle

/**
 * حارس تعطل محلي منخفض البيانات.
 * يحفظ نوع الخطأ ومسار الاستدعاء فقط، ولا يخزن نصوص المستخدم أو الاعتمادات.
 * بعد CRASH/ANR حديث يمنع الاستئناف الاستباقي مؤقتًا كي لا يتحول الخطأ إلى حلقة إغلاق.
 */
object HakimCrashShield {
    const val VERSION = "CRASH-SHIELD-2026-09-18-v3"
    private const val PREFS = "hakim_crash_shield"
    private const val PROACTIVE_PREFS = "hakim_proactive"
    private const val SAFE_RECOVERY_MS = 30L * 60L * 1000L

    @Volatile private var installed = false
    @Volatile private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(app: Application) {
        if (installed) return
        installed = true
        capturePreviousExit(app)

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, "uncaught", error)
            previousHandler?.uncaughtException(thread, error)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
        }

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (activity is HakimAgentsChatActivity) suppressRiskyAutoResume(activity)
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun guardNonCritical(context: Context, label: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        if (isFatal(t)) throw t
        record(context, label, t)
        false
    }

    fun recordNonFatal(context: Context, label: String, error: Throwable) {
        if (isFatal(error)) throw error
        record(context, label, error)
    }

    fun suppressProactiveResumeFor(context: Context, durationMs: Long, reason: String) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentUntil = prefs.getLong("safe_recovery_until", 0L)
        val requestedUntil = now + durationMs.coerceIn(0L, 60L * 60L * 1000L)
        prefs.edit()
            .putLong("safe_recovery_until", maxOf(currentUntil, requestedUntil))
            .putString("last_suppression_reason", reason.take(120))
            .putLong("last_suppression_at", now)
            .apply()
    }

    fun shouldSuppressProactiveResume(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("safe_recovery_until", 0L)
        return System.currentTimeMillis() < until
    }

    private fun suppressRiskyAutoResume(activity: HakimAgentsChatActivity) {
        if (!shouldSuppressProactiveResume(activity)) return
        val mission = runCatching { HakimMissionLedger.active(activity) }.getOrNull() ?: return
        activity.getSharedPreferences(PROACTIVE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_auto_resume_mission_id", mission.id)
            .putLong("last_auto_resume_at", System.currentTimeMillis())
            .apply()
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("safe_recovery_session_active", true)
            .apply()
    }

    private fun capturePreviousExit(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val manager = app.getSystemService(ActivityManager::class.java) ?: return@runCatching
            val latest = manager.getHistoricalProcessExitReasons(app.packageName, 0, 8)
                .maxByOrNull { it.timestamp } ?: return@runCatching
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getLong("last_seen_exit_timestamp", 0L)
            if (latest.timestamp <= seen) return@runCatching

            val dangerous = latest.reason == ApplicationExitInfo.REASON_CRASH ||
                latest.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                latest.reason == ApplicationExitInfo.REASON_ANR
            val edit = prefs.edit()
                .putLong("last_seen_exit_timestamp", latest.timestamp)
                .putInt("last_exit_reason", latest.reason)
            if (dangerous) {
                edit.putLong("last_crash_or_anr_at", latest.timestamp)
                    .putLong("safe_recovery_until", System.currentTimeMillis() + SAFE_RECOVERY_MS)
                    .putBoolean("safe_recovery_session_active", true)
            }
            edit.apply()
        }
    }

    private fun isFatal(t: Throwable): Boolean =
        t is VirtualMachineError || t is ThreadDeath || t is LinkageError

    private fun record(context: Context, label: String, error: Throwable) {
        val stack = error.stackTrace.take(16).joinToString("\n") {
            "${it.className}.${it.methodName}:${it.lineNumber}"
        }.take(6000)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_recorded_failure_at", System.currentTimeMillis())
            .putString("last_recorded_failure_label", label.take(120))
            .putString("last_recorded_failure_type", error.javaClass.name.take(220))
            .putString("last_recorded_failure_stack", stack)
            .apply()
    }

    fun status(context: Context): Map<String, Any> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return linkedMapOf(
            "version" to VERSION,
            "installed" to installed,
            "recent_crash_or_anr_suppresses_proactive_resume" to true,
            "interactive_suppression_supported" to true,
            "records_user_text_or_credentials" to false,
            "safe_recovery_active" to shouldSuppressProactiveResume(context),
            "last_exit_reason" to p.getInt("last_exit_reason", -1),
            "last_seen_exit_timestamp" to p.getLong("last_seen_exit_timestamp", 0L),
            "last_crash_or_anr_at" to p.getLong("last_crash_or_anr_at", 0L),
            "last_recorded_failure_at" to p.getLong("last_recorded_failure_at", 0L),
            "last_recorded_failure_label" to p.getString("last_recorded_failure_label", "").orEmpty(),
            "last_recorded_failure_type" to p.getString("last_recorded_failure_type", "").orEmpty(),
            "last_suppression_reason" to p.getString("last_suppression_reason", "").orEmpty()
        )
    }
}
