package ps.hakim.phoneagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        HakimConstitution.install(context)
        HakimLearning.initialize(context)
        HakimProactiveEngine.initialize(context)
        AutoUpdater.schedule(context)
        HakimSelfCheck.schedule(context)
        HakimConnectionResilience.install(context)

        val resources = HakimResourceGovernor.snapshot(context)
        if (resources.mode != HakimResourceGovernor.Mode.PRESSURE) {
            if (HakimResourceGovernor.canUseRealtimeBackgroundNetwork(context)) {
                AutoUpdater.startRealtimeListener(context)
                AutoUpdater.checkAsync(context)
            }
            HakimSelfCheck.runAsync(context)
        }

        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)

        // HC1 هو مسار التحكم الموحد الحالي. لا يجوز ربط استعادته بعد الإقلاع
        // بوجود إعدادات القناة القديمة؛ وإلا يصبح حكيم صامتًا حتى فتح التطبيق يدويًا.
        if (!disabled && HakimUnifiedRelay.isConfigured(context)) {
            HakimUnifiedRelay.start(context)
        }

        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val localPaired = prefs.getBoolean("local_adb_paired", false)
        if (!disabled && localPaired) HakimLocalPairing.reconnectAsync(context)
        if (disabled || !legacyPaired) return

        try {
            val service = Intent(context, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (e: Exception) {
            prefs.edit().putString("last_boot_start_error", e.message.orEmpty().take(300)).apply()
            HakimConnectionResilience.schedule(context)
        }
    }
}
