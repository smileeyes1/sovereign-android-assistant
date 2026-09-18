package ps.hakim.phoneagent

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // يثبت أولًا حتى تكون أي علة لاحقة قابلة للتشخيص محليًا بدل حلقة إغلاق صامتة.
        HakimCrashShield.install(this)

        // هذان حاكمان: إذا فشلا لا يجوز تشغيل تنفيذ غير محكوم.
        HakimQuranicInvariantKernel.requireInherited("app_start")
        HakimConstitution.install(this)

        // فشل مكوّن مساعد لا يجب أن يسقط واجهة حكيم كلها؛ يسجل محليًا ويُستعاد لاحقًا.
        HakimCrashShield.guardNonCritical(this, "learning_initialize") { HakimLearning.initialize(this) }
        HakimCrashShield.guardNonCritical(this, "proactive_initialize") { HakimProactiveEngine.initialize(this) }
        HakimCrashShield.guardNonCritical(this, "integration_install") { HakimIntegrationFabric.install(this) }
        HakimCrashShield.guardNonCritical(this, "ime_resilience_install") { HakimImeResilience.install(this) }
        HakimCrashShield.guardNonCritical(this, "ui_polish_install") { HakimUiPolish.install(this) }
        HakimCrashShield.guardNonCritical(this, "work_surface_install") { HakimWorkSurface.install(this) }
        HakimCrashShield.guardNonCritical(this, "restore_mission_state") { restoreActiveMissionState() }

        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        HakimCrashShield.guardNonCritical(this, "pairing_defaults") { PairingDefaults.ensure(prefs) }

        // لا ننشئ خيط شبكة دائمًا بلا إعداد فعلي؛ وأي فشل هنا لا يغلق المحادثة.
        HakimCrashShield.guardNonCritical(this, "relay_autostart") {
            if (HakimUnifiedRelay.isConfigured(this)) HakimUnifiedRelay.start(this)
        }
        startHakimIfPaired(prefs)

        // الجدولة والصيانة خدمات مساعدة؛ تبقى الواجهة قابلة للاستخدام حتى عند تعطل إحداها.
        HakimCrashShield.guardNonCritical(this, "connection_resilience_install") { HakimConnectionResilience.install(this) }
        HakimCrashShield.guardNonCritical(this, "auto_update_schedule") { AutoUpdater.schedule(this) }
        HakimCrashShield.guardNonCritical(this, "self_check_schedule") { HakimSelfCheck.schedule(this) }
        HakimCrashShield.guardNonCritical(this, "deferred_maintenance_schedule") { scheduleDeferredMaintenance() }
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
                runCatching { HakimResourceGovernor.markStartupMaintenance(app) }
            }.start()
        }, delay)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching { HakimResourceGovernor.noteTrimMemory(this, level) }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { HakimResourceGovernor.noteLowMemory(this) }
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
            prefs.edit().putString("last_autostart_error", e.javaClass.name.take(180)).apply()
        }
    }
}
