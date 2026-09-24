package ps.hakim.phoneagent

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * سياسة مؤسسة قابلة للإدارة عبر Android Enterprise / MDM.
 * عدم وجود Managed Config يعني الوضع الشخصي الافتراضي.
 */
object HakimEnterprisePolicy {
    const val VERSION = "ENTERPRISE-POLICY-2026-09-24-v1"

    data class Policy(
        val managed: Boolean,
        val externalAiAllowed: Boolean,
        val webAllowed: Boolean,
        val attachmentsAllowed: Boolean,
        val voiceAllowed: Boolean,
        val diagnosticsAllowed: Boolean,
        val studentExternalAiAllowed: Boolean,
        val studentExternalAttachmentsAllowed: Boolean,
        val externalStudentDataAllowed: Boolean
    )

    fun current(context: Context): Policy {
        val manager = context.getSystemService(RestrictionsManager::class.java)
        val b: Bundle = manager?.applicationRestrictions ?: Bundle.EMPTY
        val managed = !b.isEmpty
        return Policy(
            managed = managed,
            externalAiAllowed = b.getBoolean("allow_external_ai", true),
            webAllowed = b.getBoolean("allow_web", true),
            attachmentsAllowed = b.getBoolean("allow_attachments", true),
            voiceAllowed = b.getBoolean("allow_voice", true),
            diagnosticsAllowed = b.getBoolean("allow_diagnostics", false),
            studentExternalAiAllowed = b.getBoolean("allow_student_external_ai", false),
            studentExternalAttachmentsAllowed = b.getBoolean("allow_student_external_attachments", false),
            externalStudentDataAllowed = b.getBoolean("allow_external_student_data", false)
        )
    }


    fun forcedEducationRole(context: Context): HakimEducationProfile.Role? {
        val manager = context.getSystemService(RestrictionsManager::class.java)
        val b: Bundle = manager?.applicationRestrictions ?: Bundle.EMPTY
        if (b.isEmpty) return null
        val raw = b.getString("education_role", "").orEmpty().trim()
        if (raw.isBlank()) return null
        return HakimEducationProfile.Role.values().firstOrNull { it.wire == raw }
    }

    fun blockReason(context: Context, capability: String): String? {
        val p = current(context)
        if (!p.managed) return null
        return when (capability) {
            "external_ai" -> if (!p.externalAiAllowed) "منعت سياسة المؤسسة إرسال هذا الطلب إلى خدمة ذكاء خارجية." else null
            "web" -> if (!p.webAllowed) "منعت سياسة المؤسسة استخدام الويب لهذه المهمة." else null
            "attachments" -> if (!p.attachmentsAllowed) "منعت سياسة المؤسسة إرسال أو معالجة المرفقات لهذه المهمة." else null
            "voice" -> if (!p.voiceAllowed) "منعت سياسة المؤسسة الإدخال الصوتي." else null
            "diagnostics" -> if (!p.diagnosticsAllowed) "التشخيصات المتقدمة غير متاحة في سياسة المؤسسة." else null
            else -> null
        }
    }
}
