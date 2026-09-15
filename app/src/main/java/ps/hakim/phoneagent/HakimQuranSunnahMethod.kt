package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * منهج عالمي لحكيم: القرآن هو المصدر الأعلى للهداية والقيم والغاية والحدود الشرعية،
 * والسنة الصحيحة عن سيدنا محمد ﷺ بيانٌ ملزم وهديٌ عملي وقدوة، مع التثبت من النسبة.
 * التفسير والفقه والسيرة والرأي البشري لا تُرفع إلى مرتبة الوحي، والوقائع والوسائل الدنيوية تُحسم بالدليل المتخصص.
 */
object HakimQuranSunnahMethod {
    const val VERSION = "QURAN-SUNNAH-METHOD-2026-09-15-v2"

    data class Assessment(
        val normativeRoot: Boolean,
        val propheticGuidance: Boolean,
        val exactSourceVerification: Boolean,
        val worldlyEvidenceRequired: Boolean,
        val propheticKnowledge: HakimPropheticKnowledgePolicy.Assessment,
        val reason: String
    )

    fun assess(raw: String): Assessment {
        HakimQuranicInvariantKernel.requireInherited("quran_sunnah_method")
        val religious = HakimReligiousIntegrity.assess(raw)
        val quranic = HakimQuranicFramework.assess(raw)
        val propheticKnowledge = HakimPropheticKnowledgePolicy.assess(raw)
        val exact = quranic.exactQuranTextRequired || religious.exactSourceRequired || propheticKnowledge.requiresVerification
        return Assessment(
            normativeRoot = true,
            propheticGuidance = true,
            exactSourceVerification = exact,
            worldlyEvidenceRequired = quranic.worldlyMeansTask,
            propheticKnowledge = propheticKnowledge,
            reason = when {
                exact && propheticKnowledge.prophetic -> "يلزم التثبت من النص/النسبة/الواقعة النبوية قبل الجزم"
                exact -> "يلزم التثبت من النص/النسبة قبل الجزم"
                quranic.worldlyMeansTask -> "الغاية والأثر تحت الميزان القرآني والهدي النبوي، والوسيلة تُحسم بالدليل المتخصص"
                propheticKnowledge.prophetic -> "طبّق نطاق المعرفة النبوية الموثقة مع الأدب والتثبت وعدم اختلاق نسبة"
                religious.religious -> "الحكم والمعنى يرجعان إلى القرآن والسنة الصحيحة مع الفصل عن الاجتهاد البشري"
                else -> "المنهج القرآني والنبوي يحكم الغاية والقيم وطريقة التعامل، دون فرض نسبة دينية على تفاصيل دنيوية لم تثبت"
            }
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            appendLine("[منهج القرآن والهدي النبوي — حاكم في كل مهمة]")
            appendLine("القرآن الكريم هو المصدر الأعلى للهداية والمعنى والغاية والقيم والحدود الشرعية، والسنة الصحيحة عن سيدنا محمد ﷺ بيانٌ وهديٌ وقدوة عملية؛ لا يعلو عليهما رأي نموذج أو منفعة أو سرعة عند ثبوت الحكم والدلالة.")
            appendLine("طبّق المنهج بهذا الترتيب: ثبّت الغاية على الميزان القرآني → افحص الهدي النبوي الصحيح ذي الصلة → ميّز الوحي عن التفسير والفقه والسيرة والاجتهاد → افحص الواقع والدليل المتخصص → اختر الوسيلة المشروعة الأعلى أثرًا والأقل ظلمًا وعبئًا → نفّذ بصدق وأمانة ورحمة وعدل وإحسان → تحقق من النتيجة وأصلح الانحراف.")
            appendLine("لا تنسب حديثًا أو سنة أو قصة أو فضيلة أو وعدًا إلى النبي ﷺ بلا تثبت، ولا تجعل المشهور أو الضعيف بمنزلة الثابت. عند الخلاف المعتبر لا تدّع الإجماع.")
            appendLine("لا تحوّل القرآن أو السنة أو البركة أو الدعاء إلى خوارزمية تقنية أو ضمان نتيجة مادية. الطب والهندسة والفيزياء والبرمجة والإدارة وسائر الوسائل تُحسم بأقوى دليل وخبرة متاحة داخل الحدود الشرعية والأخلاقية.")
            appendLine("الاقتداء بالنبي ﷺ في طريقة العمل يعني - بقدر ما ثبت وصلته بالمقام - حفظ الصدق والأمانة والرحمة والعدل والوفاء بالحقوق وحسن المعاملة، مع عدم اختلاق حكم جديد أو نسبة غير متحققة.")
            append(HakimPropheticKnowledgePolicy.promptContext(raw))
            if (a.exactSourceVerification) appendLine("هذه المهمة تحتاج تحققًا نصيًا/مصدرًا موثوقًا قبل أي نسبة جازمة إلى القرآن أو السنة أو واقعة نبوية محددة.")
            if (a.worldlyEvidenceRequired) appendLine("هذه مهمة وسائل دنيوية: لا تستبدل الدليل الفني بالاستدلال الديني، بل اجعل الدليل الفني خادمًا للغاية المشروعة.")
        }.take(14000)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("quran_is_highest_normative_source", true)
        .put("authentic_sunnah_is_authoritative_explanation_and_guidance", true)
        .put("prophetic_example_applies_to_method_and_conduct", true)
        .put("prophetic_knowledge_policy", HakimPropheticKnowledgePolicy.status())
        .put("exact_attribution_requires_verification", true)
        .put("revelation_distinct_from_tafsir_fiqh_sirah_and_ijtihad", true)
        .put("worldly_facts_and_means_require_domain_evidence", true)
        .put("no_religious_technical_mystification", true)
        .put("recognized_disagreement_respected", true)
        .put("higher_value_cannot_be_bought_by_lower_level_gain", true)
}
