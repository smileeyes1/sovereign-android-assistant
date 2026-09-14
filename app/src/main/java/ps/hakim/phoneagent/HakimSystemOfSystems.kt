package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نظام الأنظمة لحكيم.
 * لا ينشئ كودًا ذاتيًا ولا خدمات دائمة جديدة؛ يولّد تركيبًا تشغيليًا مؤقتًا من الأنظمة الموثوقة بحسب المقصد.
 * كل نظام منبثق يرث القرآن والهدي النبوي والإنسان أولًا وغلاف السلطة وحاكم الموارد والتحقق.
 */
object HakimSystemOfSystems {
    const val VERSION = "HAKIM-SYSTEM-OF-SYSTEMS-2026-09-14-v1"

    enum class Unit(val title: String, val duty: String) {
        GOVERNANCE("الحاكمية", "يثبت العقد والحدود وترتيب الأولويات"),
        QURAN_SUNNAH("منهج القرآن والهدي النبوي", "يحكم الغاية والقيم والحدود الشرعية مع التثبت"),
        HUMAN_FIRST("الإنسان أولًا", "يحفظ الكرامة والرحمة وأقل عبء وسيادة المستخدم"),
        INTENT("فهم المقصد", "يفهم أقل إشارة ويستعيد السياق الموثوق"),
        RESEARCH("البحث والدليل", "يجمع الأدلة ويقارن البدائل ويحدّث الواقع"),
        BROWSER("المتصفح", "ينفذ خطوات الويب المسموحة ويتحقق من الأثر"),
        FORMS("النماذج", "يعبئ الحقول غير الحساسة ضمن الثقة والسلطة"),
        FILES("الملفات", "ينشئ ويقرأ وينظم الملفات ضمن الصلاحيات"),
        COMMUNICATION("التواصل", "يجهز التواصل ويقف قبل الأثر العالي"),
        EDUCATION("التعليم", "يبني المهام التعليمية وفق السياق والمنهاج والقواعد"),
        VERIFICATION("التحقق", "يفحص النتيجة الفعلية والانحدار"),
        RECOVERY("التعافي", "يبدل المسار ويستعيد الحالة دون توسيع السلطة"),
        LEARNING("التعلم", "يثبت ما نجح ويعيد ترتيب البدائل الآمنة"),
        RESOURCE("الموارد", "يحمي الأداء والبطارية والحرارة والذاكرة"),
        AUTHORITY("السلطة والأمان", "يمنع التوسع في الصلاحية أو كشف الأسرار أو الأثر العالي الصامت")
    }

    data class DerivedSystem(
        val goal: String,
        val units: List<Unit>,
        val protocol: List<String>,
        val executionMode: String,
        val resourceMode: String,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("goal", goal)
            .put("units", JSONArray(units.map { it.name }))
            .put("protocol", JSONArray(protocol))
            .put("execution_mode", executionMode)
            .put("resource_mode", resourceMode)
            .put("reason", reason)
            .put("ephemeral_derived_system", true)
            .put("inherits_governance", true)
            .put("cannot_expand_authority", true)
            .put("cannot_mutate_code", true)
            .put("wip_one_under_pressure", true)
    }

    private val hakimProtocol = listOf(
        "و؟ الواقع: ماذا يحدث فعلًا الآن؟",
        "و؟ المقصد: ماذا يريد المستخدم حقًا؟",
        "و؟ القيود: ما الثوابت والمجهولات وحدود السلطة؟",
        "لِمَ؟ لماذا هذا المسار أعلى قيمة وأقل خطرًا وعبئًا؟",
        "و؟ البدائل: ما أفضل البدائل المشروعة والمتاحة؟",
        "و؟ الدليل: ما الذي يثبت الاختيار والنتيجة؟",
        "اعتمد: اختر الأعلى المثبت داخل العقد",
        "أصلح: عالج السبب الجذري لا العرض فقط",
        "أكمل: واصل تلقائيًا ما دام هناك مكسب مادي آمن",
        "هَيّا: نفّذ فعليًا ثم تحقق وتعلم وجمّد النجاح"
    )

