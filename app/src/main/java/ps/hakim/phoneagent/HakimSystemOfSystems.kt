package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** نظام الأنظمة: تركيب مؤقت ذكي من الأنظمة والقدرات الموثوقة بحسب المقصد والموارد والدليل. */
object HakimSystemOfSystems {
    const val VERSION = "HAKIM-SMART-SYSTEM-OF-SYSTEMS-2026-09-15-v5"

    enum class Unit(val title: String, val duty: String) {
        GOVERNANCE("الحاكمية", "يثبت العقد والحدود وترتيب الأولويات"),
        QURAN_SUNNAH("منهج القرآن والهدي النبوي", "يحكم الغاية والقيم والحدود الشرعية مع التثبت"),
        HUMAN_FIRST("الإنسان أولًا", "يحفظ الكرامة والرحمة وإكرام الضعيف والمستضعف والعادة الصالحة وسيادة المستخدم"),
        INDEPENDENCE("الاستقلال السيادي", "يمنع الارتهان لمزود/شبكة/أداة واحدة ويحفظ قابلية النقل والاستئناف"),
        INTENT("فهم المقصد", "يفهم أقل إشارة ويستعيد السياق الموثوق"),
        DECISION_MATRIX("المصفوفات الذكية", "تحول البدائل إلى قرار متعدد الأبعاد قابل للفحص دون شراء السلامة بالنقاط"),
        SMART_ALGORITHMS("الخوارزميات الذكية", "تختار أبسط خوارزمية كافية وتزيد الذكاء فقط عند مكسب مادي مثبت"),
        EXCELLENCE("محسن التفوق", "يقارن خط الأساس والبدائل معجميًا وعلى جبهة Pareto ويمنع الانحدار"),
        CAPABILITY_MESH("شبكة التفوق والقدرات", "تكتشف الأدوات والخدمات وتختار الأعلى وتجهز البدائل"),
        RESEARCH("البحث والدليل", "يجمع الأدلة ويقارن البدائل ويحدّث الواقع"),
        BROWSER("المتصفح", "ينفذ خطوات الويب المسموحة ويتحقق من الأثر"),
        FORMS("النماذج", "يعبئ الحقول غير الحساسة ضمن الثقة والسلطة"),
        FILES("الملفات", "ينشئ ويقرأ وينظم الملفات ضمن الصلاحيات"),
        COMMUNICATION("التواصل", "يجهز التواصل ويقف قبل الأثر العالي"),
        EDUCATION("التعليم", "يبني المهام التعليمية وفق السياق والمنهاج والقواعد"),
        VERIFICATION("التحقق", "يفحص النتيجة الفعلية والانحدار"),
        RECOVERY("التعافي", "يبدل المسار ويستعيد الحالة دون توسيع السلطة"),
        LEARNING("التعلم", "يثبت ما نجح ويعيد ترتيب البدائل الآمنة والعادات النافعة المثبتة"),
        RESOURCE("الموارد", "يحمي الأداء والبطارية والحرارة والذاكرة"),
        AUTHORITY("السلطة والأمان", "يمنع التوسع في الصلاحية أو كشف الأسرار أو الأثر العالي الصامت")
    }

    data class DerivedSystem(
        val goal: String,
        val units: List<Unit>,
        val protocol: List<String>,
        val executionMode: String,
        val resourceMode: String,
        val preferredCapabilities: List<String>,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("goal", goal)
            .put("units", JSONArray(units.map { it.name }))
            .put("protocol", JSONArray(protocol))
            .put("execution_mode", executionMode)
            .put("resource_mode", resourceMode)
            .put("preferred_capabilities", JSONArray(preferredCapabilities))
            .put("reason", reason)
            .put("ephemeral_derived_system", true)
            .put("inherits_governance", true)
            .put("inherits_sovereign_independence", true)
            .put("inherits_capability_mesh", true)
            .put("inherits_human_first_mercy_honor_good_habits", true)
            .put("inherits_smart_decision_matrix", true)
            .put("inherits_smart_algorithms", true)
            .put("inherits_excellence_optimizer", true)
            .put("smartness_requires_material_gain", true)
            .put("simple_algorithm_preferred_when_sufficient", true)
            .put("cannot_expand_authority", true)
            .put("cannot_mutate_code", true)
            .put("wip_one_under_pressure", true)
    }

