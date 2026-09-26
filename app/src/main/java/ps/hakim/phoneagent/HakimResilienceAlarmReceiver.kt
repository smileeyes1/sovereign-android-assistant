package ps.hakim.phoneagent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * طبقة إنعاش مستقلة عن JobScheduler والخدمة الأمامية.
 *
 * لا تستخدم exact alarms ولا تتجاوز قيود أندرويد؛ هدفها إعطاء النظام
 * فرصة إضافية لإيقاظ حكيم بعد قتل العملية أو أثناء Doze.
 */
class HakimResilienceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val reason = when (intent?.action) {
            Intent.ACTION_USER_PRESENT -> "user_present"
            Intent.ACTION_USER_UNLOCKED -> "user_unlocked"
            ACTION_ALARM -> "resilience_alarm"
            else -> "resilience_broadcast"
        }

        // ابدأ القناة المباشرة أولًا؛ لا تعتمد على نجاح بدء ForegroundService.
        HakimUnifiedRelay.ensureAlive(app, "alarm_receiver_$reason")
        HakimConnectionResilience.recover(app, "alarm_receiver_$reason")
        schedule(app)
    }

    companion object {
        const val ACTION_ALARM = "ps.hakim.stable.RESILIENCE_ALARM"
        private const val REQUEST_CODE = 771211
        private const val INTERVAL_MS = 12L * 60L * 1000L

        fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
            val app = context.applicationContext
            try {
                val alarmManager = app.getSystemService(AlarmManager::class.java)
                val intent = Intent(app, HakimResilienceAlarmReceiver::class.java)
                    .setAction(ACTION_ALARM)
                val pending = PendingIntent.getBroadcast(
                    app,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(60_000L)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putLong("resilience_alarm_scheduled_at", System.currentTimeMillis())
                    .putLong("resilience_alarm_delay_ms", delayMs)
                    .apply()
            } catch (e: Exception) {
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putString("resilience_alarm_error", e.javaClass.simpleName + ":" + e.message.orEmpty().take(180))
                    .apply()
            }
        }
    }
}
