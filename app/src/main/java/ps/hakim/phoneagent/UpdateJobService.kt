package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                val app = applicationContext
                HakimFaultContainment.guard(app, "auto_update_job", "periodic_check") {
                    AutoUpdater.checkNow(app)
                }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
