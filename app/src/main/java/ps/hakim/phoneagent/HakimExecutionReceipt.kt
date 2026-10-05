package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * إيصال تنفيذ صغير لا يخزن محتوى النتيجة؛ يحفظ بصمتها وحكم التحقق فقط.
 */
object HakimExecutionReceipt {
    private const val PREFS = "hakim_capability_fabric"

    fun record(
        context: Context,
        requestId: String,
        capabilityId: String,
        state: String,
        result: JSONObject,
        verified: Boolean,
        verificationReason: String,
        startedAt: Long
    ): JSONObject {
        val finishedAt = System.currentTimeMillis()
        val receipt = JSONObject()
            .put("request_id", requestId.take(128))
            .put("capability", capabilityId.take(100))
            .put("state", state.take(48))
            .put("verified", verified)
            .put("verification_reason", verificationReason.take(120))
            .put("result_sha256", sha256(result.toString()))
            .put("started_at", startedAt)
            .put("finished_at", finishedAt)
            .put("duration_ms", (finishedAt - startedAt).coerceAtLeast(0L))

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_receipt", receipt.toString())
            .putLong("last_receipt_at", finishedAt)
            .apply()
        return receipt
    }

    fun last(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("last_receipt", "").orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
