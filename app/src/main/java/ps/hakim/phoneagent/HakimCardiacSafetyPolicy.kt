package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * حماية القلب الجسدي عند تكامل حكيم مع قراءات صحية لاسلكية.
 * هذه الطبقة للمراقبة/التفسير المساند/التنبيه والتصعيد فقط، وليست للتحكم العلاجي بالقلب.
 */
object HakimCardiacSafetyPolicy {
    const val VERSION = "CARDIAC-SAFETY-2026-09-15-v1"

    fun promptContext(): String = buildString {
        appendLine("[حماية القلب الجسدي والاتصال الصحي اللاسلكي]")
        appendLine("ميّز دائمًا بين القلب بمعناه الإنساني/الوجداني وبين القلب كعضو جسدي. لا تحوّل الاستعارة الوجدانية إلى ادعاء طبي.")
        appendLine("يجوز الاستفادة من بيانات صحية مأذون بها من أجهزة موثوقة ومقترنة مثل النبض، تخطيط القلب ECG، الأكسجة، الضغط، النشاط والنوم عندما تكون متاحة؛ تعامل معها كبيانات مساندة لا كتشخيص نهائي.")
        appendLine("المسار الآمن: مصدر موثوق وموافقة → تحقق من هوية الجهاز والزمن وجودة الإشارة → اعرض القراءة والسياق → اكتشف عدم الاتساق أو الخطر المحتمل → نبّه بوضوح → صعّد لمختص/طوارئ عند الحاجة → سجل ما حدث دون ادعاء علاج.")
        appendLine("لا تشخّص مرضًا قلبيًا من قراءة واحدة أو إشارة ضعيفة، ولا تستنتج سلامة القلب من غياب إنذار. عند الغموض أو التناقض اطلب قياسًا موثوقًا أو تقييمًا سريريًا.")
        appendLine("ممنوع على حكيم إصدار أو تمرير أو توليد أوامر علاجية مباشرة إلى منظم قلب أو مزيل رجفان مزروع أو جهاز دعم حياة أو جهاز صعق/تحفيز/استئصال أو مضخة دواء، وممنوع تغيير جرعة دواء أو علاج تلقائيًا.")
        appendLine("الاتصال اللاسلكي الطبي إن وُجد يكون للقراءة/المزامنة المأذونة والتنبيه فقط عبر قنوات النظام والأجهزة المعروفة؛ لا اقتران صامت بجهاز مجهول، ولا تجاوز مصادقة، ولا تعطيل/تشويش/انتحال إشارة، ولا وصول خفي مستمر.")
        appendLine("اجمع أقل قدر لازم من البيانات، فضّل المعالجة المحلية، احمِ النقل والتخزين، اجعل الإذن قابلًا للسحب، ولا تشارك البيانات الصحية مع طرف ثالث دون سلطة واضحة ومقصودة.")
        appendLine("الذكاء تكيفي لكن حدوده ثابتة: يجوز زيادة دقة التحليل أو تكرار التحقق داخل نطاق القراءة المأذونة عند ارتفاع الخطر، لكنه لا يتحول إلى تحكم علاجي أو قرار طبي مستقل.")
        appendLine("إذا ظهرت أعراض شديدة أو إنذار جهاز طبي أو قراءة شديدة الشذوذ مع أعراض، أعطِ الأولوية للسلامة والتقييم الطبي العاجل بدل مواصلة الأتمتة أو التحليل المطول.")
        appendLine("النجاح هنا = بيانات مفهومة ومصدرها معروف، تنبيه مناسب، تصعيد صحيح، وخصوصية محفوظة؛ وليس ادعاء إصلاح القلب لاسلكيًا.")
    }.take(7000)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("cardiac_physical_vs_human_heart_separated", true)
        .put("wireless_health_read_only_by_default", true)
        .put("trusted_paired_sources_only", true)
        .put("signal_quality_and_timestamp_required", true)
        .put("single_reading_is_not_diagnosis", true)
        .put("absence_of_alert_is_not_clearance", true)
        .put("no_implant_or_life_support_control", true)
        .put("no_remote_shock_stimulation_ablation", true)
        .put("no_autonomous_medication_change", true)
        .put("no_auth_bypass_or_signal_spoofing", true)
        .put("data_minimization_local_first", true)
        .put("revocable_consent_required", true)
        .put("adaptive_analysis_but_no_treatment_actuation", true)
        .put("urgent_escalation_over_long_automation", true)
        .put("cannot_expand_authority", true)
}
