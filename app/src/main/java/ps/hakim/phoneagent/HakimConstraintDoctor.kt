package ps.hakim.phoneagent

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

object HakimConstraintDoctor {
    private const val CHANNEL_ID = "hakim_constraints"
    private const val NOTIFICATION_ID = 31
    private const val PREFS = "hakim"

    fun runAsync(context: Context, reason: String) {
        val app = context.applicationContext
        Thread {
            try { run(app, reason) } catch (_: Exception) {}
        }.start()
    }

    fun run(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        val invokedByConnectionWatchdog = reason == "periodic_watchdog" || reason.startsWith("periodic_watchdog_")
        if (!invokedByConnectionWatchdog) {
            AutoUpdater.schedule(app)
            HakimSelfCheck.schedule(app)
            HakimConnectionResilience.schedule(app)
        } else {
            prefs.edit()
                .putLong("constraint_doctor_watchdog_schedule_suppressed_at", System.currentTimeMillis())
                .apply()
        }

        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("auth_key", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(app)
        val paired = legacyPaired || securePaired
        val network = hasInternet(app)
        val batteryExempt = isBatteryOptimizationIgnored(app)
        val backgroundRestricted = isBackgroundRestricted(app)
        val notificationsAllowed = notificationsAllowed(app)

        if (!disabled && paired && network) {
            HakimConnectionResilience.recover(app, "constraint_doctor_$reason")
            AutoUpdater.startRealtimeListener(app)
            AutoUpdater.checkAsync(app)
            HakimHealthBeacon.sendAsync(app, "constraint_doctor_$reason")
        }

        val constraints = JSONArray()
        fun add(code: String, severity: String, autoFixable: Boolean, systemGate: Boolean, detail: String) {
            constraints.put(JSONObject()
                .put("code", code)
                .put("severity", severity)
                .put("auto_fixable", autoFixable)
                .put("system_gate", systemGate)
                .put("detail", detail))
        }

        if (disabled) add("PAIRING_DISABLED_BY_USER", "sovereign", false, true, "فصل الاقتران بقرار المستخدم")
        else if (!paired) add("PAIRING_REQUIRED", "blocker", false, true, "يلزم اقتران موثوق ولا يجوز اختلاق السر")
        if (!network) add("NETWORK_UNAVAILABLE", "blocker", false, false, "لا توجد شبكة إنترنت فعالة الآن")
        // لا نطلب صلاحية دائمة لمصادر غير معروفة؛ التحديث يمر بهوية D1 وبوابات القطعة والتوقيع،
        // وأي بوابة نظامية لازمة فعلًا تُطلب عند الفعل لا كقدرة قائمة مسبقًا.
        if (!batteryExempt) add("BATTERY_OPTIMIZATION", "warning", false, true, "قد تقيد تحسينات البطارية الاستمرارية في الخلفية")
        if (backgroundRestricted) add("BACKGROUND_RESTRICTED", "blocker", false, true, "أندرويد يقيد عمل حكيم في الخلفية")
        if (!notificationsAllowed) add("NOTIFICATIONS_DISABLED", "warning", false, true, "تعطيل الإشعارات يخفي تنبيهات الاستعادة والموافقات")
        if (legacyPaired && network && !HakimService.running) add("LEGACY_SERVICE_NOT_RUNNING", "recovering", true, false, "تم طلب إعادة تشغيل خدمة المتصفح القديمة تلقائيًا")
        if (legacyPaired && network && HakimService.running && !HakimService.connected) add("LEGACY_COMMAND_SOCKET_OFFLINE", "recovering", true, false, "إعادة اتصال القناة القديمة يعمل")
        val secureState = prefs.getString("secure_relay_state", "unknown").orEmpty()
        if (securePaired && network && secureState != "connected") {
            add("SECURE_RELAY_RECOVERING", "recovering", true, false, "القناة الآمنة تعيد الاتصال دون تشغيل WebView القديمة")
        }

        val report = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("reason", reason.take(80))
            .put("paired", paired)
            .put("legacy_paired", legacyPaired)
            .put("secure_paired", securePaired)
            .put("user_disabled", disabled)
            .put("network", network)
            .put("service_running", HakimService.running)
            .put("service_connected", HakimService.connected)
            .put("standing_unknown_source_permission_required", false)
            .put("battery_optimization_ignored", batteryExempt)
            .put("background_restricted", backgroundRestricted)
            .put("notifications_allowed", notificationsAllowed)
            .put("constraints", constraints)

        prefs.edit()
            .putString("last_constraint_report", report.toString().take(16000))
            .putLong("last_constraint_check_at", System.currentTimeMillis())
            .apply()

        val gate = chooseSystemGate(disabled, paired, backgroundRestricted, batteryExempt, notificationsAllowed)
        if (gate != null) notifyGate(app, gate.first, gate.second)
        return report
    }

    private fun chooseSystemGate(
        disabled: Boolean,
        paired: Boolean,
        backgroundRestricted: Boolean,
        batteryExempt: Boolean,
        notificationsAllowed: Boolean
    ): Pair<String, Intent>? {
        if (disabled) return null
        if (!paired) return "إكمال اقتران حكيم" to Intent(Intent.ACTION_MAIN).setClassName("ps.hakim.stable", "ps.hakim.phoneagent.MainActivity")
        if (backgroundRestricted || !batteryExempt) {
            return "رفع قيود الخلفية/البطارية عن حكيم" to Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= 26) {
            return "تفعيل إشعارات حكيم" to Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, "ps.hakim.stable")
        }
        return null
    }

    private fun notifyGate(context: Context, gate: String, intent: Intent) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val fingerprint = gate
            val last = prefs.getString("last_constraint_gate", "")
            val lastAt = prefs.getLong("last_constraint_gate_at", 0L)
            if (last == fingerprint && now - lastAt < 6L * 60L * 60L * 1000L) return

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, 31, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "قيود استمرارية حكيم", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION") android.app.Notification.Builder(context)
            }
            nm.notify(
                NOTIFICATION_ID,
                builder.setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("حكيم وجد قيدًا نظاميًا")
                    .setContentText(gate)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
            prefs.edit().putString("last_constraint_gate", fingerprint).putLong("last_constraint_gate_at", now).apply()
        } catch (_: Exception) {}
    }

    private fun hasInternet(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) { false }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
        else context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) { false }

    private fun isBackgroundRestricted(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false
        else context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    } catch (_: Exception) { false }

    private fun notificationsAllowed(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) true
        else context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    } catch (_: Exception) { true }
}
