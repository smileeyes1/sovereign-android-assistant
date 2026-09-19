package ps.hakim.phoneagent

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

class HakimConnectionRecoveryJobService : JobService() {
    companion object {
        private const val HARD_TIMEOUT_MS = 15_000L
    }

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var timeoutGuard: Thread? = null

    private val completion = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters?): Boolean {
        val existing = worker
        if (existing?.isAlive == true) {
            recordState("coalesced", 0L, null)
            return false
        }

        completion.set(false)
        val startedElapsed = SystemClock.elapsedRealtime()
        val startedWall = System.currentTimeMillis()

        lateinit var thread: Thread
        thread = Thread {
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

                HakimHealthBeacon.sendAsync(
                    app,
                    if (safeRecovery) "periodic_watchdog_safe_recovery" else "periodic_watchdog"
                )
                if (Thread.currentThread().isInterrupted) return@Thread

                if (!safeRecovery &&
                    HakimResourceGovernor.canRunNonEssentialBackground(app)
                ) {
                    // العمل الثانوي لا يجوز أن يحتجز JobScheduler.
                    HakimConstraintDoctor.runAsync(app, "periodic_watchdog")
                    HakimSelfCheck.runAsync(app)
                }
            } catch (t: Throwable) {
                failure = t
            } finally {
                val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
                val interrupted = Thread.currentThread().isInterrupted
                if (completion.compareAndSet(false, true)) {
                    recordState(
                        if (interrupted) "stopped" else if (failure == null) "finished" else "failed",
                        elapsed,
                        failure,
                        startedWall
                    )
                    timeoutGuard?.interrupt()
                    runCatching { jobFinished(params, false) }
                }
                if (worker === Thread.currentThread()) worker = null
            }
        }.apply {
            name = "HakimConnectionWatchdog"
        }

        val guard = Thread {
            try {
                Thread.sleep(HARD_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }

            if (completion.compareAndSet(false, true)) {
                val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
                thread.interrupt()
                recordState("timed_out", elapsed, null, startedWall)
                runCatching { jobFinished(params, false) }
            }
        }.apply {
            name = "HakimConnectionWatchdogTimeout"
            priority = Thread.NORM_PRIORITY
        }

        worker = thread
        timeoutGuard = guard
        thread.start()
        guard.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        completion.set(true)
        worker?.interrupt()
        timeoutGuard?.interrupt()
        worker = null
        timeoutGuard = null
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
