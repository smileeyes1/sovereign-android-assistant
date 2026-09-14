package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimEvolutionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
                val app = applicationContext
                val resources = HakimResourceGovernor.snapshot(app)
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
                }
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
