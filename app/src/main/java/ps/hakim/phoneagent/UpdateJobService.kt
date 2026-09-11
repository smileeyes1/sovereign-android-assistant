package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try { AutoUpdater.checkNow(applicationContext) } catch (_: Exception) {}
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
