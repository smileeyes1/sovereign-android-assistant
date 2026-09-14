package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * يمنع ادعاء أن تطبيقًا هاتفيًا يساوي إنسانًا في كل شيء.
 * الهدف هو أقصى خدمة رقمية ممكنة: تنفيذ/تنسيق/تفويض ما تسمح به الأدوات والسلطة، مع كشف البوابات الواقعية بدل إخفائها.
 */
object HakimHumanCapabilityBoundary {
    enum class Class { LOCAL_DIGITAL, CONNECTED_DIGITAL, SYSTEM_GATED, PHYSICAL_OR_EXTERNAL, HUMAN_JUDGMENT_REQUIRED }

    data class Assessment(val clazz: Class, val canProceedAutomatically: Boolean, val reason: String)

    fun assess(raw: String): Assessment {
        val s = raw.lowercase()
        return when {
            Regex("(?i)(بصمة|وجهك|توقيع\\s*يدوي|اذهب|احمل|انقل\\s*جسد|قد\\s*السيارة|اسحب\\s*نقد|افحص\\s*جسديا|جسدي|مادي\\s*فعلي)").containsMatchIn(s) ->
                Assessment(Class.PHYSICAL_OR_EXTERNAL, false, "الفعل يحتاج إنسانًا/جهازًا ماديًا أو خدمة خارجية فعلية؛ حكيم يستطيع التحضير والتنسيق فقط حتى تتوفر الوسيلة")
            Regex("(?i)(كلمة\\s*مرور|otp|pin|cvv|رمز\\s*تحقق|بصمة\\s*دخول|صلاحية\\s*النظام|إذن\\s*النظام)").containsMatchIn(s) ->
                Assessment(Class.SYSTEM_GATED, false, "يلزم سر أو بوابة نظام لا يجوز اختلاقها أو تجاوزها")
            Regex("(?i)(قرار\\s*طبي|تشخيص|فتوى|حكم\\s*قضائي|عقد\\s*ملزم|إقرار\\s*قانوني)").containsMatchIn(s) ->
                Assessment(Class.HUMAN_JUDGMENT_REQUIRED, false, "يمكن لحكيم البحث والتحليل والتحضير، لكن القرار المتخصص/الملزم قد يتطلب صاحب اختصاص أو صاحب الحق")
            Regex("(?i)(موقع|ويب|بحث|بريد|رسالة|تحميل|تنزيل|شبكة|خدمة)").containsMatchIn(s) ->
                Assessment(Class.CONNECTED_DIGITAL, true, "قابل للتنفيذ/التنسيق رقميًا عند جاهزية الشبكة والخدمة والسلطة")
            else -> Assessment(Class.LOCAL_DIGITAL, true, "ابدأ محليًا ثم ارفع للقدرات المتصلة فقط عند الحاجة")
        }
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            appendLine("[حد القدرة الإنسانية الواقعي]")
            appendLine("التصنيف=${a.clazz}؛ ${a.reason}")
            appendLine("لا تدّع أن حكيم يستطيع كل ما يستطيع الإنسان الحقيقي جسديًا أو قانونيًا أو حسيًا. عظّم بدل ذلك كل ما يمكن تنفيذه أو تنسيقه أو تفويضه رقميًا ضمن الهاتف والخدمات والأدوات والصلاحيات المتاحة.")
            appendLine("إذا احتاجت الغاية إنسانًا أو جهازًا أو إذنًا أو سرًا: أنجز تلقائيًا كل التحضير الآمن الممكن، ثم توقف عند آخر بوابة لازمة فقط.")
        }.take(2200)
    }

    fun status(): JSONObject = JSONObject()
        .put("human_capability_boundary", true)
        .put("human_equivalence_claimed", false)
        .put("maximal_digital_assistance_goal", true)
        .put("physical_world_dependency_acknowledged", true)
        .put("system_permissions_cannot_be_bypassed", true)
        .put("missing_secrets_cannot_be_invented", true)
        .put("prepare_to_last_gate", true)
}
