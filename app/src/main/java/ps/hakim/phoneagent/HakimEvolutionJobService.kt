package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimEvolutionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimFaultContainment.guard(app, "evolution_job", "periodic_cycle") {
                    HakimConstitution.install(applicationContext)
                    HakimLearning.initialize(applicationContext)
                    HakimLearning.maintenance(applicationContext)
                    HakimValueContinuityEngine.resumePending(applicationContext)
                    HakimGoalSupervisor.resume(applicationContext)
                    HakimGoalExecutor.tick(applicationContext)
                    AutoUpdater.checkNow(applicationContext)
                    val report = HakimSelfCheck.run(applicationContext)
                    HakimLearning.recordHealth(applicationContext, report)
                }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
