package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HakimIntentEngine {
    private val TOL_TOKEN = Regex("(?<![\\p{L}\\p{N}_])تول★?(?![\\p{L}\\p{N}_])")
    private val ALL_TOKEN = Regex("كل\\s*شي[ءئ]★?")
    private const val ALL_CONTRACT = "قاعدة كل شيء★: داخل المقصد والتفويض فقط: و؟ ثم و؟ ثم و؟ ثم لِمَ؟ ثم و؟ ثم و؟؛ اكتشف وتحقق وخطط ونفذ واختبر الأثر وانقد وأصلح وغيّر الوسيلة وأعد الاختبار وأكمل واعتمد واحفظ وثبت واحم من الانحدار واستأنف حتى الأثر المثبت. بعد كل مرحلة اسأل ذاتيًا ماذا أيضًا يلزم لتحقيق المقصد أو حمايته أو تحسينه ماديًا، ونفذ كل فعل مأذون ذي قيمة موجبة. لا نجاح بلا أثر، لا WAIT بلا شرط استئناف، لا GATE بلا دليل، ولا توسع للصلاحية أو الخطر أو البيانات أو الكلفة."
    private const val TOL_CONTRACT = "قاعدة تول★: المقصد وسياقه المرتبط فقط؛ استعد آخر نجاح مثبت، ثم تثبت وخطط ونفذ واختبر الأثر وأصلح أو غير المسار وأعد الاختبار واحفظ النجاح وواصل حتى معيار القبول. الاستمرار فقط مع قيمة صافية موجبة. لا توسع المقصد أو الصلاحيات أو الكلفة أو البيانات أو المخاطر بسبب تول. الحساس وغير القابل للعكس والصلاحية الجديدة خلف بوابة موافقة. لا نجاح بلا دليل من موضع الأثر. النجاح المثبت خط أساس محمي. المقصد للمستخدم، الكيفية لحكيم داخل المأذون، والأثر المثبت هو الحكم."

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
        val tolActive = TOL_TOKEN.containsMatchIn(text)
        val allActive = ALL_TOKEN.containsMatchIn(text)
        val scopedText = if (tolActive) TOL_TOKEN.replace(text, " ").replace(Regex("\\s+"), " ").trim() else text
        val effectiveText = if (scopedText.isNotBlank()) scopedText else text
        val s = effectiveText.lowercase()

        val highImpact = listOf(
            "ادفع", "شراء", "اشتر", "احذف", "احذف الحساب", "تحويل مالي", "حوّل المال", "كلمة المرور",
            "رمز التحقق", "otp", "بطاقة", "cvv", "صلاحية مدير", "إدارة الجهاز", "إرسال نهائي"
        ).any { s.contains(it) }

        val route = when {
            isUrlLike(text) -> "browser"
            listOf("ابحث", "افتح موقع", "تصفح", "سجل دخول", "صفحة", "رابط").any { s.contains(it) } -> "browser"
            listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق", "برمج", "كود", "مستودع", "github", "apk").any { s.contains(it) } -> "model_or_builder"
            HakimMaterialFactory.matches(effectiveText) -> "material_factory"
            HakimHumanBiology.matches(effectiveText) -> "human_biology"
            listOf("صمم", "تصميم", "واجهة", "شعار", "صورة").any { s.contains(it) } -> "model_or_design"
            listOf("أرسل", "شارك", "تطبيق آخر", "واتساب", "بريد").any { s.contains(it) } -> "share_or_model"
            else -> "model"
        }

        val intent = when (route) {
            "browser" -> "تصفح/تنفيذ ويب"
            "model_or_builder" -> "بناء/برمجة/إنتاج"
            "material_factory" -> "تصنيع/منتج مادي"
            "human_biology" -> "أحياء/جسم الإنسان"
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
            route == "material_factory" -> "فعّل عقد مصنع حكيم؛ أنجز التصميم والتحقق المتاحين، ولا ترقِّ الحالة إلى منتج مادي دون تصنيع وقياس وقبول ميداني من نفس الأثر"
            route == "human_biology" -> "فعّل عقد الأحياء والإنسان؛ علّم وراقب وادعم القرار ضمن الدليل، ولا تنفذ تدخلًا مباشرًا على الجسم أو القلب أو الدماغ بلا بوابة مختصة"
            route == "browser" -> "افتح أو ابحث داخل حكيم ثم تابع وفق ن★ حتى تحقق الغاية"
            else -> "مرّر الغاية مع الدستور والسياق إلى أفضل نموذج أو أداة متاحة ومأذونة، مع بديل تلقائي، حتى الاكتمال"
        }

        val plan = IntentPlan(
            raw = text,
            intent = intent,
            goal = inferGoal(effectiveText),
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
            .putBoolean("tol_active", tolActive)
            .putBoolean("all_things_active", allActive)
            .apply()
        return plan
    }

    fun governedPrompt(context: Context, raw: String): String {
        val plan = resolve(context, raw)
        return buildString {
            append(HakimConstitution.promptPrefix(context))
            val tolActive = TOL_TOKEN.containsMatchIn(raw)
            val allActive = ALL_TOKEN.containsMatchIn(raw)
            appendLine("[محرك النية]")
            if (tolActive) { appendLine("[تول★]"); appendLine(TOL_CONTRACT) }
            if (allActive) { appendLine("[كل شيء★]"); appendLine(ALL_CONTRACT) }
            appendLine("النية: ${plan.intent}")
            appendLine("الغاية: ${plan.goal}")
            appendLine("المسار المبدئي: ${plan.route}")
            appendLine("سياسة العمق: ن★ تكيفية بلا رقم ثابت؛ زد الدورة ما دام هناك مكسب مادي مثبت، ولا تتوقف قبل سد الفجوات وتحقيق العقد، ولا تكرر بلا أثر.")
            appendLine("قاعدة التشغيل: لا تتوقف عند الشرح أو خطوة وسيطة؛ أكمل كل الإجراءات الآمنة والمتاحة تلقائيًا، وغيّر الوسيلة عند فشلها، حتى تحقق الغاية أو يثبت عائق حقيقي.")
            appendLine("دورة ن★: افهم→حلل→استكشف كل المفيد→اختر الأفضل/الأنسب/الأعلى→نفّذ→تحقق/أصلح→تعلّم وأعد التقدير→أكمل.")
            appendLine("عند الفعل عالي الأثر: حضّر كل شيء ثم توقف فقط قبل الفعل النهائي الذي يتطلب موافقة المستخدم.")
            appendLine("[معايير الاكتمال]")
            plan.completion.forEach { appendLine("• $it") }
            if (plan.route == "material_factory") appendLine(HakimMaterialFactory.governedContext(context, raw))
            if (plan.route == "human_biology") appendLine(HakimHumanBiology.governedContext(context, raw))
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
            .put("tol_contract_available", true)
            .put("all_things_contract_available", true)
            .put("all_things_active", p.getBoolean("all_things_active", false))
            .put("all_things_scope_limited", true)
            .put("tol_active", p.getBoolean("tol_active", false))
            .put("tol_scope_limited", true)
            .put("tol_does_not_expand_authority", true)
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
