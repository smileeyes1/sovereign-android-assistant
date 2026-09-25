package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONObject

object HakimConnectionResilience {
    const val JOB_ID = 771208
    const val RETRY_JOB_ID = 771210
    private const val PERIOD_MS = 15L * 60L * 1000L
    private const val RETRY_MIN_MS = 10_000L
    private const val CHANNEL_ID = "hakim_recovery"
    private const val NOTIFICATION_ID = 29

    @Volatile private var callbackInstalled = false

    fun install(context: Context) {
        val app = context.applicationContext
        schedule(app)
        installNetworkCallback(app)
        recover(app, "install")
    }

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, HakimConnectionRecoveryJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            scheduler.schedule(info)
        } catch (e: Exception) {
            prefs(context).edit().putString("last_recovery_schedule_error", safe(e.message)).apply()
        }
    }

    fun scheduleImmediate(context: Context, reason: String) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val info = JobInfo.Builder(
                RETRY_JOB_ID,
                ComponentName(context, HakimConnectionRecoveryJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(RETRY_MIN_MS)
                .setBackoffCriteria(RETRY_MIN_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(info)
            prefs(context).edit()
                .putString("last_immediate_recovery_reason", reason.take(80))
                .putLong("last_immediate_recovery_scheduled_at", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            prefs(context).edit().putString("last_immediate_recovery_error", safe(e.message)).apply()
        }
    }

    @Synchronized
    private fun installNetworkCallback(context: Context) {
        if (callbackInstalled) return
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    prefs(context).edit().putLong("last_network_available_at", System.currentTimeMillis()).apply()
                    if (HakimConnectivityState.hasValidatedInternet(context)) {
                        HakimUnifiedRelay.flushOutboxAsync(context)
                        recover(context, "validated_network_available")
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) {
                        prefs(context).edit().putLong("last_network_validated_at", System.currentTimeMillis()).apply()
                        HakimUnifiedRelay.flushOutboxAsync(context)
                        recover(context, "network_validated")
                    }
                }

                override fun onLost(network: Network) {
                    prefs(context).edit()
                        .putLong("last_network_lost_at", System.currentTimeMillis())
                        .putString("connection_recovery_state", "waiting_network")
                        .apply()
                }
            })
            callbackInstalled = true
        } catch (e: Exception) {
            prefs(context).edit().putString("last_network_callback_error", safe(e.message)).apply()
        }
    }

    fun recover(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val result = HakimExecutionFabric.recover(app, reason)
        val state = result.optString("state", "RECOVERING")
        val mapped = when (state) {
            "ONLINE" -> "healthy"
            "DEGRADED" -> "degraded"
            "OFFLINE_QUEUED" -> "offline_queued"
            "DISABLED_BY_USER" -> "disabled_by_user"
            "UNCONFIGURED" -> "unpaired"
            else -> "restart_requested"
        }
        prefs(app).edit()
            .putString("connection_recovery_state", mapped)
            .putString("last_recovery_reason", reason.take(80))
            .putLong("last_recovery_attempt_at", System.currentTimeMillis())
            .apply()

        if (!result.optBoolean("online") && result.optString("service_start") == "blocked") {
            prefs(app).edit().putString("connection_recovery_state", "start_blocked").apply()
            scheduleImmediate(app, "foreground_start_blocked")
            notifyRecoveryNeeded(app)
        }
        if (result.optBoolean("online")) {
            prefs(app).edit()
                .putLong("last_recovery_ok_at", System.currentTimeMillis())
                .remove("last_recovery_error")
                .apply()
        }
        return status(app)
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        val fabric = HakimExecutionFabric.status(context)
        return JSONObject()
            .put("state", p.getString("connection_recovery_state", "unknown"))
            .put("online", fabric.optBoolean("online"))
            .put("execution_fabric", fabric)
            .put("service_running", HakimService.running)
            .put("service_connected", HakimService.connected)
            .put("secure_relay_connected", HakimUnifiedRelay.isFreshConnected(context))
            .put("secure_relay_socket_open", HakimUnifiedRelay.isConnected())
            .put("validated_internet", HakimConnectivityState.hasValidatedInternet(context))
            .put("relay_outbox", HakimRelayOutbox.status(context))
            .put("local_adb_connected", p.getBoolean("local_adb_connected", false))
            .put("last_connected_at", p.getLong("last_connected_at", 0L))
            .put("last_recovery_attempt_at", p.getLong("last_recovery_attempt_at", 0L))
            .put("last_recovery_ok_at", p.getLong("last_recovery_ok_at", 0L))
            .put("last_recovery_reason", p.getString("last_recovery_reason", ""))
            .put("last_recovery_error", p.getString("last_recovery_error", ""))
            .put("last_network_available_at", p.getLong("last_network_available_at", 0L))
            .put("last_network_lost_at", p.getLong("last_network_lost_at", 0L))
            .put("last_immediate_recovery_scheduled_at", p.getLong("last_immediate_recovery_scheduled_at", 0L))
            .put("last_immediate_recovery_reason", p.getString("last_immediate_recovery_reason", ""))
    }

    private fun notifyRecoveryNeeded(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "استعادة اتصال حكيم", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val intent = Intent(context, CommandCenterActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 29, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION") android.app.Notification.Builder(context)
            }
            nm.notify(
                NOTIFICATION_ID,
                builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                    .setContentTitle("حكيم يعيد بناء قناة التنفيذ")
                    .setContentText("تعذر بدء الخدمة من الخلفية؛ سيستأنف حكيم عند أول فرصة يسمح بها أندرويد.")
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()
            )
        } catch (_: Exception) {}
    }

    private fun prefs(context: Context) = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
    private fun safe(value: String?): String = value.orEmpty().take(300)
}
