package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.util.concurrent.Executors

object HakimLocalPairing {
    const val ACTION_SUBMIT_PAIRING_CODE = "ps.hakim.stable.SUBMIT_LOCAL_ADB_PAIRING_CODE"
    const val REMOTE_INPUT_CODE = "hakim_pairing_code"
    private const val CHANNEL_ID = "hakim_local_adb_pairing"
    private const val NOTIFICATION_ID = 42042
    private val executor = Executors.newSingleThreadExecutor()

    fun arm(context: Context) {
        ensureChannel(context)
        context.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
            .putString("local_adb_state", "ARMED")
            .remove("local_adb_error")
            .apply()
        update(
            context,
            "رمز أندرويد لمرة واحدة: افتح «إقران الجهاز باستخدام رمز الاقتران»، اترك الشاشة مفتوحة، ثم أدخل الأرقام الـ٦ هنا",
            allowInput = true,
        )
    }

    fun openWirelessDebuggingSettings(context: Context) {
        arm(context)
        val direct = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(direct) }.getOrElse {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun submitCode(context: Context, code: String) {
        val normalized = code.filter { it.isDigit() }
        if (!normalized.matches(Regex("^[0-9]{6}$"))) {
            update(context, "الرمز يجب أن يكون ٦ أرقام فقط من نافذة اقتران أندرويد", allowInput = true)
            return
        }

        update(context, "جارٍ الاقتران المحلي داخل حكيم… أبقِ نافذة رمز أندرويد مفتوحة", allowInput = false)
        val app = context.applicationContext
        executor.execute {
            val result = HakimAdbConnectionManager.get(app).pairAndConnect(app, normalized)
            val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            if (result.paired && result.connected) {
                prefs.edit()
                    .putString("local_adb_state", "CONNECTED")
                    .putBoolean("local_adb_paired", true)
                    .putBoolean("local_adb_connected", true)
                    .putLong("local_adb_last_success", System.currentTimeMillis())
                    .remove("local_adb_error")
                    .apply()
                update(app, "تم التأسيس. سيعيد حكيم الاتصال تلقائيًا دون طلب الرمز عادةً", allowInput = false, ongoing = false)
            } else {
                prefs.edit()
                    .putString("local_adb_state", if (result.paired) "PAIRED_NOT_CONNECTED" else "PAIR_FAILED")
                    .putBoolean("local_adb_paired", result.paired)
                    .putBoolean("local_adb_connected", result.connected)
                    .putString("local_adb_error", result.error ?: "UNKNOWN")
                    .apply()
                update(app, "تعذر الاكتمال: ${friendly(result.error)} — افتح رمزًا جديدًا فقط إذا طلب أندرويد ذلك", allowInput = true)
            }
        }
    }

    fun reconnectAsync(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("local_adb_paired", false)) return
        executor.execute {
            val ok = HakimAdbConnectionManager.get(app).reconnect(app)
            prefs.edit()
                .putBoolean("local_adb_connected", ok)
                .putString("local_adb_state", if (ok) "CONNECTED" else "PAIRED_NOT_CONNECTED")
                .apply()
            if (ok) {
                prefs.edit().putLong("local_adb_last_success", System.currentTimeMillis()).apply()
            }
        }
    }

    fun currentSummary(context: Context): String {
        val prefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        return when (prefs.getString("local_adb_state", "IDLE")) {
            "CONNECTED" -> "الاتصال المحلي: متصل — لا يلزم رمز جديد"
            "PAIRED_NOT_CONNECTED" -> "الاتصال المحلي: مقترن، وحكيم يحاول التعافي تلقائيًا"
            "PAIR_FAILED" -> "الاتصال المحلي: لم يكتمل الاقتران؛ افتح رمز أندرويد جديدًا عند المحاولة"
            "ARMED" -> "الاتصال المحلي: بانتظار رمز أندرويد ذي ٦ أرقام لهذه المرة فقط"
            else -> "الاتصال المحلي: غير مهيأ بعد — التأسيس لمرة واحدة"
        }
    }

    private fun update(context: Context, text: String, allowInput: Boolean, ongoing: Boolean = true) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("حكيم — تأسيس الاتصال المحلي")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(ongoing)
            .setAutoCancel(false)

        if (allowInput) {
            val remoteInput = RemoteInput.Builder(REMOTE_INPUT_CODE)
                .setLabel("رمز الاقتران — ٦ أرقام من أندرويد")
                .build()
            val intent = Intent(context, HakimPairingReceiver::class.java).apply {
                action = ACTION_SUBMIT_PAIRING_CODE
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, 42042, intent, flags)
            val action = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "إدخال رمز الاقتران — مرة واحدة",
                pendingIntent,
            ).addRemoteInput(remoteInput).build()
            builder.addAction(action)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تأسيس اتصال حكيم المحلي",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "إدخال رمز الاقتران المحلي من أندرويد لمرة واحدة لتأسيس ADB داخل حكيم"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun friendly(error: String?): String = when {
        error == null -> "خطأ غير محدد"
        error.contains("PAIRING_SERVICE_NOT_FOUND") -> "لم تظهر خدمة الاقتران؛ أبقِ نافذة الرمز مفتوحة"
        error.contains("INVALID_PAIRING_CODE") -> "رمز الاقتران غير صالح"
        error.contains("Connect", ignoreCase = true) -> "فشل الاتصال بعد الاقتران"
        else -> "فشل الاقتران المحلي"
    }
}
