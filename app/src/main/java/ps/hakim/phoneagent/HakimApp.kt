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
        val safeRecovery = HakimCrashShield.shouldSuppressProactiveResume(this)

        // هذان حاكمان: إذا فشلا لا يجوز تشغيل تنفيذ غير محكوم.
        HakimQuranicInvariantKernel.requireInherited("app_start")
        HakimConstitution.install(this)

        // Hooks الواجهة فقط تبقى على الخيط الرئيسي حتى تُسجل قبل إنشاء أول Activity.
        // لا تنفذ هذه الدوال شبكة أو فحصًا بنيويًا ثقيلًا.
        HakimCrashShield.guardNonCritical(this, "ime_resilience_install") { HakimImeResilience.install(this) }
        HakimCrashShield.guardNonCritical(this, "ui_polish_install") { HakimUiPolish.install(this) }
        HakimCrashShield.guardNonCritical(this, "work_surface_install") { HakimWorkSurface.install(this) }

        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        HakimCrashShield.guardNonCritical(this, "pairing_defaults") { PairingDefaults.ensure(prefs) }

        // كل تهيئة غير حرجة تُنقل خارج main thread حتى لا يسبب بدء Activity أو JobService مهلة ANR.
        startNonCriticalBootstrap(safeRecovery, prefs)
    }

    private fun startNonCriticalBootstrap(
        safeRecovery: Boolean,
        prefs: android.content.SharedPreferences
    ) {
        Thread {
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            val app = applicationContext

            HakimCrashShield.guardNonCritical(app, "learning_initialize") { HakimLearning.initialize(app) }
            HakimCrashShield.guardNonCritical(app, "proactive_initialize") { HakimProactiveEngine.initialize(app) }
            HakimCrashShield.guardNonCritical(app, "integration_install") { HakimIntegrationFabric.install(app) }
            HakimCrashShield.guardNonCritical(app, "restore_mission_state") { restoreActiveMissionState() }

            HakimCrashShield.guardNonCritical(app, "relay_autostart") {
                if (HakimUnifiedRelay.isConfigured(app)) HakimUnifiedRelay.start(app)
            }
            if (!safeRecovery) {
                HakimCrashShield.guardNonCritical(app, "legacy_browser_autostart") {
                    startLegacyBrowserIfPaired(prefs)
                }
            }

            HakimCrashShield.guardNonCritical(app, "startup_health") {
                HakimHealthBeacon.sendAsync(app, if (safeRecovery) "safe_recovery_start" else "app_start")
            }

            HakimCrashShield.guardNonCritical(app, "connection_resilience_install") { HakimConnectionResilience.install(app) }
            HakimCrashShield.guardNonCritical(app, "auto_update_schedule") { AutoUpdater.schedule(app) }
            HakimCrashShield.guardNonCritical(app, "self_check_schedule") { HakimSelfCheck.schedule(app) }
            HakimCrashShield.guardNonCritical(app, "deferred_maintenance_schedule") { scheduleDeferredMaintenance() }
        }.start()
    }

    private fun scheduleDeferredMaintenance() {
        if (HakimCrashShield.shouldSuppressProactiveResume(this)) return
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
                // اكتشاف نموذج محلي لا يخرج من loopback ولا يحتاج مفتاح API.
                runCatching { HakimLocalReasoningBridge.probe(app) }
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

    private fun startLegacyBrowserIfPaired(prefs: android.content.SharedPreferences) {
        val disabled = prefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("auth_key", "").orEmpty().isNotBlank()
        if (disabled || !legacyPaired) return
        try {
            val intent = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            prefs.edit().putString("last_autostart_error", e.javaClass.name.take(180)).apply()
        }
    }
}
