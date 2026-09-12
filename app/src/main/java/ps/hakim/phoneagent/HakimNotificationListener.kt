package ps.hakim.phoneagent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/** سجل إشعارات محلي محدود داخل حكيم؛ تُنقّح رموز التحقق الشائعة قبل الإرسال البعيد. */
class HakimNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val item = JSONObject()
            .put("package", sbn.packageName)
            .put("title", redact(extras.getCharSequence("android.title")?.toString().orEmpty()))
            .put("text", redact(extras.getCharSequence("android.text")?.toString().orEmpty()))
            .put("posted_at", sbn.postTime)
        synchronized(recent) {
            recent.addFirst(item)
            while (recent.size > 100) recent.removeLast()
        }
    }

    companion object {
        @Volatile var instance: HakimNotificationListener? = null
            private set
        private val recent = ArrayDeque<JSONObject>()

        fun isConnected(): Boolean = instance != null

        fun snapshot(): JSONArray = JSONArray().also { arr ->
            synchronized(recent) { recent.forEach { arr.put(it) } }
        }

        private fun redact(raw: String): String {
            var s = raw.take(1000)
            val context = s.lowercase()
            if (Regex("otp|one.?time|verification|security.?code|رمز.?التحقق|رمز.?الأمان|كود").containsMatchIn(context)) {
                s = s.replace(Regex("(?<!\\d)\\d{4,8}(?!\\d)"), "[رمز مخفي]")
            }
            return s
        }
    }
}