    fun compose(context: Context, raw: String): DerivedSystem {
        HakimQuranicInvariantKernel.requireInherited("system_of_systems")
        val goal = HakimIntentContext.infer(context, raw).resolvedRequest.trim().ifBlank { "استمرار المهمة الحالية" }
        val s = goal.lowercase()
        val units = linkedSetOf(
            Unit.GOVERNANCE,
            Unit.QURAN_SUNNAH,
            Unit.HUMAN_FIRST,
            Unit.INTENT,
            Unit.RESOURCE,
            Unit.AUTHORITY
        )

        if (containsAny(s, "ابحث", "قارن", "تحقق", "مصدر", "حديث", "آية", "احدث", "أحدث")) units += Unit.RESEARCH
        if (containsAny(s, "موقع", "متصفح", "رابط", "صفحة", "افتح", "سجل دخول")) units += Unit.BROWSER
        if (containsAny(s, "نموذج", "املأ", "عبئ", "ادخل البيانات", "استمارة")) units += Unit.FORMS
        if (containsAny(s, "ملف", "pdf", "وورد", "صورة", "تنزيل", "تحميل", "حفظ")) units += Unit.FILES
        if (containsAny(s, "رسالة", "بريد", "واتساب", "ارسل", "أرسل", "رد")) units += Unit.COMMUNICATION
        if (containsAny(s, "طالب", "درس", "صف", "مدرسة", "منهاج", "تعليم", "رياضيات", "ورقة عمل")) units += Unit.EDUCATION

        // لا يوجد نظام منبثق بلا تحقق وتعافٍ وتعلم: هذه حلقات إغلاق لا إضافات شكلية.
        units += Unit.VERIFICATION
        units += Unit.RECOVERY
        units += Unit.LEARNING

        val resources = HakimResourceGovernor.snapshot(context)
        val execution = when (resources.mode) {
            HakimResourceGovernor.Mode.PRESSURE -> "SEQUENTIAL_WIP1_MINIMAL_BACKGROUND"
            HakimResourceGovernor.Mode.CONSERVE -> "SEQUENTIAL_WIP1_LIGHT_BACKGROUND"
            HakimResourceGovernor.Mode.PERFORMANCE -> "SEQUENTIAL_PRIMARY_WITH_SAFE_IO_OVERLAP"
            HakimResourceGovernor.Mode.BALANCED -> "SEQUENTIAL_PRIMARY"
        }
        val reason = buildString {
            append("نظام منبثق مؤقت للمقصد الحالي من ")
            append(units.size)
            append(" أنظمة موثوقة؛ الوضع=")
            append(resources.mode.name)
            append(". لا كود ذاتي جديد ولا صلاحيات جديدة؛ التركيب فقط يتكيف مع المهمة والموارد.")
        }
        return DerivedSystem(
            goal = goal.take(1800),
            units = units.toList(),
            protocol = hakimProtocol,
            executionMode = execution,
            resourceMode = resources.mode.name,
            reason = reason
        )
    }

    fun promptContext(context: Context, raw: String): String {
        val d = compose(context, raw)
        return buildString {
            appendLine("[نظام الأنظمة والأنظمة المنبثقة]")
            appendLine("أنشئ لكل مهمة نظامًا منبثقًا مؤقتًا من الأنظمة اللازمة فقط؛ لا تنشئ خدمة دائمة أو كودًا ذاتيًا ولا توسع الصلاحيات.")
            appendLine("المقصد=${d.goal}")
            appendLine("وضع التنفيذ=${d.executionMode}؛ وضع الموارد=${d.resourceMode}.")
            appendLine("الأنظمة النشطة:")
            d.units.forEach { appendLine("• ${it.title}: ${it.duty}") }
            appendLine("بروتوكول التشغيل الحاكم:")
            d.protocol.forEach { appendLine("• $it") }
            appendLine("يجوز للنظام المنبثق إنشاء أنظمة فرعية منطقية عند الحاجة، لكنها ترث نفس العقد والحاكمية والسلطة والموارد، وتبقى داخل WIP=1 إذا كان الهاتف تحت ضغط.")
            appendLine("لا تعتبر كثرة الأنظمة جودة بحد ذاتها؛ فعّل أقل مجموعة تحقق الغاية بأعلى أثر صافٍ، ثم أضف نظامًا فقط عند وجود فجوة مادية مثبتة.")
        }.take(9000)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("system_of_systems", true)
        .put("derived_systems", true)
        .put("derived_systems_are_ephemeral_orchestration", true)
        .put("derived_systems_inherit_quran_sunnah", true)
        .put("derived_systems_inherit_human_first", true)
        .put("derived_systems_inherit_authority_envelope", true)
        .put("derived_systems_inherit_resource_governor", true)
        .put("derived_systems_cannot_expand_authority", true)
        .put("derived_systems_cannot_mutate_code", true)
        .put("wip_one_under_resource_pressure", true)
        .put("protocol", JSONArray(hakimProtocol))
        .put("resource_mode", HakimResourceGovernor.snapshot(context).mode.name)

    private fun containsAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(it) }
}
