package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimConnectionRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                if (HakimConnectivityState.hasValidatedInternet(applicationContext)) {
                    HakimUnifiedRelay.pollCachedOnce(applicationContext)
                    HakimUnifiedRelay.flushOutboxAsync(applicationContext)
                }
                HakimConnectionResilience.recover(applicationContext, "periodic_watchdog")
                HakimConstraintDoctor.run(applicationContext, "periodic_watchdog")
                HakimSelfCheck.runAsync(applicationContext)
                HakimSelfImprovementLoop.scheduleEvaluation(applicationContext, "periodic_watchdog")
            } catch (_: Exception) {
            } finally {
                val fabric = HakimExecutionFabric.status(applicationContext)
                val shouldRetry = params?.jobId == HakimConnectionResilience.RETRY_JOB_ID &&
                    !fabric.optBoolean("online") &&
                    fabric.optString("state") !in setOf("UNCONFIGURED", "DISABLED_BY_USER")
                jobFinished(params, shouldRetry)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
