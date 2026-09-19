package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * عقد المعرفة النبوية الموثقة.
 *
 * الهدف: تغطية كل المجالات المادية المتعلقة بسيدنا محمد ﷺ مع منع ادعاء أن corpus واحدًا
 * يساوي «السنة كلها». بخلاف القرآن ذي النص المحفوظ المحدد، التراث الحديثي والسيري متعدد
 * المصادر والدرجات؛ لذلك الاكتمال هنا = اكتمال نطاق + منهج تحقق + فشل مغلق عند غياب الدليل.
 */
object HakimPropheticKnowledgePolicy {
    const val VERSION = "PROPHETIC-KNOWLEDGE-2026-09-15-v1"

    /** نطاقات التغطية التي يجب ألا تسقط من أي ادعاء «كل ما يخص النبي ﷺ». */
    val COVERAGE_DOMAINS = listOf(
        "identity_names_titles_lineage",
        "birth_childhood_youth_pre_prophethood",
        "revelation_and_beginning_of_prophethood",
        "meccan_period_dawah_and_persecution",
        "isra_miraj_with_source_verification",
        "hijrah_and_medinan_period",
        "worship_prayer_fasting_hajj_dhikr_dua",
        "character_mercy_justice_patience_truthfulness_trust",
        "household_wives_mothers_of_believers_children_family",
        "ahl_al_bayt_and_companions_with_fairness",
        "daily_guidance_food_dress_sleep_travel_social_conduct",
        "teaching_fatwa_judgment_leadership_and_consultation",
        "dawah_delegations_letters_treaties_and_relations",
        "battles_expeditions_and_conflict_context",
        "miracles_signs_and_prophetic_distinctions",
        "shamail_appearance_manners_and_personal_traits",
        "final_hajj_final_illness_death_and_burial",
        "rights_love_obedience_following_sending_blessings",
        "hadith_attribution_grading_takhrij_and_variants",
        "sirah_chronology_and_disputed_reports"
    )

    data class Assessment(
        val prophetic: Boolean,
        val specificAttributionOrFact: Boolean,
        val requiresVerification: Boolean,
        val domains: List<String>,
        val reason: String
    )

