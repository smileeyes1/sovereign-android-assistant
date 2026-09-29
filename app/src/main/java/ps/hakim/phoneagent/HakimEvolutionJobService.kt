package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimEvolutionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimFaultContainment.guard(app, "evolution_job", "periodic_cycle") {
                    HakimConstitution.install(app)
                    HakimLearning.initialize(app)
                    HakimLearning.maintenance(app)
                    HakimValueContinuityEngine.resumePending(app)
                    HakimGoalSupervisor.resume(app)
                    HakimGoalExecutor.tick(app)
                    val report = HakimSelfCheck.run(app)
                    HakimLearning.recordHealth(app, report)
                }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
