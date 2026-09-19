package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * ناقد خطة مستقل: لا يكتفي بأن تكون الخطة قابلة للتحليل؛ يجب أن تكون مهنية قابلة للتحقق.
 * يفحص سجل القرار المختصر فقط، ولا يطلب سلسلة التفكير الداخلية من أي مزود.
 */
object HakimDeliberationQuality {
    const val VERSION = "DELIBERATION-AUDIT-2026-09-15-v1"

    data class Audit(
        val acceptable: Boolean,
        val blocked: Boolean,
        val score: Int,
        val reason: String,
        val deficiencies: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("acceptable", acceptable)
            .put("blocked", blocked)
            .put("score", score)
            .put("reason", reason)
            .put("deficiencies", JSONArray(deficiencies))
    }

    fun audit(plan: HakimReasoningProtocol.Plan, rawGoal: String): Audit {
        val wisdom = HakimEliteWisdomEngine.assess(rawGoal)
        val shubuhat = HakimHalalShubuhatGuard.assessTask(rawGoal, plan.risk == "high")
        val defects = mutableListOf<String>()
        var points = 100

        if (wisdom.gate == HakimEliteWisdomEngine.Gate.BLOCK) {
            return Audit(false, true, 0, wisdom.reason, listOf("فشل بوابة الحكمة الحاكمة"))
        }
        val questionGate = HakimQuestionOperator.gate(plan)
        if (!questionGate.proceed) {
            return Audit(false, false, 0, questionGate.reason, listOf("فشل قاعدة ؟/و؟ قبل التنفيذ"))
        }
        if (plan.protocolVersion < 3) {
            defects += "الخطة لا تستخدم بروتوكول القرار المهني v3"
            points -= 40
        }
        if (plan.phase !in setOf("research", "execute", "verify")) {
            defects += "مرحلة الخطة غير منضبطة"
            points -= 35
        }
        if (plan.protocolVersion >= 3 && plan.planId.isBlank()) {
            defects += "معرف الخطة مفقود"
            points -= 8
        }
        val minConfidence = if (plan.actions.isEmpty()) 50 else 60
        if (plan.confidence !in minConfidence..100) {
            defects += "الثقة غير معايرة أو دون الحد المهني"
            points -= 20
        }
        if (plan.confidence > wisdom.confidenceCeiling && wisdom.gate != HakimEliteWisdomEngine.Gate.PROCEED) {
            defects += "الثقة تتجاوز سقف الدليل قبل التحقق المطلوب"
            points -= 20
        }
        if (plan.actions.isNotEmpty()) {
            if (plan.alternativesConsidered < 2) {
                defects += "لم يثبت فحص بديلين متمايزين على الأقل"
                points -= 14
            }
            if (plan.successCriteria.isEmpty()) {
                defects += "معيار النجاح غير محدد"
                points -= 18
            }
            if (plan.verification.isEmpty()) {
                defects += "خطة التحقق بعد التنفيذ مفقودة"
                points -= 20
            }
            if (plan.rollback.isBlank()) {
                defects += "خطة التراجع/التعافي مفقودة"
                points -= 12
            }
            if (plan.phase == "execute" && plan.idempotencyKey.isBlank()) {
                defects += "مفتاح منع تكرار التنفيذ مفقود"
                points -= 18
            }
            if (plan.phase == "execute" && plan.expectedState.isEmpty()) {
                defects += "الحالة المتوقعة بعد التنفيذ غير محددة"
                points -= 16
            }
            if (plan.phase == "research" && plan.actions.any { it.type in setOf("set_text", "fill_profile") }) {
                defects += "مرحلة البحث تحاول تغيير بيانات بدل جمع الدليل"
                points -= 35
            }
            if (plan.phase == "verify" && plan.actions.any { it.type in setOf("set_text", "fill_profile") }) {
                defects += "مرحلة التحقق ليست قراءة فقط"
                points -= 35
            }
        }
        if (plan.risk !in setOf("low", "moderate", "high")) {
            defects += "مستوى الخطر غير مصنف"
            points -= 8
        }
        if (plan.assumptions.size > 6 || plan.unknowns.size > 6 || plan.evidenceNeeded.size > 6) {
            defects += "سجل القرار متضخم أو غير منضبط"
            points -= 8
        }
        if (wisdom.gate == HakimEliteWisdomEngine.Gate.VERIFY_FIRST &&
            plan.evidenceNeeded.isEmpty() && plan.unknowns.isEmpty() && plan.evidenceRefs.isEmpty()) {
            defects += "مهمة التثبت لا تسجل ما يلزم التحقق منه أو دليلًا متحققًا"
            points -= 24
        }
        if (wisdom.gate == HakimEliteWisdomEngine.Gate.VERIFY_FIRST && plan.phase == "execute" && plan.evidenceRefs.isEmpty()) {
            defects += "التنفيذ بدأ قبل إغلاق بوابة التثبت بدليل مسجل"
            points -= 32
        }
        if (shubuhat != null && shubuhat.gate in setOf(HakimHalalShubuhatGuard.Gate.VERIFY_FIRST, HakimHalalShubuhatGuard.Gate.ABSTAIN) &&
            plan.phase == "execute" && plan.evidenceRefs.isEmpty()) {
            defects += "مسألة حلال/حرام أو شبهة انتقلت إلى التنفيذ بلا دليل شرعي مسجل"
            points -= 45
        }
        if (plan.done && plan.actions.isNotEmpty()) {
            defects += "الخطة تدعي الاكتمال مع وجود أفعال معلقة"
            points -= 40
        }
        if (plan.done && plan.phase != "verify") {
            defects += "إعلان الاكتمال لم يأت من مرحلة تحقق مستقلة"
            points -= 28
        }

        val normalized = points.coerceIn(0, 100)
        val acceptable = defects.none { it.contains("فشل بوابة") } && normalized >= 78
        return Audit(
            acceptable = acceptable,
            blocked = false,
            score = normalized,
            reason = if (acceptable)
                "الخطة اجتازت بوابة المداولة المهنية: بدائل/ثقة/نجاح/تحقق/تراجع ضمن الحد المطلوب"
            else "الخطة تحتاج إعادة تخطيط قبل التنفيذ: ${defects.joinToString("؛ ").take(700)}",
            deficiencies = defects
        )
    }

