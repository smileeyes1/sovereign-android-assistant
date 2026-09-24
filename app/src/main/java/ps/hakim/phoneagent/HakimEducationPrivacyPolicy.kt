package ps.hakim.phoneagent

import android.content.Context

/**
 * Education privacy guard.
 *
 * It does not try to infer identity. It blocks obvious high-risk student data from external
 * channels by default and gives managed schools an explicit override.
 */
object HakimEducationPrivacyPolicy {
    const val VERSION = "EDUCATION-PRIVACY-2026-09-24-v1"

    enum class Capability {
        EXTERNAL_AI,
        EXTERNAL_ATTACHMENT,
        WEB
    }

    fun blockReason(
        context: Context,
        capability: Capability,
        prompt: String,
        hasAttachments: Boolean = false
    ): String? {
        val role = HakimEducationProfile.current(context)
        val enterprise = HakimEnterprisePolicy.current(context)

        if (
            role == HakimEducationProfile.Role.STUDENT &&
            capability == Capability.EXTERNAL_AI &&
            !enterprise.studentExternalAiAllowed
        ) {
            return "وضع الطالب يحافظ على العمل محليًا افتراضيًا؛ لم تسمح سياسة المؤسسة باستخدام ذكاء خارجي."
        }

        if (
            role == HakimEducationProfile.Role.STUDENT &&
            capability == Capability.EXTERNAL_ATTACHMENT &&
            hasAttachments &&
            !enterprise.studentExternalAttachmentsAllowed
        ) {
            return "وضع الطالب لا يرسل المرفقات إلى خدمات خارجية افتراضيًا."
        }

        if (
            capability != Capability.WEB &&
            containsProtectedStudentData(prompt) &&
            !enterprise.externalStudentDataAllowed
        ) {
            return "يبدو أن الطلب يتضمن بيانات طالب حساسة؛ أبقيتها داخل الجهاز ولم أرسلها إلى خدمة خارجية."
        }

        return null
    }

    fun containsProtectedStudentData(text: String): Boolean {
        val q = text.trim()
        if (q.isBlank()) return false

        val labels = listOf(
            "رقم الهوية",
            "رقم هويه",
            "هوية الطالب",
            "هويه الطالب",
            "رقم الطالب",
            "الرقم الوطني",
            "كشف علامات",
            "كشف العلامات",
            "قائمة الطلبة",
            "اسماء الطلبة",
            "أسماء الطلبة"
        )
        if (labels.any { q.contains(it, ignoreCase = true) }) return true

        val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        val phone = Regex("""(?<!\d)(?:\+?970|0)?5[0-9]{8}(?!\d)""")
        val idNearLabel = Regex("""(?:هوية|هويه|رقم\s*الطالب)\D{0,12}\d{7,10}""")
        return email.containsMatchIn(q) || phone.containsMatchIn(q) || idNearLabel.containsMatchIn(q)
    }
}
