package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimConnectionRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimFaultContainment.guard(app, "recovery_job", "periodic_watchdog") {
                    HakimConnectionResilience.recover(app, "periodic_watchdog")
                    HakimConstraintDoctor.run(app, "periodic_watchdog")
                    HakimSelfCheck.runAsync(app)
                    HakimSelfImprovementLoop.scheduleEvaluation(applicationContext, "periodic_watchdog")
                }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
