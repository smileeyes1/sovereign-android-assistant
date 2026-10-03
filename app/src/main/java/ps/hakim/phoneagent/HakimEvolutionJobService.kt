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
                    HakimAutonomousContinuation.pulse(applicationContext, "evolution_job")
                    AutoUpdater.checkNow(applicationContext)
                    val report = HakimSelfCheck.run(applicationContext)
                    HakimLearning.recordHealth(applicationContext, report)
                    if (HakimDevelopmentControlPlane.shouldSignal(applicationContext)) {
                        HakimHealthBeacon.sendNow(applicationContext, "development_request")
                    }
                }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
