package ps.hakim.phoneagent

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * حارس خفيف داخل العملية. لا يحاول إبقاء الهاتف مستيقظًا باستمرار،
 * بل يراقب تقدم حلقة القناة ويعيدها فقط عند ثبوت الخمول.
 */
object HakimRelayWatchdog {
    private const val PERIOD_SECONDS = 90L
    private val installed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "hakim-relay-watchdog").apply { isDaemon = true }
    }
    @Volatile private var future: ScheduledFuture<*>? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        future = executor.scheduleWithFixedDelay({
            try {
                if (!HakimUnifiedRelay.isConfigured(app)) return@scheduleWithFixedDelay
                val restarted = HakimUnifiedRelay.ensureAlive(app, "process_watchdog")
                if (!HakimUnifiedRelay.isConnected()) {
                    HakimConnectionResilience.scheduleSoon(app, "watchdog_offline")
                }
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putLong("relay_watchdog_tick_at", System.currentTimeMillis())
                    .putBoolean("relay_watchdog_restarted", restarted)
                    .apply()
            } catch (e: Exception) {
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putString("relay_watchdog_error", e.javaClass.simpleName + ":" + e.message.orEmpty().take(180))
                    .apply()
            }
        }, 45L, PERIOD_SECONDS, TimeUnit.SECONDS)
    }
}
