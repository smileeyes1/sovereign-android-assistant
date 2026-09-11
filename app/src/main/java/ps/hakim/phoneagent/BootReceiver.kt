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
        AutoUpdater.schedule(context)
        HakimSelfCheck.schedule(context)
        AutoUpdater.checkAsync(context)
        HakimSelfCheck.runAsync(context)

        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        val paired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        if (!paired) return

        try {
            val service = Intent(context, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (_: Exception) {
            // إذا منع النظام البدء لحظة الإقلاع، تبقى جداول الفحص والتحديث قائمة ويبدأ حكيم عند فتح التطبيق لاحقًا.
        }
    }
}
