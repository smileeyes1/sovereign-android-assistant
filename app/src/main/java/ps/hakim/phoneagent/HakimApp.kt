package ps.hakim.phoneagent

import android.app.Application
import android.content.Intent
import android.os.Build

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        restoreActiveMissionState()
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        PairingDefaults.ensure(prefs)
        HakimUnifiedRelay.start(this)
        startHakimIfPaired(prefs)
        HakimConnectionResilience.install(this)
        HakimHealthBeacon.sendAsync(this, "app_start")
        AutoUpdater.schedule(this)
        AutoUpdater.startRealtimeListener(this)
        HakimSelfCheck.schedule(this)
        AutoUpdater.checkAsync(this)
        HakimSelfCheck.runAsync(this)
        HakimConstraintDoctor.runAsync(this, "app_start")
    }

    private fun restoreActiveMissionState() {
        val mission = HakimMissionLedger.active(this) ?: return
        if (mission.phase in setOf(
                HakimMissionLedger.Phase.WAITING_APPROVAL,
                HakimMissionLedger.Phase.WAITING_CREDENTIAL,
                HakimMissionLedger.Phase.WAITING_TRUST
            )) return
        HakimMissionLedger.progress(
            this,
            HakimMissionLedger.Phase.RECOVER,
            "استعيدت المهمة بعد تشغيل التطبيق؛ يلزم قراءة الحالة الحالية قبل أي استكمال"
        )
    }

    private fun startHakimIfPaired(prefs: android.content.SharedPreferences) {
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = !prefs.getString(HakimUnifiedRelay.KEY_TOPIC, "").isNullOrBlank() &&
            !prefs.getString(HakimUnifiedRelay.KEY_RESULT_URL, "").isNullOrBlank() &&
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
