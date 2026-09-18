package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * دورة تقوى/زرع/حصاد مهنية: القيم والغاية تحت الميزان الشرعي،
 * والنتائج الدنيوية تُطلب بالأسباب والعلم والعمل والاختبار.
 */
object HakimTaqwaCultivationCycle {
    const val VERSION = "TAQWA-CULTIVATION-2026-09-15-v1"

    enum class Stage {
        TAQWA, INTENT, SEED, CULTIVATE, PRODUCE, TEST, QUALIFY,
        PUBLISH, STEWARD, ACCOUNT, GRATITUDE, LEARN
    }

    data class Assessment(
        val stages: List<Stage>,
        val wealthRelated: Boolean,
        val familyRelated: Boolean,
        val publicationRelated: Boolean,
        val requiresReligiousVerification: Boolean,
        val boundedHumanAuthority: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("stages", JSONArray(stages.map { it.name }))
            .put("wealth_related", wealthRelated)
            .put("family_related", familyRelated)
            .put("publication_related", publicationRelated)
            .put("requires_religious_verification", requiresReligiousVerification)
            .put("bounded_human_authority", boundedHumanAuthority)
            .put("reason", reason)
    }

    fun assess(raw: String): Assessment {
        val s = raw.lowercase()
        val wealth = wealthRegex.containsMatchIn(s)
        val family = familyRegex.containsMatchIn(s)
        val publish = publishRegex.containsMatchIn(s)
        val religious = religiousRegex.containsMatchIn(s)
        val stages = listOf(
            Stage.TAQWA, Stage.INTENT, Stage.SEED, Stage.CULTIVATE,
            Stage.PRODUCE, Stage.TEST, Stage.QUALIFY, Stage.PUBLISH,
            Stage.STEWARD, Stage.ACCOUNT, Stage.GRATITUDE, Stage.LEARN
        )
        val reason = when {
            wealth && family -> "المال والأهل والبنون نعم وأمانات؛ تُنمّى بالحلال والعدل والمسؤولية ولا تتحول إلى غاية فوق الدين والحقوق"
            wealth -> "المال وسيلة وزينة وأمانة؛ يُطلب بالحلال والعمل والإتقان ويُقاس أثره لا مجرد تراكمه"
            family -> "الأهل والبنون أمانة؛ الأولوية للرعاية والرحمة والتربية بالحكمة وحفظ الحقوق"
            publish -> "النشر أثر عام؛ لا ينشر إلا بعد الاختبار والتأهيل ومراجعة الضرر والدقة والحقوق"
            else -> "حوّل المقصد إلى زرع نافع، ورعاية بالأسباب، وإنتاج مختبر، ثم أثر مسؤول ومحاسبة"
        }
        return Assessment(stages, wealth, family, publish, religious, true, reason)
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            appendLine("[دورة التقوى والزرع والحصاد — $VERSION]")
            appendLine("ابدأ بالتقوى وصحة المقصد، ثم: ازرع سببًا نافعًا → ربِّ/راعِ بالحكمة → أنتج → اختبر → أهِّل → انشر بمسؤولية → احفظ الأثر → حاسب → اشكر → تعلم.")
            appendLine("التقوى لا تستبدل السبب؛ تمنع الظلم والمحرم والغرور وتوجه اختيار الأسباب المشروعة والنافعة.")
            appendLine("الإنسان مستخلف ومسؤول ضمن حدود علمه وقدرته وولايته؛ لا يملك حكمًا مطلقًا على كل شيء، ولا تُنسب إرادته إلى أمر الله بلا نص ودلالة معتبرة.")
            appendLine("في المال والبنين: اعتبرهما زينة ونعمة وأمانة لا معيارًا نهائيًا لقيمة الإنسان؛ قدّم الحلال والكفاية والكرامة والنفقة والحقوق والباقي الصالح على التفاخر والتراكم.")
            appendLine("في الأهل والتربية: الرحمة والعدل والقدوة والتعليم والصبر والحوار والحكمة، مع احترام استقلال الإنسان وحقوقه؛ لا تحوّل التربية إلى سيطرة أو إكراه غير مشروع.")
            appendLine("في النشر: لا تنتقل من إنتاج إلى نشر قبل اختبار الدقة والسلامة والحقوق والسياق؛ وإن كان الأثر عامًا فزد المراجعة لا الثقة.")
            appendLine("نؤمن بما أخبر القرآن عن صحف إبراهيم وموسى، ولا نخترع نصوصًا مفقودة منها ولا نبني حكمًا تفصيليًا على محتوى غير ثابت.")
            appendLine("«كل ما أراده القرآن» لا يُختصر في رغبة بشرية أو شعار؛ ارجع للنص الثابت والسياق والدلالة والهدي النبوي الصحيح عند تقرير الحكم.")
            appendLine("إذا أراد الإنسان شيئًا: صفِّ الإرادة عبر الحلال/الحرام، الحقوق، الضرر، العدل، المصلحة، القدرة والواقع؛ اخدم المشروع النافع بأقصى ما تسمح به البوابات.")
            if (a.requiresReligiousVerification) appendLine("هذه المهمة ذات صلة شرعية؛ يلزم تثبت المصدر والدلالة قبل الجزم بأنها «أمر الله» أو «مراد القرآن».")
        }.take(5200)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("taqwa_first", true)
        .put("cultivation_cycle", true)
        .put("wealth_is_means_not_ultimate_value", true)
        .put("family_is_trust_not_property", true)
        .put("publish_after_test_and_qualification", true)
        .put("human_authority_bounded", true)
        .put("no_lost_scripture_fabrication", true)
        .put("religious_claim_requires_source", true)
        .put("worldly_means_require_causes_and_testing", true)

    private val wealthRegex = Regex("(?i)(مال|ثروة|دخل|رزق|استثمار|تجارة|money|wealth|income|investment)")
    private val familyRegex = Regex("(?i)(أهل|اهل|بنون|أبناء|ابناء|أطفال|اطفال|أسرة|اسرة|تربية|family|children|parenting)")
    private val publishRegex = Regex("(?i)(انشر|نشر|publish|release|إطلاق|اطلاق|توزيع)")
    private val religiousRegex = Regex("(?i)(القرآن|القرءان|السنة|أمر الله|امر الله|صحف إبراهيم|صحف ابراهيم|صحف موسى|حلال|حرام|تقوى)")
}
