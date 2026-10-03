package ps.hakim.phoneagent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * قناة تنفيذ محلية عبر Termux RUN_COMMAND.
 *
 * لا تقبل shell حرًا. كل عملية تُحوَّل إلى profile ثابت داخل
 * ~/.hakim/bin/hakim-control في Termux. هذا يفصل بين امتلاك قناة قوية
 * وبين منح صلاحية غير محدودة للأوامر.
 */
object HakimTermuxControl {
    const val VERSION = "TERMUX-LOCAL-CONTROL-2026-10-03-v1"

    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    private const val CONTROL_PATH = "/data/data/com.termux/files/home/.hakim/bin/hakim-control"
    private const val WORKDIR = "/data/data/com.termux/files/home"
    private const val PREFS = "hakim_termux_control"
    private const val MIN_REPEAT_MS = 5_000L
    private const val ONLINE_WINDOW_MS = 10L * 60L * 1000L

    private val executionId = AtomicInteger(10_000)
    private val SAFE_PROFILES = setOf(
        "status",
        "adb_status",
        "adb_connect",
        "adb_selftest",
        "resilience_status",
    )

    fun isInstalled(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                TERMUX_PACKAGE,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(TERMUX_PACKAGE, 0)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED

    fun ready(context: Context): Boolean =
        isInstalled(context) && hasRunCommandPermission(context)

    @Synchronized
    fun dispatch(context: Context, profile: String, reason: String = "manual"): JSONObject {
        val app = context.applicationContext
        if (profile !in SAFE_PROFILES) {
            return JSONObject()
                .put("ok", false)
                .put("error", "termux_profile_not_allowed")
                .put("profile", profile.take(40))
        }
        if (!isInstalled(app)) {
            return JSONObject().put("ok", false).put("error", "termux_not_installed")
        }
        if (!hasRunCommandPermission(app)) {
            return JSONObject().put("ok", false).put("error", "termux_run_command_permission_missing")
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastProfile = prefs.getString("last_profile", "").orEmpty()
        val lastAt = prefs.getLong("last_dispatch_at", 0L)
        if (profile == lastProfile && now - lastAt < MIN_REPEAT_MS) {
            return JSONObject()
                .put("ok", true)
                .put("dispatched", false)
                .put("throttled", true)
                .put("profile", profile)
        }

        val id = executionId.incrementAndGet()
        val callback = Intent(app, HakimTermuxResultReceiver::class.java)
            .putExtra("execution_id", id)
            .putExtra("profile", profile)
            .putExtra("reason", reason.take(80))
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(app, id, callback, flags)

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, CONTROL_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf(profile))
            putExtra(EXTRA_WORKDIR, WORKDIR)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_PENDING_INTENT, pending)
        }

        return try {
            app.startService(intent)
            prefs.edit()
                .putString("version", VERSION)
                .putString("state", "DISPATCHED")
                .putString("last_profile", profile)
                .putString("last_reason", reason.take(80))
                .putInt("last_execution_id", id)
                .putLong("last_dispatch_at", now)
                .remove("last_error")
                .apply()
            JSONObject()
                .put("ok", true)
                .put("dispatched", true)
                .put("profile", profile)
                .put("execution_id", id)
        } catch (e: Exception) {
            prefs.edit()
                .putString("state", "BLOCKED")
                .putString("last_error", e.javaClass.simpleName)
                .putLong("last_dispatch_at", now)
                .apply()
            JSONObject()
                .put("ok", false)
                .put("error", "termux_dispatch_failed")
                .put("detail", e.javaClass.simpleName)
        }
    }

    fun recover(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        if (!ready(app)) return status(app).put("ok", false).put("error", "termux_not_ready")
        val result = dispatch(app, "adb_connect", reason)
        return status(app)
            .put("ok", result.optBoolean("ok", false))
            .put("recover_dispatch", result)
    }

    fun probe(context: Context, reason: String = "probe"): JSONObject {
        val app = context.applicationContext
        if (!ready(app)) return status(app).put("ok", false).put("error", "termux_not_ready")
        val result = dispatch(app, "status", reason)
        return status(app)
            .put("ok", result.optBoolean("ok", false))
            .put("probe_dispatch", result)
    }

    fun recordResult(
        context: Context,
        executionId: Int,
        profile: String,
        stdout: String,
        stderr: String,
        exitCode: Int,
        errCode: Int,
        errMessage: String,
    ) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val cleanOut = sanitize(stdout)
        val cleanErr = sanitize(stderr)
        val cleanMessage = sanitize(errMessage)
        val success = exitCode == 0 && errCode <= 0 &&
            (profile != "status" || cleanOut.contains("HAKIM_TERMUX_CONTROL=PASS"))

        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("version", VERSION)
            .putString("state", if (success) "ONLINE" else "RESULT_FAILED")
            .putInt("last_execution_id", executionId)
            .putString("last_profile", profile.take(40))
            .putString("last_stdout", cleanOut.take(4096))
            .putString("last_stderr", cleanErr.take(2048))
            .putInt("last_exit_code", exitCode)
            .putInt("last_err_code", errCode)
            .putString("last_error", if (success) "" else cleanMessage.take(512))
            .putLong("last_result_at", now)
            .apply()

        if (success) {
            HakimAutonomousContinuation.pulse(app, "termux_" + profile.take(32))
        }
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastResultAt = p.getLong("last_result_at", 0L)
        val recent = lastResultAt > 0L && System.currentTimeMillis() - lastResultAt <= ONLINE_WINDOW_MS
        val online = p.getString("state", "") == "ONLINE" && recent
        return JSONObject()
            .put("termux_local_control", true)
            .put("version", VERSION)
            .put("termux_installed", isInstalled(app))
            .put("run_command_permission", hasRunCommandPermission(app))
            .put("ready", ready(app))
            .put("online", online)
            .put("state", if (online) "ONLINE" else p.getString("state", "UNCONFIGURED"))
            .put("last_profile", p.getString("last_profile", ""))
            .put("last_dispatch_at", p.getLong("last_dispatch_at", 0L))
            .put("last_result_at", lastResultAt)
            .put("last_exit_code", p.getInt("last_exit_code", Int.MIN_VALUE))
            .put("fixed_profiles_only", true)
            .put("arbitrary_shell_exposed", false)
            .put("external_remote_quota_required", false)
            .put("paid_provider_required", false)
            .put("local_transport", true)
            .put("high_impact_requires_separate_gate", true)
            .put("unlimited_claim", false)
            .put("resource_limits_apply", true)
    }

    private fun sanitize(raw: String): String {
        var value = raw
            .replace(Regex("(?i)(password|passwd|token|secret|authorization|relay[_-]?key)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
        if (value.length > 8192) value = value.take(8192)
        return value
    }
}

class HakimTermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("result") ?: return
        val executionId = intent.getIntExtra("execution_id", 0)
        val profile = intent.getStringExtra("profile").orEmpty()
        if (executionId <= 0 || profile.isBlank()) return
        HakimTermuxControl.recordResult(
            context.applicationContext,
            executionId,
            profile,
            result.getString("stdout", "").orEmpty(),
            result.getString("stderr", "").orEmpty(),
            result.getInt("exitCode", -1),
            result.getInt("err", -1),
            result.getString("errmsg", "").orEmpty(),
        )
    }
}