    private val hakimProtocol = listOf(
        "و؟ الواقع: ماذا يحدث فعلًا الآن؟",
        "و؟ المقصد: ماذا يريد المستخدم حقًا؟",
        "و؟ القيود: ما الثوابت والمجهولات وحدود السلطة؟",
        "لِمَ؟ لماذا هذا المسار أعلى قيمة وأقل خطرًا وعبئًا وارتهانًا؟",
        "و؟ البدائل: ما أفضل البدائل والقدرات المشروعة والمتاحة؟",
        "و؟ الدليل: ما الذي يثبت الاختيار والنتيجة والاستقلال عن نقطة فشل واحدة؟",
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
            Unit.INDEPENDENCE,
            Unit.INTENT,
            Unit.DECISION_MATRIX,
            Unit.SMART_ALGORITHMS,
            Unit.EXCELLENCE,
            Unit.CAPABILITY_MESH,
            Unit.RESOURCE,
            Unit.AUTHORITY
        )

        if (containsAny(s, "ابحث", "قارن", "تحقق", "مصدر", "حديث", "آية", "احدث", "أحدث")) units += Unit.RESEARCH
        if (containsAny(s, "موقع", "متصفح", "رابط", "صفحة", "افتح", "سجل دخول")) units += Unit.BROWSER
        if (containsAny(s, "نموذج", "املأ", "عبئ", "ادخل البيانات", "استمارة")) units += Unit.FORMS
        if (containsAny(s, "ملف", "pdf", "وورد", "صورة", "تنزيل", "تحميل", "حفظ")) units += Unit.FILES
        if (containsAny(s, "رسالة", "بريد", "واتساب", "ارسل", "أرسل", "رد")) units += Unit.COMMUNICATION
        if (containsAny(s, "طالب", "درس", "صف", "مدرسة", "منهاج", "تعليم", "رياضيات", "ورقة عمل")) units += Unit.EDUCATION
        units += Unit.VERIFICATION
        units += Unit.RECOVERY
        units += Unit.LEARNING

