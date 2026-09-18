package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimEvolutionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
                    .onFailure { HakimFaultLedger.record(applicationContext, "evolution_thread_priority", it, severity = HakimFaultLedger.Severity.INFO) }
                val app = applicationContext
                val resources = HakimResourceGovernor.snapshot(app)
                if (HakimCrashShield.shouldSuppressProactiveResume(app)) {
                    getSharedPreferences("hakim_governance", MODE_PRIVATE).edit()
                        .putString("last_evolution_state", "DEFERRED_CRASH_RECOVERY")
                        .putLong("last_evolution_at", System.currentTimeMillis())
                        .apply()
                    HakimHealthBeacon.sendAsync(app, "evolution_deferred_crash_recovery")
                    return@Thread
                }
                if (resources.mode == HakimResourceGovernor.Mode.PRESSURE) {
                    getSharedPreferences("hakim_governance", MODE_PRIVATE).edit()
                        .putString("last_evolution_state", "DEFERRED_RESOURCE_PRESSURE")
                        .putLong("last_evolution_at", System.currentTimeMillis())
                        .apply()
                    return@Thread
                }

                HakimConstitution.install(app)
                HakimLearning.initialize(app)
                HakimProactiveEngine.initialize(app)
                HakimSovereignOneKernel.recordSignal(
                    app,
                    HakimSovereignOneKernel.SignalKind.EVOLUTION,
                    "evolution_job",
                    "resource_mode=${resources.mode.name}",
                    100
                )
                HakimLearning.maintenance(app)

                if (resources.mode == HakimResourceGovernor.Mode.CONSERVE) {
                    HakimAdaptiveLearning.consolidate(app, "CONSERVE_MODE")
                    HakimProactiveEngine.runSafeBackground(app, "evolution_job_conserve", "CONSERVE_MODE")
                    getSharedPreferences("hakim_governance", MODE_PRIVATE).edit()
                        .putString("last_evolution_state", "LIGHT_PASS")
                        .putLong("last_evolution_at", System.currentTimeMillis())
                        .apply()
                } else {
                    val report = HakimSelfCheck.run(app)
                    HakimLearning.recordHealth(app, report)
                    HakimSovereignOneKernel.recordSignal(
                        app,
                        HakimSovereignOneKernel.SignalKind.HEALTH,
                        "self_check",
                        "status=${report.optString("status", "UNKNOWN")};failed=${report.optInt("failed")};warnings=${report.optInt("warnings")}",
                        if (report.optString("status") == "PASS") 100 else 80
                    )
                    HakimLearning.consolidateAdaptation(app, report.optString("status", "UNKNOWN"))
                    HakimProactiveEngine.runSafeBackground(
                        app,
                        "evolution_job",
                        report.optString("status", "UNKNOWN")
                    )
                    getSharedPreferences("hakim_governance", MODE_PRIVATE).edit()
                        .putString("last_evolution_state", "FULL_PASS")
                        .putLong("last_evolution_at", System.currentTimeMillis())
                        .apply()
                    HakimFaultLedger.resolve(app, "evolution_job", report.optString("status", "UNKNOWN"))
                }
            } catch (t: Throwable) {
                HakimFaultLedger.record(applicationContext, "evolution_job", t, severity = HakimFaultLedger.Severity.MATERIAL)
                getSharedPreferences("hakim_governance", MODE_PRIVATE).edit()
                    .putString("last_evolution_state", "FAILED_RECORDED")
                    .putLong("last_evolution_at", System.currentTimeMillis())
                    .apply()
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        HakimFaultLedger.record(applicationContext, "evolution_job_stopped", message = "job_stopped_by_system", severity = HakimFaultLedger.Severity.WARNING)
        return true
    }
}
