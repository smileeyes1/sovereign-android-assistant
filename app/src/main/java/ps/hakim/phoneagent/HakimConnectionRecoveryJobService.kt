package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimConnectionRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimConnectionResilience.recover(app, "periodic_watchdog")
                val safeRecovery = HakimCrashShield.shouldSuppressProactiveResume(app)
                HakimHealthBeacon.sendNow(app, if (safeRecovery) "periodic_watchdog_safe_recovery" else "periodic_watchdog")
                if (!safeRecovery &&
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
