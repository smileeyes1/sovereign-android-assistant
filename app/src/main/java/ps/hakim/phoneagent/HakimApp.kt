package ps.hakim.phoneagent

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HakimQuranicInvariantKernel.requireInherited("app_start")
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        HakimProactiveEngine.initialize(this)
        HakimIntegrationFabric.install(this)
        HakimImeResilience.install(this)
        restoreActiveMissionState()

        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        PairingDefaults.ensure(prefs)

        // لا ننشئ خيط شبكة دائمًا بلا إعداد فعلي؛ هذه كانت كلفة بلا منفعة على الهاتف.
        if (HakimUnifiedRelay.isConfigured(this)) {
            HakimUnifiedRelay.start(this)
        }
        startHakimIfPaired(prefs)

        // الجدولة رخيصة؛ التنفيذ الفعلي يُحكم لاحقًا بحالة الموارد.
        HakimConnectionResilience.install(this)
        AutoUpdater.schedule(this)
        HakimSelfCheck.schedule(this)
        scheduleDeferredMaintenance()
    }

    private fun scheduleDeferredMaintenance() {
        if (!HakimResourceGovernor.shouldRunStartupMaintenance(this)) return
        val delay = HakimResourceGovernor.startupDeferralMs(this)
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
                val app = applicationContext
                if (!HakimResourceGovernor.canRunNonEssentialBackground(app)) return@Thread
                runCatching { HakimHealthBeacon.sendAsync(app, "app_start_deferred") }
                runCatching {
                    if (HakimResourceGovernor.canUseRealtimeBackgroundNetwork(app)) {
                        AutoUpdater.startRealtimeListener(app)
                    }
                }
                // تأسيس القرآن المحلي المتحقق يتم مرةً عند الحاجة فقط، على شبكة غير محسوبة
                // ومع موارد مناسبة؛ والثقة النهائية تبقى للبصمة الرسمية + فحص ١١٤/٦٢٣٦.
                runCatching { HakimQuranBootstrap.syncIfNeeded(app) }
                runCatching {
                    val report = HakimSelfCheck.run(app)
                    HakimLearning.recordHealth(app, report)
                }
                runCatching { HakimProactiveEngine.runSafeBackground(app, "app_start_deferred") }
                HakimResourceGovernor.markStartupMaintenance(app)
            }.start()
        }, delay)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        HakimResourceGovernor.noteTrimMemory(this, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        HakimResourceGovernor.noteLowMemory(this)
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
