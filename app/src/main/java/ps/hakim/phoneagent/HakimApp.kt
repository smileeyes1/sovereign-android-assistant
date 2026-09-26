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
        HakimExecutionFabric.recover(this, "app_start")
        HakimConnectionResilience.install(this)
        HakimResilienceAlarmReceiver.schedule(this)
        HakimRelayWatchdog.install(this)
        HakimNetworkGuardian.install(this)
        HakimHealthBeacon.sendAsync(this, "app_start")
        HakimSelfCheck.schedule(this)
        HakimSelfCheck.runAsync(this)
        HakimConstraintDoctor.runAsync(this, "app_start")
        HakimSelfImprovementLoop.install(this)
        startHakimIfPaired(prefs)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            HakimResilienceAlarmReceiver.schedule(this, 2L * 60L * 1000L)
            HakimConnectionResilience.scheduleSoon(this, "trim_memory_" + level)
        }
    }

    private fun startHakimIfPaired(prefs: android.content.SharedPreferences) {
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(this)
        if (disabled || (!legacyPaired && !securePaired)) return
        try {
            val intent = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            prefs.edit().putString("last_autostart_error", e.message.orEmpty().take(300)).apply()
        }
    }
}
