package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * نواة علوم وهندسة قابلة للفحص. لا تدّعي معرفة كل شيء؛ تختار منهج التحقق المناسب
 * للمجال، وتفصل القيم والغاية عن السببية العلمية والتقنية.
 */
object HakimScientificEngineeringKernel {
    const val VERSION = "SCI-ENG-KERNEL-2026-09-15-v1"

    enum class Domain { MATHEMATICS, PHYSICS, CHEMISTRY, ENGINEERING, SECURITY, SAFETY, GENERAL }

    data class Assessment(
        val domains: Set<Domain>,
        val requiredChecks: List<String>,
        val researchRequired: Boolean,
        val hazardous: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("domains", JSONArray(domains.map { it.name }))
            .put("required_checks", JSONArray(requiredChecks))
            .put("research_required", researchRequired)
            .put("hazardous", hazardous)
            .put("reason", reason)
    }

    fun assess(rawGoal: String): Assessment {
        val s = rawGoal.lowercase()
        val domains = linkedSetOf<Domain>()
        if (mathRegex.containsMatchIn(s)) domains += Domain.MATHEMATICS
        if (physicsRegex.containsMatchIn(s)) domains += Domain.PHYSICS
        if (chemRegex.containsMatchIn(s)) domains += Domain.CHEMISTRY
        if (engineeringRegex.containsMatchIn(s)) domains += Domain.ENGINEERING
        if (securityRegex.containsMatchIn(s)) domains += Domain.SECURITY
        if (safetyRegex.containsMatchIn(s)) domains += Domain.SAFETY
        if (domains.isEmpty()) domains += Domain.GENERAL
        val checks = linkedSetOf<String>()
        if (Domain.MATHEMATICS in domains) checks += listOf(
            "تعريف الرموز والافتراضات", "حل مستقل أو عكسي", "فحص الحدود والحالات الخاصة", "اتساق الوحدات عند وجودها"
        )
        if (Domain.PHYSICS in domains) checks += listOf(
            "تحليل الأبعاد والوحدات SI", "اختيار النموذج ونطاق صلاحيته", "قوانين الحفظ ذات الصلة", "تقدير عدم اليقين والحساسية"
        )
        if (Domain.CHEMISTRY in domains) checks += listOf(
            "موازنة المعادلات والحفظ الذري", "الستويكيومترية والوحدات", "تمييز البيانات التجريبية عن التقدير", "فحص مخاطر المواد والتفاعل"
        )
        if (Domain.ENGINEERING in domains) checks += listOf(
            "متطلبات وقيود قابلة للقياس", "بدائل ومفاضلات", "أنماط الفشل وحدود الأمان", "خطة اختبار وقبول وتراجع"
        )
        if (Domain.SECURITY in domains) checks += listOf(
            "نموذج تهديد", "أقل صلاحية", "افتراض الاختراق والعزل", "سجل تدقيق وفشل مغلق وتعافٍ"
        )
        if (Domain.SAFETY in domains) checks += listOf(
            "تحديد الخطر", "شدة واحتمال التعرض", "ضوابط وقائية قبل التنفيذ", "شرط إيقاف وطوارئ"
        )
        if (Domain.GENERAL in domains) checks += listOf(
            "فصل الحقيقة عن الافتراض", "مصدر مناسب", "معيار نجاح", "تحقق مستقل"
        )

        val hazardous = hazardRegex.containsMatchIn(s)
        val research = currentRegex.containsMatchIn(s) || hazardous ||
            domains.any { it in setOf(Domain.PHYSICS, Domain.CHEMISTRY, Domain.ENGINEERING, Domain.SECURITY, Domain.SAFETY) }
        val reason = if (hazardous)
            "المهمة تحمل احتمال ضرر؛ المعرفة العلمية لا تلغي بوابة السلامة والسلطة"
        else "اختيرت فحوص علمية/هندسية بحسب المجال بدل إجابة عامة غير قابلة للتحقق"
        return Assessment(domains, checks.toList(), research, hazardous, reason)
    }

    fun promptContext(rawGoal: String): String {
        val a = assess(rawGoal)
        return buildString {
            appendLine("[نواة العلوم والهندسة — $VERSION]")
            appendLine("المجالات=${a.domains.joinToString(",") { it.name }}؛ البحث/التحقق الخارجي=${a.researchRequired}؛ خطر محتمل=${a.hazardous}.")
            appendLine("الفحوص الإلزامية: ${a.requiredChecks.joinToString("؛ ")}")
            appendLine("لا تستبدل القياس أو المصدر أو التجربة بالدعاء/البركة، ولا تجعل الوحي سببًا تقنيًا خفيًا. القرآن والهدي النبوي الصحيح يحكمان الغاية والقيم والعدل والأمانة والرحمة والحدود، والوسائل العلمية تُثبت بالدليل.")
            appendLine("في المسائل العلمية: اذكر النموذج والافتراضات والوحدات ونطاق الصلاحية وعدم اليقين حيث يلزم؛ افحص النتيجة بطريقة مستقلة قبل الاعتماد.")
            appendLine("في الهندسة: صمّم للأعطال لا للحالة المثالية فقط؛ اختبر أسوأ حالة معقولة، والفشل المغلق، والتراجع، وقابلية الصيانة.")
            appendLine("في الأمن والسلامة: لا توسع الصلاحيات لتعويض نقص التصميم، ولا تنفذ خطوة خطرة لمجرد أنها ممكنة تقنيًا.")
        }.take(4200)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("mathematics", true)
        .put("physics", true)
        .put("chemistry", true)
        .put("engineering", true)
        .put("security", true)
        .put("safety", true)
        .put("dimensional_analysis", true)
        .put("uncertainty_calibration", true)
        .put("failure_modes", true)
        .put("threat_modeling", true)
        .put("fail_closed", true)
        .put("scientific_causation_separate_from_revelation", true)

    private val mathRegex = Regex("(?i)(رياضيات|معادلة|جبر|هندسة رياضية|احتمال|إحصاء|math|algebra|calculus|geometry|probability|statistics)")
    private val physicsRegex = Regex("(?i)(فيزياء|قوة|طاقة|سرعة|تسارع|كهرباء|حرارة|ضغط|physics|force|energy|velocity|acceleration|voltage|temperature)")
    private val chemRegex = Regex("(?i)(كيمياء|تفاعل|مادة|حمض|قاعدة|مول|تركيز|chemistry|reaction|acid|base|mole|concentration)")
    private val engineeringRegex = Regex("(?i)(هندسة|تصميم|بناء|معمار|ميكانيك|كهرباء|إلكترون|برمجيات|engineering|design|architecture|mechanical|electrical|software)")
    private val securityRegex = Regex("(?i)(أمن|امن|تهديد|تشفير|اختراق|صلاحية|حماية|security|threat|encryption|breach|privilege|hardening)")
    private val safetyRegex = Regex("(?i)(سلامة|خطر|ضرر|وقاية|طوارئ|safety|hazard|harm|prevention|emergency)")
    private val hazardRegex = Regex("(?i)(انفجار|سم|سام|حريق|ضغط عال|جهد عال|إشعاع|مادة خطرة|explosive|toxic|poison|fire|high voltage|radiation|hazardous)")
    private val currentRegex = Regex("(?i)(اليوم|الآن|حالي|أحدث|current|today|latest|حديث)")
}
