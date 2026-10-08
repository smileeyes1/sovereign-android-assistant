package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HakimIntentEngine {
    data class IntentPlan(
        val raw: String,
        val intent: String,
        val goal: String,
        val route: String,
        val highImpact: Boolean,
        val needsUserGate: Boolean,
        val completion: List<String>,
        val nextAction: String,
        val depthPolicy: String = "ADAPTIVE_N_STAR"
    ) {
        fun asJson(): JSONObject = JSONObject()
            .put("intent", intent)
            .put("goal", goal)
            .put("route", route)
            .put("high_impact", highImpact)
            .put("needs_user_gate", needsUserGate)
            .put("depth_policy", depthPolicy)
            .put("completion", JSONArray(completion))
            .put("next_action", nextAction)
    }

    fun resolve(context: Context, raw: String): IntentPlan {
        val text = raw.trim()
        val s = text.lowercase()

        val highImpact = listOf(
            "ادفع", "شراء", "اشتر", "احذف", "احذف الحساب", "تحويل مالي", "حوّل المال", "كلمة المرور",
            "رمز التحقق", "otp", "بطاقة", "cvv", "صلاحية مدير", "إدارة الجهاز", "إرسال نهائي"
        ).any { s.contains(it) }

        val route = when {
            isUrlLike(text) -> "browser"
            listOf("ابحث", "افتح موقع", "تصفح", "سجل دخول", "صفحة", "رابط").any { s.contains(it) } -> "browser"
            listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق", "برمج", "كود", "مستودع", "github", "apk").any { s.contains(it) } -> "model_or_builder"
            listOf("صمم", "تصميم", "واجهة", "شعار", "صورة").any { s.contains(it) } -> "model_or_design"
            listOf("أرسل", "شارك", "تطبيق آخر", "واتساب", "بريد").any { s.contains(it) } -> "share_or_model"
            else -> "model"
        }

        val intent = when (route) {
            "browser" -> "تصفح/تنفيذ ويب"
            "model_or_builder" -> "بناء/برمجة/إنتاج"
            "model_or_design" -> "تصميم/إبداع"
            "share_or_model" -> "توجيه/إرسال"
            else -> "فهم وتنفيذ غاية عامة"
        }

        val completion = mutableListOf(
            "فهم الغاية والعقد دون تغيير المعنى",
            "تطبيق ن★ على كل جزء و«كيف» ذي صلة",
            "استخدام كل أداة/مصدر/دليل مفيد ومتاح ومسموح",
            "اختيار أفضل وأنسب وأعلى مسار مثبت",
            "تنفيذ كل الخطوات الآمنة المتاحة تلقائيًا",
            "التحقق من الناتج الفعلي وإصلاح السبب الجذري",
            "عدم ترك فجوة مادية قابلة للإغلاق أو خطوة لازمة على المستخدم",
            "فحص الانحدار والتكامل عند انطباقهما",
            "الإغلاق فقط عند تحقق الغاية أو وجود عائق حقيقي مثبت وعدم وجود مكسب مادي إضافي"
        )

        val nextAction = when {
            highImpact -> "نفّذ كل التحضير الآمن ثم اطلب الموافقة عند آخر خطوة عالية الأثر فقط"
            route == "browser" -> "افتح أو ابحث داخل حكيم ثم تابع وفق ن★ حتى تحقق الغاية"
            else -> "مرّر الغاية مع الدستور والسياق إلى أفضل نموذج أو أداة متاحة ومأذونة، مع بديل تلقائي، حتى الاكتمال"
        }

        val plan = IntentPlan(
            raw = text,
            intent = intent,
            goal = inferGoal(text),
            route = route,
            highImpact = highImpact,
            needsUserGate = highImpact,
            completion = completion,
            nextAction = nextAction
        )

        context.getSharedPreferences("hakim_intent", Context.MODE_PRIVATE)
            .edit()
            .putString("last_plan", plan.asJson().toString())
            .putLong("last_plan_at", System.currentTimeMillis())
            .putString("depth_policy", plan.depthPolicy)
            .apply()
        return plan
    }

    fun governedPrompt(context: Context, raw: String): String {
        val plan = resolve(context, raw)
        return buildString {
            append(HakimConstitution.promptPrefix(context))
            appendLine("[محرك النية]")
            appendLine("النية: ${plan.intent}")
            appendLine("الغاية: ${plan.goal}")
            appendLine("المسار المبدئي: ${plan.route}")
            appendLine("سياسة العمق: ن★ تكيفية بلا رقم ثابت؛ زد الدورة ما دام هناك مكسب مادي مثبت، ولا تتوقف قبل سد الفجوات وتحقيق العقد، ولا تكرر بلا أثر.")
            appendLine("قاعدة التشغيل: لا تتوقف عند الشرح أو خطوة وسيطة؛ أكمل كل الإجراءات الآمنة والمتاحة تلقائيًا، وغيّر الوسيلة عند فشلها، حتى تحقق الغاية أو يثبت عائق حقيقي.")
            appendLine("دورة ن★: افهم→حلل→استكشف كل المفيد→اختر الأفضل/الأنسب/الأعلى→نفّذ→تحقق/أصلح→تعلّم وأعد التقدير→أكمل.")
            appendLine("عند الفعل عالي الأثر: حضّر كل شيء ثم توقف فقط قبل الفعل النهائي الذي يتطلب موافقة المستخدم.")
            appendLine("[معايير الاكتمال]")
            plan.completion.forEach { appendLine("• $it") }
            appendLine("[أمر المستخدم]")
            append(raw.trim())
        }.take(12_000)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences("hakim_intent", Context.MODE_PRIVATE)
        return JSONObject()
            .put("intent_engine", true)
            .put("adaptive_nstar", true)
            .put("depth_policy", p.getString("depth_policy", "ADAPTIVE_N_STAR"))
            .put("default_auto_completion", true)
            .put("safe_auto_continue", true)
            .put("material_gap_blocks_complete", true)
            .put("high_impact_gate", true)
            .put("last_plan", p.getString("last_plan", ""))
            .put("last_plan_at", p.getLong("last_plan_at", 0L))
    }

    private fun inferGoal(text: String): String {
        if (text.isBlank()) return "غير محددة"
        return text.replace(Regex("\\s+"), " ").take(500)
    }

    private fun isUrlLike(text: String): Boolean {
        val q = text.trim()
        return q.startsWith("https://") || q.startsWith("http://") || (q.contains(".") && !q.contains(" "))
    }
}
