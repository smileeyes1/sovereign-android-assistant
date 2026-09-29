package ps.hakim.phoneagent

import android.app.Application
import android.content.Intent
import android.os.Build

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        HakimFaultContainment.guard(this, "app_start", "constitution_install", critical = true) {
            HakimConstitution.install(this)
        }
        HakimFaultContainment.guard(this, "app_start", "learning_initialize") {
            HakimLearning.initialize(this)
        }
        HakimFaultContainment.guard(this, "app_start", "pairing_defaults", critical = true) {
            PairingDefaults.ensure(prefs)
        }
        HakimFaultContainment.guard(this, "app_start", "execution_fabric_recover") {
            HakimExecutionFabric.recover(this, "app_start")
        }
        HakimFaultContainment.guard(this, "app_start", "connection_resilience_install") {
            HakimConnectionResilience.install(this)
        }
        HakimFaultContainment.guard(this, "app_start", "alarm_schedule") {
            HakimResilienceAlarmReceiver.schedule(this)
        }
        HakimFaultContainment.guard(this, "app_start", "relay_watchdog_install") {
            HakimRelayWatchdog.install(this)
        }
        HakimFaultContainment.guard(this, "app_start", "network_guardian_install") {
            HakimNetworkGuardian.install(this)
        }
        HakimFaultContainment.guard(this, "app_start", "auto_update_schedule") {
            AutoUpdater.schedule(this)
        }
        HakimFaultContainment.guard(this, "app_start", "auto_update_realtime") {
            AutoUpdater.startRealtimeListener(this)
        }
        AutoUpdater.checkAsync(this)
        HakimHealthBeacon.sendAsync(this, "app_start")
        HakimSelfCheck.schedule(this)
        HakimSelfCheck.runAsync(this)
        HakimConstraintDoctor.runAsync(this, "app_start")
        HakimFaultContainment.guard(this, "app_start", "self_improvement_install") {
            HakimSelfImprovementLoop.install(this)
        }
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
        HakimFaultContainment.guard(this, "app_start", "foreground_service_start") {
            val intent = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
    }
}
