package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimConnectionRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                HakimConnectionResilience.recover(applicationContext, "periodic_watchdog")
                HakimConstraintDoctor.run(applicationContext, "periodic_watchdog")
                HakimSelfCheck.runAsync(applicationContext)
                HakimSelfImprovementLoop.scheduleEvaluation(applicationContext, "periodic_watchdog")
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
