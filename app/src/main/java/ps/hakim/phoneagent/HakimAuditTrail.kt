package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * سجل تدقيق محلي منخفض البيانات.
 * لا يسجل نص الطلب أو محتوى الصفحة أو الأسرار أو المرفقات.
 */
object HakimAuditTrail {
    const val VERSION = "AUDIT-TRAIL-2026-09-24-v1"
    private const val FILE = "hakim_audit.jsonl"
    private const val MAX_LINES = 200

    @Synchronized
    fun record(context: Context, event: String, success: Boolean?) {
        val safeEvent = event
            .replace(Regex("[^A-Za-z0-9:_-]"), "_")
            .take(120)
            .ifBlank { "unknown" }

        val row = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("event", safeEvent)
            .put("success", success ?: JSONObject.NULL)
            .toString()

        val file = File(context.filesDir, FILE)
        val existing = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
        file.writeText((existing + row).joinToString("\n") + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    fun count(context: Context): Int {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.useLines { it.take(MAX_LINES + 1).count() } else 0
    }
}
