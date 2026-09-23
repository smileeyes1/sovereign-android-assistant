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
        HakimValueContinuityEngine.resumePending(context)
        HakimGoalSupervisor.resume(context)
        HakimGoalExecutor.tick(context)
        HakimSelfCheck.schedule(context)
        HakimConnectionResilience.install(context)
        HakimSelfCheck.runAsync(context)
        HakimLocalPairing.reconnectAsync(context)

        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val paired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        if (disabled || !paired) return

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
