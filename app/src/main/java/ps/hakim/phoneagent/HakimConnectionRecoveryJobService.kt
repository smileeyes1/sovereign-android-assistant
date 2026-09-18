package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimConnectionRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimConnectionResilience.recover(app, "periodic_watchdog")
                HakimHealthBeacon.sendNow(app, "periodic_watchdog")
                if (!HakimCrashShield.shouldSuppressProactiveResume(app) &&
                    HakimResourceGovernor.canRunNonEssentialBackground(app)
                ) {
                    HakimConstraintDoctor.run(app, "periodic_watchdog")
                    HakimSelfCheck.runAsync(app)
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
