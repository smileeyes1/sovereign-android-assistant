package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimEvolutionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                HakimConstitution.install(applicationContext)
                HakimLearning.initialize(applicationContext)
                HakimProactiveEngine.initialize(applicationContext)
                HakimLearning.maintenance(applicationContext)
                val report = HakimSelfCheck.run(applicationContext)
                HakimLearning.recordHealth(applicationContext, report)
                HakimLearning.consolidateAdaptation(applicationContext, report.optString("status", "UNKNOWN"))
                HakimProactiveEngine.runSafeBackground(
                    applicationContext,
                    "evolution_job",
                    report.optString("status", "UNKNOWN")
                )
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
