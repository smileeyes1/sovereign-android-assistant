package ps.hakim.phoneagent

import android.app.Application
import android.content.Intent
import android.os.Build

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        startHakimIfPaired(prefs)
        AutoUpdater.schedule(this)
        AutoUpdater.startRealtimeListener(this)
        HakimSelfCheck.schedule(this)
        AutoUpdater.checkAsync(this)
        HakimSelfCheck.runAsync(this)
    }

    private fun startHakimIfPaired(prefs: android.content.SharedPreferences) {
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val paired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        if (disabled || !paired) return
        try {
            val intent = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            prefs.edit().putString("last_autostart_error", e.message.orEmpty().take(300)).apply()
        }
    }
}
