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
    private const val PERIOD_MS = 15L * 60L * 1000L
    private const val CHANNEL_ID = "hakim_recovery"
    private const val NOTIFICATION_ID = 29

    @Volatile private var callbackInstalled = false

    fun install(context: Context) {
        val app = context.applicationContext
        HakimQuranicInvariantKernel.requireInherited("connection_resilience_install")
        HakimIntegrationFabric.requireCore(app, "connection_resilience_install")
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

    @Synchronized
    private fun installNetworkCallback(context: Context) {
        if (callbackInstalled) return
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    prefs(context).edit().putLong("last_network_available_at", System.currentTimeMillis()).apply()
                    recover(context, "network_available")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        recover(context, "network_capabilities")
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
        HakimQuranicInvariantKernel.requireInherited("connection_resilience_recover")
        HakimIntegrationFabric.requireCore(app, "connection_resilience_recover")
        val p = prefs(app)
        PairingDefaults.ensure(p)

        val disabled = p.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = p.getString("command_topic", "").orEmpty().isNotBlank() &&
            p.getString("result_topic", "").orEmpty().isNotBlank() &&
            p.getString("auth_key", "").orEmpty().isNotBlank() &&
            p.getString("auth_key", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(app)
        val localPaired = p.getBoolean("local_adb_paired", false)
        val anyPaired = legacyPaired || securePaired || localPaired
        val now = System.currentTimeMillis()

        if (disabled) {
            p.edit().putString("connection_recovery_state", "disabled_by_user").apply()
            return state(app, "disabled_by_user", reason)
        }
        if (!anyPaired) {
            p.edit().putString("connection_recovery_state", "unpaired").apply()
            return state(app, "unpaired", reason)
        }

        // أي نتيجة حُفظت محليًا أثناء انقطاع مزود أو الشبكة تُعاد تلقائيًا.
        HakimSovereignResultChannel.flushAsync(app)

        // HC1 is independently recoverable and must never depend on legacy topics.
        if (securePaired) HakimUnifiedRelay.start(app)

        // Local ADB is an independent maintenance path; reconnect opportunistically.
        if (localPaired) HakimLocalPairing.reconnectAsync(app)

        val secureState = p.getString("secure_relay_state", "unknown").orEmpty()
        val legacyHealthy = legacyPaired && HakimService.running && HakimService.connected
        val secureHealthy = securePaired && secureState == "connected"

        if (legacyHealthy || secureHealthy) {
            val transport = when {
                legacyHealthy && secureHealthy -> "secure+legacy"
                secureHealthy -> "secure"
                else -> "legacy"
            }
            p.edit()
                .putString("connection_recovery_state", "healthy")
                .putString("connection_recovery_transport", transport)
                .putLong("last_recovery_ok_at", now)
                .remove("last_recovery_error")
                .apply()
            return state(app, "healthy", reason)
        }

        // Secure/local-only installations recover without ever starting the legacy WebView service.
        // In safe-recovery after crash/ANR, suppress legacy restart even if stale legacy fields remain.
        if (!legacyPaired || HakimCrashShield.shouldSuppressProactiveResume(app)) {
            val recoveryState = if (securePaired) "secure_reconnect_requested" else "local_reconnect_requested"
            p.edit()
                .putString("connection_recovery_state", recoveryState)
                .putString("connection_recovery_transport", if (securePaired) "secure" else "local")
                .putString("last_recovery_reason", reason.take(80))
                .putLong("last_recovery_attempt_at", now)
                .remove("last_recovery_error")
                .apply()
            return state(app, recoveryState, reason)
        }

        return try {
            val intent = Intent(app, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
            else app.startService(intent)
            p.edit()
                .putString("connection_recovery_state", "restart_requested")
                .putString("connection_recovery_transport", if (securePaired) "secure+legacy" else "legacy")
                .putString("last_recovery_reason", reason.take(80))
                .putLong("last_recovery_attempt_at", now)
                .remove("last_recovery_error")
                .apply()
            state(app, "restart_requested", reason)
        } catch (e: Exception) {
            val message = safe(e.message.ifNullOrBlank { e.javaClass.simpleName })
            p.edit()
                .putString("connection_recovery_state", "start_blocked")
                .putString("last_recovery_reason", reason.take(80))
                .putString("last_recovery_error", message)
                .putLong("last_recovery_attempt_at", now)
                .apply()
            notifyRecoveryNeeded(app)
            state(app, "start_blocked", reason).put("error", message)
        }
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val p = prefs(app)
        val legacyPaired = p.getString("command_topic", "").orEmpty().isNotBlank() &&
            p.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(app)
        val localPaired = p.getBoolean("local_adb_paired", false)
        return JSONObject()
            .put("integration_aware", true)
            .put("state", p.getString("connection_recovery_state", "unknown"))
            .put("transport", p.getString("connection_recovery_transport", ""))
            .put("legacy_paired", legacyPaired)
            .put("secure_paired", securePaired)
            .put("local_paired", localPaired)
            .put("secure_relay_state", p.getString("secure_relay_state", "unknown"))
            .put("service_running", HakimService.running)
            .put("service_connected", HakimService.connected)
            .put("last_connected_at", p.getLong("last_connected_at", 0L))
            .put("last_recovery_attempt_at", p.getLong("last_recovery_attempt_at", 0L))
            .put("last_recovery_ok_at", p.getLong("last_recovery_ok_at", 0L))
            .put("last_recovery_reason", p.getString("last_recovery_reason", ""))
            .put("last_recovery_error", p.getString("last_recovery_error", ""))
            .put("last_network_available_at", p.getLong("last_network_available_at", 0L))
            .put("last_network_lost_at", p.getLong("last_network_lost_at", 0L))
    }

    private fun state(context: Context, state: String, reason: String): JSONObject =
        status(context).put("state", state).put("reason", reason)

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
                    .setContentTitle("حكيم يحاول استعادة الاتصال")
                    .setContentText("منع أندرويد إعادة التشغيل من الخلفية. فتح حكيم يعيد المحاولة فورًا.")
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()
            )
        } catch (_: Exception) {}
    }

    private fun prefs(context: Context) = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
    private fun safe(value: String?): String = value.orEmpty().take(300)
    private fun String?.ifNullOrBlank(block: () -> String): String = if (this.isNullOrBlank()) block() else this
}
