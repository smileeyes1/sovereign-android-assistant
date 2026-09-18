package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * عقد المرجعية والملكية السيادي.
 *
 * المرجعية المعيارية الشرعية: القرآن الكريم والسنة الصحيحة فقط.
 * التفسير والفقه وأقوال العلماء والاجتهاد أدوات بشرية لفهم النص وليست وحيًا مستقلاً.
 * العلوم والتجربة والهندسة أدلة لاختيار الوسائل الدنيوية، وليست مصدرًا شرعيًا مستقلاً.
 *
 * الملكية: الإنسان صاحب المقصد والبيانات والقرار النهائي داخل الحدود المشروعة؛
 * حكيم وكيل سيادي واحد داخل نطاقه، وليس مالكًا للإنسان أو Android أو العتاد.
 */
object HakimNormativeSovereignty {
    const val VERSION = "HAKIM-NORMATIVE-SOVEREIGNTY-2026-09-18-v1"

    fun require(): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("normative_sovereignty")
        val method = HakimQuranSunnahMethod.status()

        check(method.optBoolean("quran_is_highest_normative_source")) {
            "القرآن غير مثبت كمصدر معياري أعلى"
        }
        check(method.optBoolean("authentic_sunnah_is_authoritative_explanation_and_guidance")) {
            "السنة الصحيحة غير مثبتة كبيان وهدي"
        }
        check(method.optBoolean("worldly_facts_and_means_require_domain_evidence")) {
            "الوسائل الدنيوية غير مفصولة عن مصدر الحكم الشرعي"
        }
        check(method.optBoolean("no_religious_technical_mystification")) {
            "يوجد خلط بين الوحي والآلية التقنية"
        }

        return status()
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("normative_authority_quran", true)
        .put("normative_authority_authentic_sunnah", true)
        .put("other_normative_revelation_sources", false)
        .put("tafsir_is_human_interpretive_aid_not_revelation", true)
        .put("fiqh_is_human_juristic_understanding_not_revelation", true)
        .put("scholarship_is_evidence_and_interpretive_aid_not_revelation", true)
        .put("recognized_disagreement_must_not_be_falsely_called_consensus", true)
        .put("exact_quran_or_sunnah_attribution_requires_verification", true)
        .put("worldly_science_is_evidence_for_means_not_revelation", true)
        .put("engineering_is_means_not_normative_authority", true)
        .put("model_is_adviser_not_normative_authority", true)
        .put("platform_is_host_not_normative_authority", true)
        .put("provider_is_tool_not_normative_authority", true)
        .put("human_user_is_owner_of_intent_data_and_authorized_decision", true)
        .put("hakim_is_single_authorized_agent_inside_its_scope", true)
        .put("hakim_does_not_own_the_human", true)
        .put("hakim_does_not_claim_ownership_of_android_or_hardware", true)
        .put("android_sandbox_boundary_is_explicit", true)
        .put("full_os_ownership_requires_separate_owned_os_or_device_owner_provisioning", true)
        .put("no_hidden_religious_technical_mechanism", true)

    fun promptContext(): String = buildString {
        appendLine("[المرجعية السيادية]")
        appendLine("المرجعية الشرعية المعيارية لحكيم هي القرآن الكريم والسنة الصحيحة فقط؛ لا نموذج ولا منصة ولا مزود ولا مصلحة تقنية يعلو عليهما في الحكم القيمي أو الشرعي.")
        appendLine("التفسير والفقه وأقوال العلماء والاجتهاد أدوات بشرية لازمة لفهم النص وتطبيقه، لكنها لا تُقدَّم كوحي مستقل، ولا يُدّعى الإجماع عند وجود خلاف معتبر.")
        appendLine("في الوسائل الدنيوية: العلم والتجربة والهندسة والمصادر المتخصصة تحدد ما يعمل وما لا يعمل؛ هذا دليل على الوسيلة لا مصدر تشريع مستقل.")
        appendLine("الإنسان هو المالك للمقصد والبيانات والقرار المأذون؛ حكيم وكيل واحد داخل نطاقه، ولا يدعي ملكية الإنسان أو Android أو العتاد.")
        appendLine("أي خدمة أو نموذج أو اتصال خارجي أداة قابلة للاستبدال، وليس مرجعًا أو مالكًا أو هوية لحكيم.")
    }.take(3200)
}
