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
        HakimNetworkGuardian.install(this)
        HakimHealthBeacon.sendAsync(this, "app_start")
        HakimSelfCheck.schedule(this)
        HakimSelfCheck.runAsync(this)
        HakimConstraintDoctor.runAsync(this, "app_start")
        HakimSelfImprovementLoop.install(this)
    }

    private fun startHakimIfPaired(prefs: android.content.SharedPreferences) {
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = !prefs.getString(HakimUnifiedRelay.KEY_TOPIC, "").isNullOrBlank() &&
            !prefs.getString(HakimUnifiedRelay.KEY_RESULT_TOPIC, "").isNullOrBlank() &&
            !prefs.getString(HakimUnifiedRelay.KEY_RELAY_KEY, "").isNullOrBlank()
        if (disabled || (!legacyPaired && !securePaired)) return
        try {
            val intent = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            prefs.edit().putString("last_autostart_error", e.message.orEmpty().take(300)).apply()
        }
    }
}
