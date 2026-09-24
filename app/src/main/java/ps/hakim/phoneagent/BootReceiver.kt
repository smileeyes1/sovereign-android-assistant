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
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            HakimSelfImprovementLoop.onPackageReplaced(context)
        }
        HakimValueContinuityEngine.resumePending(context)
        HakimGoalSupervisor.resume(context)
        HakimGoalExecutor.tick(context)
        HakimSelfCheck.schedule(context)
        HakimExecutionFabric.recover(context, "boot_or_replace")
        HakimConnectionResilience.install(context)
        HakimNetworkGuardian.install(context)
        HakimSelfCheck.runAsync(context)
        HakimLocalPairing.reconnectAsync(context)

        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(context)
        if (disabled || (!legacyPaired && !securePaired)) return

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
