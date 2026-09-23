package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * طبقة حكيم للأحياء والإنسان.
 *
 * النطاق المسموح افتراضيًا: التعليم، التنظيم، التفسير الحذر للبيانات،
 * المراقبة غير الغازية، المحاكاة، وتجهيز معلومات مفيدة للإنسان/المختص.
 *
 * لا تمنح استقلالًا علاجيًا ولا تفويضًا لتغيير الجسم أو القلب أو الدماغ.
 */
object HakimHumanBiology {
    const val VERSION = "HUMAN-BIOLOGY-2026-09-23-v1"

    enum class Mode {
        EDUCATION,
        WELLNESS,
        NON_INVASIVE_MONITORING,
        CLINICAL_DECISION_SUPPORT,
        DIRECT_INTERVENTION
    }

    private val systems = listOf(
        "الدماغ والجهاز العصبي",
        "القلب والدورة الدموية",
        "التنفس والرئتان",
        "الدم والمناعة",
        "الهضم والكبد والبنكرياس",
        "الكلى والمسالك",
        "الغدد والهرمونات والاستقلاب",
        "العضلات والعظام والمفاصل",
        "الجلد والأنسجة",
        "الحواس",
        "النوم والإيقاع اليومي",
        "النمو والتطور",
        "الصحة الإنجابية",
        "الوراثة والخلايا والأنسجة",
        "الميكروبيوم والعوامل البيئية"
    )

    data class BiologyPlan(
        val goal: String,
        val mode: Mode,
        val systems: List<String>,
        val autonomousClinicalAction: Boolean,
        val requiresQualifiedHumanGate: Boolean,
        val evidenceRequirements: List<String>
    ) {
        fun asJson(): JSONObject = JSONObject()
            .put("biology_version", VERSION)
            .put("goal", goal)
            .put("mode", mode.name)
            .put("systems", JSONArray(systems))
            .put("autonomous_clinical_action", autonomousClinicalAction)
            .put("requires_qualified_human_gate", requiresQualifiedHumanGate)
            .put("evidence_requirements", JSONArray(evidenceRequirements))
    }

    fun matches(raw: String): Boolean {
        val s = raw.lowercase()
        return listOf(
            "الأحياء", "احياء", "الجسم", "جسم الانسان", "الإنسان", "الانسان",
            "الدماغ", "المخ", "القلب", "الأعصاب", "الاعصاب", "الصحة", "طبي",
            "الطب", "خلايا", "خلية", "جين", "وراث", "مناعة", "عضلات", "عظام",
            "هرمون", "كبد", "كلى", "رئة", "رئتين", "دم", "ضغط", "نبض"
        ).any { s.contains(it) }
    }

    fun plan(context: Context, raw: String): BiologyPlan {
        val goal = raw.trim().replace(Regex("\\s+"), " ").take(1000)
        val s = goal.lowercase()
        val mode = when {
            listOf("حفز", "تحفيز", "صعق", "نبضة كهرب", "زرع", "حقن", "جرعة", "دواء", "جراحة",
                "تعديل جيني", "جينومي", "واجهة دماغ", "bci").any { s.contains(it) } -> Mode.DIRECT_INTERVENTION
            listOf("شخّص", "شخص", "علاج", "قرار طبي", "تشخيص").any { s.contains(it) } -> Mode.CLINICAL_DECISION_SUPPORT
            listOf("نبض", "ضغط", "حرارة", "نوم", "أكسجين", "مراقبة", "قياس").any { s.contains(it) } -> Mode.NON_INVASIVE_MONITORING
            listOf("لياقة", "عافية", "غذاء", "نشاط").any { s.contains(it) } -> Mode.WELLNESS
            else -> Mode.EDUCATION
        }

        val selected = systems.filter { system ->
            val normalized = system.lowercase()
            s.split(Regex("\\s+")).any { token -> token.length >= 4 && normalized.contains(token) }
        }.ifEmpty { systems }

        val clinical = mode == Mode.CLINICAL_DECISION_SUPPORT || mode == Mode.DIRECT_INTERVENTION
        val evidence = mutableListOf(
            "تمييز المعلومة العامة عن البيانات الشخصية",
            "ذكر مصدر وحداثة المعلومة عند القرار الصحي",
            "إظهار عدم اليقين وحدود القياس"
        )
        if (clinical) evidence += listOf(
            "عدم تحويل الاستدلال إلى تشخيص مؤكد بلا دليل سريري",
            "بوابة إنسان مؤهل قبل تدخل علاجي أو تشخيصي عالي الأثر",
            "سجل قابل للمراجعة للبيانات والاستدلال والقرار"
        )
        if (mode == Mode.DIRECT_INTERVENTION) evidence +=
            "لا تنفيذ ذاتي على الجسم أو القلب أو الدماغ دون جهاز طبي مناسب وتفويض مهني وموافقة صريحة"

        val plan = BiologyPlan(
            goal = if (goal.isBlank()) "غير محددة" else goal,
            mode = mode,
            systems = selected,
            autonomousClinicalAction = false,
            requiresQualifiedHumanGate = clinical,
            evidenceRequirements = evidence.distinct()
        )

        context.getSharedPreferences("hakim_human_biology", Context.MODE_PRIVATE)
            .edit()
            .putString("biology_version", VERSION)
            .putString("last_plan", plan.asJson().toString())
            .putLong("last_plan_at", System.currentTimeMillis())
            .apply()
        return plan
    }

    fun governedContext(context: Context, raw: String): String {
        val p = plan(context, raw)
        return buildString {
            appendLine("[حكيم — الأحياء والإنسان]")
            appendLine("الإصدار: $VERSION")
            appendLine("الوضع: ${p.mode.name}")
            appendLine("القاعدة: الفهم والمراقبة ودعم القرار ≠ تشخيص مؤكد ≠ علاج ≠ تدخل مباشر.")
            appendLine("الإنسان صاحب القرار؛ أي تدخل على القلب/الدماغ/الجسم يتطلب دليلًا مناسبًا وقناة مختصة وموافقة صريحة.")
            appendLine("الخصوصية: اجمع أقل قدر صحي لازم، ولا تُحوّل البيانات الصحية إلى ذاكرة عامة أو سجل غير لازم.")
            appendLine("[الأجهزة الحيوية المشمولة]")
            p.systems.forEach { appendLine("• $it") }
            appendLine("[متطلبات الدليل]")
            p.evidenceRequirements.forEach { appendLine("• $it") }
        }.take(7000)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences("hakim_human_biology", Context.MODE_PRIVATE)
        return JSONObject()
            .put("human_biology", true)
            .put("version", p.getString("biology_version", VERSION))
            .put("systems_count", systems.size)
            .put("covers_brain", true)
            .put("covers_heart", true)
            .put("covers_whole_body_systems", true)
            .put("autonomous_clinical_action", false)
            .put("direct_intervention_requires_qualified_gate", true)
            .put("health_data_minimization", true)
            .put("last_plan", p.getString("last_plan", ""))
            .put("last_plan_at", p.getLong("last_plan_at", 0L))
    }
}
