package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService

class HakimNetworkGuardianJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                HakimNetworkGuardian.inspectAndProtect(applicationContext, "periodic_guardian")
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
