package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * حارس «الحلال بيّن والحرام بيّن وبينهما مشتبهات».
 * لا ينشئ حكمًا شرعيًا من الكلمات؛ الحكم القطعي يحتاج مصدرًا متحققًا وسياقًا ودلالة.
 */
object HakimHalalShubuhatGuard {
    const val VERSION = "HALAL-SHUBUHAT-GUARD-2026-09-15-v1"

    enum class RulingState { CLEAR_PERMITTED, CLEAR_PROHIBITED, DOUBTFUL, UNKNOWN }
    enum class Gate { PROCEED, VERIFY_FIRST, ABSTAIN, BLOCK_AND_ALTERNATIVE }

    data class Decision(
        val ruling: RulingState,
        val gate: Gate,
        val sourceVerified: Boolean,
        val highImpact: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("ruling", ruling.name)
            .put("gate", gate.name)
            .put("source_verified", sourceVerified)
            .put("high_impact", highImpact)
            .put("reason", reason)
    }

    fun decide(
        ruling: RulingState,
        sourceVerified: Boolean,
        highImpact: Boolean
    ): Decision {
        if ((ruling == RulingState.CLEAR_PERMITTED || ruling == RulingState.CLEAR_PROHIBITED) && !sourceVerified) {
            return Decision(
                RulingState.UNKNOWN,
                if (highImpact) Gate.ABSTAIN else Gate.VERIFY_FIRST,
                false,
                highImpact,
                "لا يجوز تحويل حكم غير متحقق إلى حلال/حرام بيّن؛ يلزم مصدر شرعي موثوق وسياق ودلالة"
            )
        }
        return when (ruling) {
            RulingState.CLEAR_PERMITTED -> Decision(
                ruling, Gate.PROCEED, true, highImpact,
                "الحكم المبيح متحقق؛ يمضي الفعل فقط بعد اجتياز بقية بوابات الحقوق والسلامة والسلطة"
            )
            RulingState.CLEAR_PROHIBITED -> Decision(
                ruling, Gate.BLOCK_AND_ALTERNATIVE, true, highImpact,
                "التحريم متحقق؛ امنع الإعانة على الفعل وقدّم البديل المشروع الأقرب للغاية"
            )
            RulingState.DOUBTFUL -> Decision(
                ruling, if (highImpact) Gate.ABSTAIN else Gate.VERIFY_FIRST, sourceVerified, highImpact,
                "المسألة مشتبهة؛ استبرئ للدين والعرض ولا تنفذ أثرًا جوهريًا قبل التثبت"
            )
            RulingState.UNKNOWN -> Decision(
                ruling, if (highImpact) Gate.ABSTAIN else Gate.VERIFY_FIRST, false, highImpact,
                "الحكم غير محسوم؛ لا تجزم ولا تُنشئ فتوى آلية، وابحث في مصدر معتبر ثم أعد التقييم"
            )
        }
    }

    /**
     * فحص المهمة لا يستنبط الحكم. إذا كانت المهمة تسأل عن الحلال/الحرام أو الشبهة،
     * يبدأ من UNKNOWN/DOUBTFUL حتى يأتي دليل متحقق من طبقة النزاهة الشرعية.
     */
    fun assessTask(raw: String, highImpact: Boolean = false): Decision? {
        val s = raw.trim().lowercase()
        if (!relevantRegex.containsMatchIn(s)) return null
        val state = if (shubhaRegex.containsMatchIn(s)) RulingState.DOUBTFUL else RulingState.UNKNOWN
        return decide(state, sourceVerified = false, highImpact = highImpact)
    }

    fun promptContext(raw: String, highImpact: Boolean = false): String {
        val task = assessTask(raw, highImpact)
        return buildString {
            appendLine("[حارس الحلال والحرام والشبهات — $VERSION]")
            appendLine("الأصل النبوي الحاكم: الحلال البيّن والحرام البيّن وبينهما مشتبهات؛ اتقاء الشبهات استبراء للدين والعرض، وحمى الله محارمه، وصلاح القلب أصل في صلاح العمل.")
            appendLine("لا تُنشئ حكم حلال/حرام من كلمة أو حدس أو ثقة نموذج. الحكم البيّن يحتاج تثبتًا من مصدر معتبر وسياق ودلالة؛ وعند الخلاف المعتبر لا تدّع الإجماع.")
            appendLine("CLEAR_PERMITTED المتحقق: امضِ فقط بعد بقية بوابات الحقوق والسلامة. CLEAR_PROHIBITED المتحقق: امنع الإعانة وقدّم البديل المشروع. DOUBTFUL/UNKNOWN: تحقق أولًا، وامتنع عن الأثر الجوهري حتى يتضح.")
            appendLine("طبّق هامش أمان حول المحارم: لا تجعل التصميم يسير على حافة المنع عندما يوجد بديل أبعد عن الشبهة ويحقق الغاية بكلفة معقولة.")
            appendLine("القلب هنا مبدأ إنساني/أخلاقي للمقصد والضمير والإصلاح؛ لا يُحوّل إلى مستشعر تقني أو ادعاء قراءة النيات أو الغيب.")
            if (task != null) appendLine("حالة المهمة الحالية=${task.ruling}/${task.gate}: ${task.reason}")
        }.take(4200)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("clear_halal_requires_verified_source", true)
        .put("clear_haram_requires_verified_source", true)
        .put("doubtful_requires_verification", true)
        .put("high_impact_doubt_abstains", true)
        .put("verified_haram_blocks_and_offers_permissible_alternative", true)
        .put("protective_margin_around_prohibitions", true)
        .put("heart_not_technical_sensor", true)
        .put("no_automated_fatwa_from_keywords", true)

    private val relevantRegex = Regex(
        "(?i)(حلال|حرام|شبهة|شبهات|مشتبه|مشتبهات|ورع|استبرأ|استبراء|محارم|حكم شرعي|يجوز|لا يجوز|فتوى)"
    )
    private val shubhaRegex = Regex("(?i)(شبهة|شبهات|مشتبه|مشتبهات|غير واضح|غير محسوم|اختلط|التبس)")
}
