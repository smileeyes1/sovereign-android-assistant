package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * طبقة استمرارية مرئية وخفيفة لمقاومة سياسات OEM التي تلغي JobScheduler في الخلفية.
 * لا تنشئ WebView ولا قناة شبكة جديدة ولا تطلب صلاحيات إضافية؛ وظيفتها إبقاء عملية
 * حكيم في حالة foreground مع نبض محلي يعيد تثبيت عقد الجدولة idempotent.
 */
class HakimContinuityService : Service() {
    companion object {
        private const val CHANNEL_ID = "hakim_continuity"
        private const val NOTIFICATION_ID = 31
        private const val TICK_MS = 60_000L

        fun ensure(context: Context) {
            val app = context.applicationContext
            try {
                val intent = Intent(app, HakimContinuityService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
                else app.startService(intent)
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putLong("continuity_foreground_requested_at", System.currentTimeMillis())
                    .remove("continuity_foreground_start_error")
                    .apply()
            } catch (e: Exception) {
                app.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
                    .putString("continuity_foreground_start_error", e.javaClass.simpleName.take(120))
                    .apply()
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            Thread {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
                val app = applicationContext
                runCatching { HakimConnectionResilience.schedule(app) }
                app.getSharedPreferences("hakim", MODE_PRIVATE).edit()
                    .putLong("continuity_foreground_tick_at", System.currentTimeMillis())
                    .apply()
            }.start()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching { HakimConnectionResilience.schedule(applicationContext) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { HakimConnectionResilience.schedule(applicationContext) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "استمرارية حكيم", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HakimAgentsChatActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("حكيم")
            .setContentText("الاستمرارية المحلية نشطة")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
