package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.SystemClock

class HakimConnectionRecoveryJobService : JobService() {
    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val existing = worker
        if (existing?.isAlive == true) {
            recordState("coalesced", 0L, null)
            return false
        }

        val startedElapsed = SystemClock.elapsedRealtime()
        val startedWall = System.currentTimeMillis()
        val thread = Thread {
            var failure: Throwable? = null
            try {
                runCatching {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                }
                recordState("running", 0L, null, startedWall)

                val app = applicationContext
                HakimConnectionResilience.recover(app, "periodic_watchdog")
                if (Thread.currentThread().isInterrupted) return@Thread

                val safeRecovery = HakimCrashShield.shouldSuppressProactiveResume(app)

                // نبضة الصحة لا يجوز أن تحبس JobScheduler بانتظار الشبكة أو حساب البصمة.
                HakimHealthBeacon.sendAsync(
                    app,
                    if (safeRecovery) "periodic_watchdog_safe_recovery" else "periodic_watchdog"
                )
                if (Thread.currentThread().isInterrupted) return@Thread

                if (!safeRecovery &&
                    HakimResourceGovernor.canRunNonEssentialBackground(app)
                ) {
                    HakimConstraintDoctor.run(app, "periodic_watchdog")
                    if (Thread.currentThread().isInterrupted) return@Thread
                    HakimSelfCheck.runAsync(app)
                }
            } catch (t: Throwable) {
                failure = t
            } finally {
                val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
                val interrupted = Thread.currentThread().isInterrupted
                recordState(
                    if (interrupted) "stopped" else if (failure == null) "finished" else "failed",
                    elapsed,
                    failure,
                    startedWall
                )
                if (!interrupted) {
                    runCatching { jobFinished(params, false) }
                }
                if (worker === Thread.currentThread()) worker = null
            }
        }.apply {
            name = "HakimConnectionWatchdog"
        }

        worker = thread
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        worker?.interrupt()
        worker = null
        recordState("stopped_by_system", 0L, null)
        return true
    }

    private fun recordState(
        state: String,
        durationMs: Long,
        failure: Throwable?,
        startedAt: Long = 0L
    ) {
        val edit = getSharedPreferences("hakim", MODE_PRIVATE).edit()
            .putString("last_connection_watchdog_state", state)
            .putLong("last_connection_watchdog_duration_ms", durationMs)
            .putLong("last_connection_watchdog_recorded_at", System.currentTimeMillis())

        if (startedAt > 0L) edit.putLong("last_connection_watchdog_started_at", startedAt)
        if (failure == null) {
            edit.remove("last_connection_watchdog_error")
        } else {
            edit.putString("last_connection_watchdog_error", failure.javaClass.simpleName.take(120))
        }
        edit.apply()
    }
}
