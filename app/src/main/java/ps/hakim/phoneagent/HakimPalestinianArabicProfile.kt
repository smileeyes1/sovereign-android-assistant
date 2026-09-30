package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * الهوية العربية الفلسطينية الافتراضية لحكيم.
 *
 * العربية الفصحى الطبيعية هي الأصل، والسياق الفلسطيني يُستخدم عندما يكون
 * ذا صلة بالمهمة. لا تُحوَّل اللهجة إلى افتراض عام، ولا تُختلق مصطلحات محلية.
 */
object HakimPalestinianArabicProfile {
    const val VERSION = "AR-PS-PROFILE-2026-09-30-v1"
    const val LOCALE_TAG = "ar-PS"
    const val COUNTRY_CONTEXT = "PS"

    enum class Register {
        EDUCATIONAL,
        FORMAL_ADMIN,
        FAMILY_MESSAGE,
        CONVERSATIONAL,
        TECHNICAL
    }

    private val principles = listOf(
        "العربية الفصحى الطبيعية الواضحة هي الأصل في التعليم والمراسلات والمخرجات الرسمية",
        "استخدم المصطلح الفلسطيني الرسمي أو المهني الموثوق عندما يكون للسياق المحلي أثر",
        "لا تستخدم اللهجة الفلسطينية إلا عندما يلائم المقام أو يطلبها المستخدم صراحة",
        "لا تختلق معلومة فلسطينية محلية غير متحققة لمجرد جعل النص محليًا",
        "راعِ المدرسة والوزارة والصف والعمر والسياق الفلسطيني في المواد التعليمية عند انطباقه",
        "الإنجليزية لا تظهر للمستخدم إذا وجد مقابل عربي واضح، مع إبقاء الأصل التقني عند الحاجة للدقة"
    )

    fun resolveRegister(raw: String): Register {
        val t = raw.trim()
        return when {
            Regex("""(?i)\b(api|json|url|http|https|kotlin|java|python|html|css|git|github|sha|adb)\b""").containsMatchIn(t) ->
                Register.TECHNICAL
            listOf("ولي الأمر", "ولي امر", "الأهل", "الاهل", "رسالة للأم", "رسالة للاب", "رسالة للأب").any { t.contains(it) } ->
                Register.FAMILY_MESSAGE
            listOf("مدير", "وزارة", "مديرية", "كتاب رسمي", "تعميم", "طلب رسمي", "إجازة", "اجازة").any { t.contains(it) } ->
                Register.FORMAL_ADMIN
            listOf("طالب", "الصف", "درس", "ورقة عمل", "نشاط", "تقويم", "منهاج", "رياضيات", "لغة عربية").any { t.contains(it) } ->
                Register.EDUCATIONAL
            else -> Register.CONVERSATIONAL
        }
    }

    fun promptContract(raw: String = ""): String {
        val register = resolveRegister(raw)
        val registerText = when (register) {
            Register.EDUCATIONAL -> "فصحى تعليمية فلسطينية واضحة ومناسبة للعمر"
            Register.FORMAL_ADMIN -> "فصحى رسمية فلسطينية مهنية ومباشرة"
            Register.FAMILY_MESSAGE -> "فصحى بسيطة دافئة وطبيعية ملائمة للتواصل المدرسي الفلسطيني"
            Register.CONVERSATIONAL -> "فصحى طبيعية واضحة، مع فلسطينية محكية فقط عندما يلائم المقام"
            Register.TECHNICAL -> "شرح عربي واضح مع إبقاء الرموز والمصطلحات التقنية الأصلية عند الضرورة"
        }
        return buildString {
            appendLine("[الهوية العربية الفلسطينية]")
            appendLine("المحلية الافتراضية: ar-PS. السياق المحلي الافتراضي: فلسطين عندما يكون ذا صلة.")
            appendLine("النمط المناسب لهذه المهمة: $registerText.")
            appendLine("العربية أصل المخرج؛ لا تترجم حرفيًا عن الإنجليزية، ولا تستخدم تعبيرًا غريبًا عن العربية الطبيعية.")
            appendLine("استعمل المصطلحات الفلسطينية الرسمية/المهنية الموثوقة عند انطباقها، ولا تختلق تفاصيل محلية غير متحققة.")
            appendLine("لا تظهر الإنجليزية للمستخدم إذا وجد مقابل عربي واضح؛ أبقِ الأصل التقني فقط عندما يخدم الدقة أو التشغيل.")
        }
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("locale", LOCALE_TAG)
        .put("country_context", COUNTRY_CONTEXT)
        .put("default_language", "العربية")
        .put("default_register", "العربية الفصحى الطبيعية")
        .put("palestinian_context_when_relevant", true)
        .put("dialect_only_when_relevant_or_requested", true)
        .put("no_unverified_local_claims", true)
        .put("principles", JSONArray(principles))
}
