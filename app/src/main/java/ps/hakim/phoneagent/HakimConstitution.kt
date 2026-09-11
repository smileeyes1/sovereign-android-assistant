package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object HakimConstitution {
    const val VERSION = "SEVENFOLD-2026-09-11-v1"
    const val SEVEN = 7

    private val intelligence = listOf(
        "فهم الغاية والسياق",
        "تحليل القيود والمخاطر",
        "توليد البدائل",
        "مقارنة البدائل واختيار الأنسب",
        "التخطيط القابل للتنفيذ",
        "التحقق من الناتج الفعلي",
        "التعلم والتحسين مع منع الانحدار"
    )

    private val automatic = listOf(
        "استشعار الحالة والحاجة تلقائيًا",
        "اختيار الخطوة الآمنة التالية تلقائيًا",
        "تنفيذ المتاح تلقائيًا",
        "التحقق من النتيجة تلقائيًا",
        "الإصلاح والتعافي تلقائيًا",
        "حفظ التعلم والنجاح تلقائيًا",
        "الاستمرار أو الإغلاق عند اكتمال الغاية تلقائيًا"
    )

    private val benefit = listOf(
        "يفيد الغاية مباشرة",
        "يقلل العبء",
        "يرفع الصحة والدقة",
        "يرفع الاعتمادية",
        "يرفع الأمان والخصوصية",
        "يرفع الاستقلالية والاستدامة",
        "يحقق أفضل قيمة ضمن المجاني أولًا"
    )

    private val completion = listOf(
        "الغاية محفوظة",
        "المخرج المطلوب موجود",
        "القيود الحاكمة محفوظة",
        "لا فجوة جوهرية معروفة",
        "التحقق الفعلي ناجح",
        "خط الرجوع موجود عند التغيير",
        "النجاح المثبت محفوظ وقابل لإعادة الاستخدام"
    )

    private val invariants = listOf(
        "الحقيقة قبل الادعاء",
        "الأمان والحقوق قبل السرعة",
        "قرار المستخدم السيادي فوق الأتمتة",
        "لا خدمة مدفوعة دون موافقة صريحة",
        "لا صلاحية خطرة بلا غاية مادية",
        "لا نجاح بلا دليل من الناتج الفعلي",
        "لا هدم لنجاح مثبت لمجرد التحسين"
    )

    fun install(context: Context) {
        val prefs = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        val canonical = canonicalJson().toString()
        prefs.edit()
            .putString("constitution_version", VERSION)
            .putInt("intelligence_repetitions", SEVEN)
            .putInt("automatic_repetitions", SEVEN)
            .putInt("benefit_repetitions", SEVEN)
            .putInt("completion_gates", SEVEN)
            .putString("constitution_sha256", sha256(canonical))
            .putString("constitution_json", canonical)
            .putBoolean("sevenfold_default_everywhere_useful", true)
            .putBoolean("best_fit_highest", true)
            .putBoolean("all_from_all_in_all_useful", true)
            .putBoolean("self_learning_guarded", true)
            .putBoolean("self_evolution_guarded", true)
            .putBoolean("fail_closed_core_changes", true)
            .apply()
    }

    fun status(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", prefs.getString("constitution_version", VERSION))
            .put("intelligence_x7", prefs.getInt("intelligence_repetitions", 0) == SEVEN)
            .put("automatic_x7", prefs.getInt("automatic_repetitions", 0) == SEVEN)
            .put("benefit_x7", prefs.getInt("benefit_repetitions", 0) == SEVEN)
            .put("completion_x7", prefs.getInt("completion_gates", 0) == SEVEN)
            .put("sevenfold_default", prefs.getBoolean("sevenfold_default_everywhere_useful", false))
            .put("best_fit_highest", prefs.getBoolean("best_fit_highest", false))
            .put("all_from_all_in_all_useful", prefs.getBoolean("all_from_all_in_all_useful", false))
            .put("self_learning_guarded", prefs.getBoolean("self_learning_guarded", false))
            .put("self_evolution_guarded", prefs.getBoolean("self_evolution_guarded", false))
            .put("fail_closed_core_changes", prefs.getBoolean("fail_closed_core_changes", false))
            .put("sha256", prefs.getString("constitution_sha256", ""))
    }

    fun canonicalJson(): JSONObject = JSONObject()
        .put("name", "دستور حكيم السباعي الأعلى")
        .put("version", VERSION)
        .put("intelligence_x7", JSONArray(intelligence))
        .put("automatic_x7", JSONArray(automatic))
        .put("benefit_x7", JSONArray(benefit))
        .put("completion_x7", JSONArray(completion))
        .put("invariants_x7", JSONArray(invariants))
        .put("execution_chain", "غاية→فهم→تحليل→بدائل→اختيار→خطة→تنفيذ→تحقق→إصلاح→تعلم→منع انحدار→اكتمال")
        .put("scope", "كل شيء ذي صلة، من كل مصدر/أداة موثوقة نافعة، وفي كل موضع مفيد ومسموح")

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
