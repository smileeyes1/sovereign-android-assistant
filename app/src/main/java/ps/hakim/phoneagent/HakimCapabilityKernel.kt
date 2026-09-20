package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * نواة القدرات: تفصل بين «القدرة» و«الإذن بالفعل».
 * لا تنفذ أدوات بنفسها؛ تصف القدرات، تقيم المخاطر، وتنتج قرارًا محليًا fail-closed.
 */
object HakimCapabilityKernel {
    private const val PREFS = "hakim_capability_kernel"

    data class Capability(
        val id: String,
        val title: String,
        val reversible: Boolean,
        val sensitive: Boolean,
        val externalSideEffect: Boolean
    )

    private val builtIns = listOf(
        Capability("observe_ui", "قراءة واجهة غير حساسة", true, false, false),
        Capability("navigate_ui", "تنقل محلي في الواجهة", true, false, false),
        Capability("type_text", "كتابة نص غير حساس", true, false, true),
        Capability("browser_open", "فتح رابط أو صفحة", true, false, true),
        Capability("local_file_read", "قراءة ملف محلي مأذون", true, false, false),
        Capability("local_file_write", "إنشاء/تعديل ملف محلي مأذون", true, false, true),
        Capability("build_candidate", "بناء مرشح داخل بيئة معزولة", true, false, false),
        Capability("install_candidate", "تثبيت/ترقية مرشح", false, true, true),
        Capability("send_external", "إرسال أو نشر خارجي", false, true, true),
        Capability("financial_action", "دفع أو تحويل مالي", false, true, true),
        Capability("grant_permission", "منح صلاحية جديدة", false, true, true)
    )

    fun catalog(): JSONArray = JSONArray().apply {
        builtIns.forEach { c ->
            put(JSONObject()
                .put("id", c.id)
                .put("title", c.title)
                .put("reversible", c.reversible)
                .put("sensitive", c.sensitive)
                .put("external_side_effect", c.externalSideEffect))
        }
    }

    /**
     * القرار: allow للأعمال المحلية منخفضة الأثر فقط، gate للحساسة/غير القابلة للعكس،
     * deny للقدرة غير المعروفة. لا يوجد fallback إلى سماح.
     */
    fun authorize(context: Context, capabilityId: String, target: String = "", detail: String = ""): JSONObject {
        val cap = builtIns.firstOrNull { it.id == capabilityId }
            ?: return decision(context, capabilityId, target, "deny", "unknown_capability", detail)

        val sensitiveProbe = (target + " " + detail).lowercase()
        val containsSecret = Regex(
            "password|passcode|otp|pin|cvv|cvc|secret|token|private.?key|كلمة.?المرور|رمز.?التحقق|رقم.?البطاقة|مفتاح.?خاص"
        ).containsMatchIn(sensitiveProbe)
        if (containsSecret) return decision(context, capabilityId, target, "gate", "sensitive_material", "[redacted]")

        val verdict = if (cap.sensitive || !cap.reversible) "gate" else "allow"
        val reason = if (verdict == "gate") "high_impact_or_irreversible" else "low_impact_reversible"
        return decision(context, capabilityId, target, verdict, reason, detail)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("capability_kernel", true)
            .put("fail_closed", true)
            .put("unknown_capability_denied", true)
            .put("sensitive_material_gated", true)
            .put("catalog_size", builtIns.size)
            .put("last_decision", p.getString("last_decision", ""))
            .put("last_decision_at", p.getLong("last_decision_at", 0L))
    }

    private fun decision(
        context: Context,
        capabilityId: String,
        target: String,
        verdict: String,
        reason: String,
        detail: String
    ): JSONObject {
        val safeTarget = target.take(240)
        val detailHash = sha256(detail.take(4000))
        val obj = JSONObject()
            .put("capability", capabilityId.take(80))
            .put("target", safeTarget)
            .put("verdict", verdict)
            .put("reason", reason)
            .put("detail_sha256", detailHash)
            .put("time", System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_decision", obj.toString())
            .putLong("last_decision_at", System.currentTimeMillis())
            .apply()
        return obj
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