    fun assess(raw: String): Assessment {
        HakimQuranicInvariantKernel.requireInherited("prophetic_knowledge_policy")
        val s = raw.trim().lowercase()
        val prophetic = PROPHETIC_REGEX.containsMatchIn(s)
        if (!prophetic) {
            return Assessment(false, false, false, emptyList(), "لا توجد إحالة نبوية مباشرة")
        }

        val specific = SPECIFIC_CLAIM_REGEX.containsMatchIn(s)
        val matched = linkedSetOf<String>()
        DOMAIN_PATTERNS.forEach { (domain, regex) -> if (regex.containsMatchIn(s)) matched += domain }
        if (matched.isEmpty()) matched += "general_prophetic_guidance"

        return Assessment(
            prophetic = true,
            specificAttributionOrFact = specific,
            requiresVerification = specific,
            domains = matched.toList(),
            reason = if (specific)
                "السؤال يتضمن نسبة أو واقعة محددة عن النبي ﷺ؛ يلزم تحقق من المصدر والدرجة والسياق"
            else
                "السؤال يتعلق بالنبي ﷺ على مستوى عام؛ طبّق الأدب والهدي الثابت ولا تخترع نسبة تفصيلية"
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        if (!a.prophetic) return ""
        return buildString {
            appendLine("[المعرفة النبوية الموثقة — سيدنا محمد ﷺ]")
            appendLine("غطِّ المجال المطلوب من سيرة النبي ﷺ وهديه وسنته وشمائله وأخلاقه وعبادته ومعاملاته وأسرته وأمهات المؤمنين وآل بيته وصحابته ودعوته وهجرته وقيادته وغزواته وصلحه ورسائله ومعجزاته وخصائصه ومرضه ووفاته وحقوقه، لكن لا تجعل شمول الموضوع ذريعة لقبول رواية غير ثابتة.")
            appendLine("سلّم الدليل: القرآن المحكم في موضعه → الحديث الثابت مع عزوه ودرجته عند الحاجة → ما حسُن وثبت بقدر درجته → السيرة والتاريخ بعد نقد المصدر والتعارض → المختلف فيه يُعرض كمختلف فيه → الضعيف/الموضوع/المجهول لا يُروى بصيغة الجزم.")
            appendLine("صحيحا البخاري ومسلم مرجعان عظيمان في الحديث الصحيح، وما عداهما لا يُحكم عليه بمجرد اسم الكتاب؛ افحص الحديث المعين وحكم أهل الحديث عند الحاجة.")
            appendLine("ميّز دائمًا بين القرآن، والحديث القدسي، والحديث النبوي، والسيرة، والشمائل، والتفسير، والفقه، واستنباط العلماء، ولا تنقل كلام عالم أو سيرةً متأخرة على أنه كلام النبي ﷺ.")
            appendLine("في المغازي والنزاعات والخصائص والمعجزات وأشراط الساعة والفضائل والقصص المشهورة شدّد التثبت لأن كثرة التداول ليست دليل صحة.")
            appendLine("في آل البيت والصحابة وأمهات المؤمنين التزم العدل والأدب والتثبت، ولا تُدخل جدلًا طائفيًا أو روايات واهية في موضع التعليم أو الاقتداء.")
            appendLine("الاقتداء العملي يقدّم الثابت من الصدق والأمانة والرحمة والعدل والحلم والشورى والوفاء والإحسان وحفظ الحقوق، مع مراعاة اختلاف المقامات وعدم اختلاق حكم شرعي أو سنة جديدة.")
            appendLine("لا تدّع أن السنة أو السيرة «مكتملة محليًا» لمجرد اكتمال هذا النطاق؛ اكتمال النطاق شيء، وامتلاك corpus نصي موثق شامل شيء آخر.")
            if (a.requiresVerification) appendLine("هذه المهمة فيها نسبة/واقعة نبوية محددة: تحقّق من النص والمصدر والراوي/المخرج والدرجة والسياق قبل الجزم.")
        }.take(7600)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("scope_schema_complete", COVERAGE_DOMAINS.size == 20)
        .put("coverage_domain_count", COVERAGE_DOMAINS.size)
        .put("coverage_domains", JSONArray(COVERAGE_DOMAINS))
        .put("quran_has_separate_verified_local_corpus", true)
        .put("local_exhaustive_prophetic_corpus_verified", false)
        .put("all_heritage_reports_assumed_authentic", false)
        .put("specific_attribution_requires_verification", true)
        .put("hadith_grade_must_be_preserved_when_material", true)
        .put("sirah_reports_require_source_criticism", true)
        .put("disputed_reports_must_remain_disputed", true)
        .put("weak_or_fabricated_not_presented_as_authentic", true)
        .put("sectarian_inflaming_from_weak_reports_forbidden", true)
        .put("prophetic_love_and_reverence_without_exaggeration", true)
        .put("no_technical_mystification", true)
        .put("completeness_claim_fail_closed", true)

    private val PROPHETIC_REGEX = Regex(
        "(?i)(محمد|النبي|رسول الله|الرسول|المصطفى|السنة النبوية|السيرة النبوية|الشمائل|الهدي النبوي|الصلاة على النبي|ﷺ|صلى الله عليه وسلم|أمهات المؤمنين|امهات المؤمنين|آل البيت|أهل البيت|الصحابة)"
    )

    private val SPECIFIC_CLAIM_REGEX = Regex(
        "(?i)(قال|حديث|رواه|أخرجه|صححه|ضعفه|درجة|تخريج|قصة|حدث|متى|أين|كم|من هو|من هي|غزوة|سرية|معجزة|خصائص|ولد|توفي|دفن|زوجة|زوجاته|أبناؤه|بناته|صفة|شمائل|حجة الوداع|مرض النبي|وفاة النبي|الإسراء|المعراج)"
    )

    private val DOMAIN_PATTERNS = linkedMapOf(
        "identity_names_titles_lineage" to Regex("(?i)(اسمه|أسماؤه|نسب|لقب|كنية|قريش|بني هاشم)"),
        "birth_childhood_youth_pre_prophethood" to Regex("(?i)(ولد|ولادة|طفولة|رضاعة|شباب|قبل البعثة|خديجة قبل البعثة)"),
        "revelation_and_beginning_of_prophethood" to Regex("(?i)(الوحي|البعثة|غار حراء|اقرأ|جبريل)"),
        "meccan_period_dawah_and_persecution" to Regex("(?i)(مكة|مكي|قريش|الدعوة سرا|الدعوة جهرا|الحصار|الطائف)"),
        "isra_miraj_with_source_verification" to Regex("(?i)(الإسراء|الاسراء|المعراج)"),
        "hijrah_and_medinan_period" to Regex("(?i)(الهجرة|المدينة|يثرب|الأنصار|المهاجرين)"),
        "worship_prayer_fasting_hajj_dhikr_dua" to Regex("(?i)(صلاة|صيام|حج|عمرة|ذكر|دعاء|عبادة|وتر|قيام)"),
        "character_mercy_justice_patience_truthfulness_trust" to Regex("(?i)(خلق|أخلاق|رحمة|عدل|صبر|صدق|أمانة|حلم|عفو)"),
        "household_wives_mothers_of_believers_children_family" to Regex("(?i)(زوج|زوجات|أمهات المؤمنين|امهات المؤمنين|خديجة|عائشة|حفصة|أبناؤه|بناته|فاطمة|القاسم|إبراهيم)"),
        "ahl_al_bayt_and_companions_with_fairness" to Regex("(?i)(آل البيت|أهل البيت|الصحابة|أبو بكر|عمر|عثمان|علي)"),
        "daily_guidance_food_dress_sleep_travel_social_conduct" to Regex("(?i)(طعام|شراب|لباس|نوم|سفر|زيارة|سلام|مجلس|آداب)"),
        "teaching_fatwa_judgment_leadership_and_consultation" to Regex("(?i)(تعليم|فتوى|قضاء|حكم|قيادة|شورى|إمامة)"),
        "dawah_delegations_letters_treaties_and_relations" to Regex("(?i)(دعوة|وفود|رسائل|كتاب إلى|صلح|معاهدة|الحديبية)"),
        "battles_expeditions_and_conflict_context" to Regex("(?i)(غزوة|غزوات|سرية|سرايا|بدر|أحد|الخندق|حنين|تبوك|فتح مكة)"),
        "miracles_signs_and_prophetic_distinctions" to Regex("(?i)(معجزة|معجزات|آية من آيات النبوة|خصائص|دلائل النبوة)"),
        "shamail_appearance_manners_and_personal_traits" to Regex("(?i)(شمائل|صفة النبي|شكله|هيئته|شعره|لحيته|طوله|مشيته|ضحكه)"),
        "final_hajj_final_illness_death_and_burial" to Regex("(?i)(حجة الوداع|مرض النبي|وفاة النبي|توفي|دفن|الرفيق الأعلى)"),
        "rights_love_obedience_following_sending_blessings" to Regex("(?i)(محبة النبي|طاعة النبي|اتباع النبي|الصلاة على النبي|حقوق النبي|توقير النبي)"),
        "hadith_attribution_grading_takhrij_and_variants" to Regex("(?i)(حديث|رواه|أخرجه|إسناد|سند|متن|صحيح|حسن|ضعيف|موضوع|تخريج|راوي)"),
        "sirah_chronology_and_disputed_reports" to Regex("(?i)(سيرة|التسلسل الزمني|ترتيب الأحداث|اختلف|خلاف|رواية أخرى|تاريخ)"),
    )
}