        val resources = HakimResourceGovernor.snapshot(context)
        val ranked = HakimCapabilityMesh.rank(context, goal, if (resources.mode == HakimResourceGovernor.Mode.PRESSURE) 3 else 5)
        val decision = HakimDecisionMatrix.evaluate(goal)
        val execution = when (resources.mode) {
            HakimResourceGovernor.Mode.PRESSURE -> "SEQUENTIAL_WIP1_MINIMAL_BACKGROUND"
            HakimResourceGovernor.Mode.CONSERVE -> "SEQUENTIAL_WIP1_LIGHT_BACKGROUND"
            HakimResourceGovernor.Mode.PERFORMANCE -> "SEQUENTIAL_PRIMARY_WITH_SAFE_IO_OVERLAP"
            HakimResourceGovernor.Mode.BALANCED -> "SEQUENTIAL_PRIMARY"
        }
        val reason = "نظام ذكي منبثق من ${units.size} وحدات؛ قرار=${decision.mode}/${decision.score}; أعلى القدرات=${ranked.joinToString(",") { it.node.id }}؛ الموارد=${resources.mode}. الذكاء وسيلة لا غاية: لا كود ذاتي ولا صلاحيات جديدة ولا تعقيد بلا مكسب مادي مثبت."
        return DerivedSystem(
            goal = goal.take(1800),
            units = units.toList(),
            protocol = hakimProtocol,
            executionMode = execution,
            resourceMode = resources.mode.name,
            preferredCapabilities = ranked.map { it.node.id },
            reason = reason
        )
    }

    fun promptContext(context: Context, raw: String): String {
        val d = compose(context, raw)
        return buildString {
            appendLine("[نظام الأنظمة الذكي والأنظمة المنبثقة]")
            appendLine("أنشئ لكل مهمة نظامًا منبثقًا مؤقتًا من الأنظمة اللازمة فقط؛ لا تنشئ خدمة دائمة أو كودًا ذاتيًا ولا توسع الصلاحيات.")
            appendLine("المقصد=${d.goal}")
            appendLine("وضع التنفيذ=${d.executionMode}؛ وضع الموارد=${d.resourceMode}.")
            appendLine("الأنظمة النشطة:")
            d.units.forEach { appendLine("• ${it.title}: ${it.duty}") }
            appendLine("القدرات المفضلة بالترتيب: ${d.preferredCapabilities.joinToString(" ← ")}")
            appendLine("بروتوكول التشغيل الحاكم:")
            d.protocol.forEach { appendLine("• $it") }
            append(HakimHumanFirstPolicy.promptContext())
            append(HakimDecisionMatrix.promptContext(d.goal))
            append(HakimExcellenceOptimizer.promptContext())
            append(HakimSovereignIndependence.promptContext(context))
            append(HakimCapabilityMesh.promptContext(context, d.goal))
            appendLine("طبّق المنظومات الذكية والمصفوفات الذكية والخوارزميات الذكية على كل موضع يحقق فائدة مثبتة: الفهم، البحث، المفاضلة، التخطيط، التنفيذ، التحقق، التعافي، التعلم، والعادات. لا تضف طبقة ذكاء إذا كان المسار الأبسط يحقق نفس الغاية بجودة مساوية أو أعلى.")
            appendLine("اختيار الخوارزمية تكيفي لكن محكوم: ابدأ بالحتمية/الأبسط، ارفع العمق عند غموض أو مخاطر أو بدائل متعددة أو فشل، وارجع إلى LAST_VERIFIED_BASELINE عند الانحدار.")
            appendLine("كل نظام فرعي ذي صلة يرث ميثاق الإنسان أولًا: الرحمة، إصلاح القلب دون ادعاء الباطن، إكرام الضعيف والمستضعف، العادة الصالحة النافعة، وعدم استغلال الحاجة أو التوسع الصامت في السلطة.")
            appendLine("يجوز إنشاء أنظمة فرعية منطقية عند الحاجة، لكنها ترث العقد والحاكمية والسلطة والموارد وشبكة القدرات والاستقلال السيادي، وتبقى WIP=1 تحت الضغط.")
            appendLine("لا تعتبر كثرة الأنظمة أو الأدوات أو الخوارزميات جودة بحد ذاتها؛ فعّل أقل مجموعة تحقق الغاية بأعلى أثر صافٍ، ثم أضف فقط عند فجوة مادية مثبتة.")
        }.take(34000)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("system_of_systems", true)
        .put("smart_systems", true)
        .put("smart_decision_matrices", true)
        .put("smart_algorithms", true)
        .put("smartness_applies_to_every_materially_useful_layer", true)
        .put("simple_first_complexity_on_evidence", true)
        .put("derived_systems", true)
        .put("derived_systems_are_ephemeral_orchestration", true)
        .put("derived_systems_inherit_quran_sunnah", true)
        .put("derived_systems_inherit_human_first", true)
        .put("derived_systems_inherit_mercy_heart_reform_vulnerable_honor_good_habits", true)
        .put("derived_systems_inherit_smart_decision_matrix", true)
        .put("derived_systems_inherit_excellence_optimizer", true)
        .put("derived_systems_inherit_sovereign_independence", true)
        .put("derived_systems_inherit_authority_envelope", true)
        .put("derived_systems_inherit_resource_governor", true)
        .put("derived_systems_inherit_capability_mesh", true)
        .put("derived_systems_cannot_expand_authority", true)
        .put("derived_systems_cannot_mutate_code", true)
        .put("wip_one_under_resource_pressure", true)
        .put("protocol", JSONArray(hakimProtocol))
        .put("resource_mode", HakimResourceGovernor.snapshot(context).mode.name)
        .put("decision_matrix_dimensions", HakimDecisionMatrix.dimensions())
        .put("excellence_optimizer", HakimExcellenceOptimizer.status())
        .put("sovereign_independence", HakimSovereignIndependence.status(context))
        .put("capability_mesh", HakimCapabilityMesh.status(context))

    private fun containsAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(it) }
}
