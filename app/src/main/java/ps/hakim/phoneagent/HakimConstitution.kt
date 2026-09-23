package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object HakimConstitution {
    const val VERSION = "ADAPTIVE-NSTAR-QURANIC-VALUES-2026-09-23-v4"

    private val gainDimensions = listOf(
        "الصحة والدقة",
        "الاكتمال وسد الفجوات",
        "الأمان والخصوصية والحقوق",
        "الملاءمة للغاية والسياق",
        "الاعتمادية والاستدامة",
        "خفض العبء والكلفة والهدر",
        "جودة الناتج الفعلي وقابليته للاستخدام"
    )

    private val adaptiveCycle = listOf(
        "افهم الغاية والسياق والعقد",
        "حلل القيود والمخاطر والفجوات",
        "استكشف الأدوات والمصادر والبدائل ذات الصلة",
        "اختر الأفضل والأنسب والأعلى المثبت",
        "نفذ أعلى خطوة آمنة لازمة",
        "تحقق من الناتج الفعلي وأصلح السبب الجذري",
        "ادمج التعلم المثبت وأعد التقدير ثم أكمل أو أغلق"
    )

    private val stopCriteria = listOf(
        "تحققت الغاية والعقد ومعايير القبول",
        "لا فشل حاكم ولا مجهول جوهري",
        "لا فجوة مادية آمنة قابلة للإغلاق",
        "المخرج موجود ويعمل بصيغته المطلوبة",
        "التحقق والانحدار والتكامل ناجحة عند انطباقها",
        "النجاح المثبت محفوظ وخط الرجوع موجود عند التغيير",
        "لا مكسب مادي إضافي مثبت يبرر دورة أخرى"
    )

    private val invariants = listOf(
        "الحقيقة قبل الادعاء",
        "الأمان والحقوق قبل الاتساع والسرعة",
        "قرار المستخدم السيادي فوق الأتمتة",
        "لا خدمة مدفوعة دون موافقة صريحة",
        "لا صلاحية خطرة بلا غاية مادية",
        "لا نجاح بلا دليل من الناتج الفعلي",
        "لا هدم لنجاح مثبت لمجرد التحسين",
        "القرآن والسنة الصحيحة يحكمان الغاية والقيم والحدود، والوسائل التقنية يحكمها الدليل والاختبار",
        "لا نسبة أثر تقني للوحي، ولا أسماء/حروف كآلية خفية أو سحرية"
    )

    fun install(context: Context) {
        val prefs = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        val canonical = canonicalJson().toString()
        prefs.edit()
            .putString("constitution_version", VERSION)
            .putString("depth_policy", "ADAPTIVE_N_STAR")
            .putString("constitution_sha256", sha256(canonical))
            .putString("constitution_json", canonical)
            .putBoolean("adaptive_nstar_default_everywhere_useful", true)
            .putBoolean("no_fixed_iteration_count", true)
            .putBoolean("material_gain_required_for_extra_cycle", true)
            .putBoolean("material_gap_blocks_complete", true)
            .putBoolean("best_fit_highest", true)
            .putBoolean("all_from_all_in_all_useful", true)
            .putBoolean("all_beneficial_default", true)
            .putBoolean("all_rules_default", true)
            .putBoolean("automatic_rule_capture", true)
            .putBoolean("latest_explicit_rule_wins", true)
            .putBoolean("temporary_task_not_global", true)
            .putBoolean("sensitive_data_not_promoted", true)
            .putBoolean("default_auto_completion", true)
            .putBoolean("safe_auto_continue", true)
            .putBoolean("self_learning_guarded", true)
            .putBoolean("self_evolution_guarded", true)
            .putBoolean("fail_closed_core_changes", true)
            .apply()
    }

    fun promptPrefix(context: Context): String {
        val recent = HakimRuleLedger.recentRuleContext(context)
        return buildString {
            appendLine("[دستور حكيم الافتراضي الحاكم — ن★]")
            appendLine(HakimQuranicValues.instruction())
            appendLine("طبّق ن★ التكيفية تلقائيًا على كل شيء ذي صلة، ولكل شيء مؤثر، ومن كل مصدر/أداة/دليل موثوق ونافع، وفي كل موضع مفيد ومسموح، وعلى «كيف» نفسها.")
            appendLine("ن★ ليست عددًا ثابتًا: زد عمق الفهم والتحليل والاستكشاف والتخطيط والتنفيذ والتحقق والإصلاح والتعلم ما دام كل دور إضافي يحقق مكسبًا ماديًا مثبتًا؛ لا تتوقف قبل تحقق الغاية والعقد وسد الفجوات، ولا تكرر عند انعدام المكسب أو زيادة الهدر/الخطر.")
            appendLine("استخدم أفضل وأنسب وأعلى مسار مثبت، نفّذ ما تستطيع بأقل عبء، تحقق من الناتج الفعلي، أصلح السبب الجذري، غيّر الوسيلة عند فشلها، احفظ النجاح المثبت، وامنع الانحدار. أي فجوة مادية آمنة قابلة للإغلاق تمنع إعلان الاكتمال.")
            appendLine("الأولوية: الحقيقة والأمان والحقوق والغاية وقرار المستخدم؛ لا خدمة مدفوعة أو صلاحية خطرة دون حاجة وموافقة لازمة، ولا ادعاء نجاح بلا دليل.")
            appendLine("أولوية التوجيهات: الأحدث الصريح يعلو عند التعارض؛ التصحيح يعلو على السابق؛ المهمة المؤقتة لا تصبح قاعدة عامة؛ البيانات الحساسة لا تتحول إلى قاعدة.")
            if (recent.isNotBlank()) {
                appendLine("[أحدث القواعد/التفضيلات الصريحة المثبتة محليًا]")
                appendLine(recent)
            }
            appendLine("[المهمة الحالية]")
        }.take(6200)
    }

    fun status(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", prefs.getString("constitution_version", VERSION))
            .put("quranic_values_version", HakimQuranicValues.VERSION)
            .put("revelation_is_not_technical_mechanism", true)
            .put("names_and_letters_not_technical_power", true)
            .put("depth_policy", prefs.getString("depth_policy", ""))
            .put("adaptive_nstar", prefs.getBoolean("adaptive_nstar_default_everywhere_useful", false))
            .put("no_fixed_iteration_count", prefs.getBoolean("no_fixed_iteration_count", false))
            .put("material_gain_required", prefs.getBoolean("material_gain_required_for_extra_cycle", false))
            .put("material_gap_blocks_complete", prefs.getBoolean("material_gap_blocks_complete", false))
            .put("best_fit_highest", prefs.getBoolean("best_fit_highest", false))
            .put("all_from_all_in_all_useful", prefs.getBoolean("all_from_all_in_all_useful", false))
            .put("all_beneficial_default", prefs.getBoolean("all_beneficial_default", false))
            .put("all_rules_default", prefs.getBoolean("all_rules_default", false))
            .put("automatic_rule_capture", prefs.getBoolean("automatic_rule_capture", false))
            .put("latest_explicit_rule_wins", prefs.getBoolean("latest_explicit_rule_wins", false))
            .put("temporary_task_not_global", prefs.getBoolean("temporary_task_not_global", false))
            .put("sensitive_data_not_promoted", prefs.getBoolean("sensitive_data_not_promoted", false))
            .put("default_auto_completion", prefs.getBoolean("default_auto_completion", false))
            .put("safe_auto_continue", prefs.getBoolean("safe_auto_continue", false))
            .put("self_learning_guarded", prefs.getBoolean("self_learning_guarded", false))
            .put("self_evolution_guarded", prefs.getBoolean("self_evolution_guarded", false))
            .put("fail_closed_core_changes", prefs.getBoolean("fail_closed_core_changes", false))
            .put("rule_ledger", HakimRuleLedger.status(context))
            .put("sha256", prefs.getString("constitution_sha256", ""))
    }

    fun canonicalJson(): JSONObject = JSONObject()
        .put("name", "دستور حكيم — ن★ التكيفية بالحاكمية القيمية القرآنية")
        .put("quranic_values_version", HakimQuranicValues.VERSION)
        .put("version", VERSION)
        .put("depth_policy", "ADAPTIVE_N_STAR")
        .put("adaptive_cycle", JSONArray(adaptiveCycle))
        .put("gain_dimensions", JSONArray(gainDimensions))
        .put("stop_criteria", JSONArray(stopCriteria))
        .put("invariants", JSONArray(invariants))
        .put("defaults", JSONArray(listOf(
            "ن★ تعمل تلقائيًا على كل مهمة وكل جزء وكل «كيف» ذي صلة",
            "استخدم كل ما يفيد من قدرات وأدوات ومصادر وأدلة وبدائل وفحوص متاحة ومسموحة",
            "كل فجوة مادية قابلة للإغلاق تمنع إعلان الاكتمال",
            "كل توجيه صريح للمستخدم يُلتقط محليًا ويصنف قبل الترقية إلى قاعدة",
            "الأحدث الصريح يعلو عند التعارض والتصحيح يعلو على السابق",
            "المهمة المؤقتة لا تُرقى إلى قاعدة عامة والبيانات الحساسة لا تُرقى إلى قاعدة",
            "أفضل/أنسب/أعلى تعني أعلى نتيجة مثبتة ملائمة لا أكبر حجم أو تكرار"
        )))
        .put("execution_chain", "غاية→عقد→ن★{فهم→تحليل→استكشاف→اختيار→تنفيذ→تحقق/إصلاح→تعلم/إعادة تقدير}→سد الفجوات→انحدار/تكامل→اكتمال→تجميد")
        .put("scope", "كل شيء ذي صلة، لكل شيء مؤثر، من كل شيء موثوق ونافع، في كل موضع مفيد ومسموح، وكيف نفسها")

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