    fun promptContext(rawGoal: String): String {
        val wisdom = HakimEliteWisdomEngine.assess(rawGoal)
        return buildString {
            appendLine("[حلقة المداولة المهنية — $VERSION]")
            append(HakimQuestionOperator.promptContext())
            append(HakimHalalShubuhatGuard.promptContext(rawGoal, false))
            appendLine("لا تُخرج سلسلة تفكير داخلية. أنشئ سجل قرار موجزًا ومنضبطًا فقط.")
            appendLine("اعمل بهذا الترتيب: 1) الهدف ومعيار النجاح، 2) الحقائق والمجهولات، 3) بدائل متمايزة قليلة، 4) نقد كل بديل بالبوابات الحاكمة، 5) اختيار أقل تدخل يحقق الغاية، 6) خطة تحقق وتراجع.")
            appendLine("للخطة المهنية يجب تسجيل: protocol_version=3، phase، plan_id، idempotency_key، confidence، alternatives_considered، assumptions، unknowns، evidence_needed، evidence_refs، success_criteria، expected_state، verification، risk، rollback، ثم actions المحدودة.")
            appendLine("research يجمع الدليل ولا يكتب بيانات؛ execute يحتاج expected_state ومفتاح منع تكرار؛ verify قراءة مستقلة ولا يعلن done=true قبل تحقق postcondition.")
            appendLine("لا ترفع الثقة لتعويض نقص الدليل. بوابة المهمة الحالية=${wisdom.gate} وسقف الثقة قبل مزيد من التثبت=${wisdom.confidenceCeiling}/100.")
            appendLine("إذا لم يوجد مكسب مادي أو كان خط الأساس أفضل، اختر NO-OP/KEEP_BASELINE بدل التغيير لذاته.")
        }.take(5000)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("protocol_v3_required_for_execution", true)
        .put("alternatives_required", true)
        .put("success_criteria_required", true)
        .put("verification_required", true)
        .put("rollback_required", true)
        .put("confidence_calibrated", true)
        .put("private_chain_of_thought_not_requested", true)
        .put("phase_separation", true)
        .put("idempotency_required", true)
        .put("postcondition_required", true)
        .put("question_operator", HakimQuestionOperator.status())
        .put("halal_shubuhat_guard", HakimHalalShubuhatGuard.status())
        .put("accept_threshold", 78)
}
