package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * جذر الثقة القرآني لحكيم.
 * «من النواة إلى الحافة/ذريًا» هنا استعارة معمارية: كل طبقة حرجة ترث نفس الثوابت القيمية والشرعية.
 * لا يعني ذلك أن قوانين الذرة أو النواة أو الطب أو الهندسة تُستخرج من القرآن؛ الوسائل الدنيوية تُحسم بالدليل العلمي والتجريبي.
 */
object HakimQuranicInvariantKernel {
    const val VERSION = "QURANIC-KERNEL-TO-EDGE-2026-09-14-v1"

    data class Attestation(
        val scope: String,
        val inherited: Boolean,
        val quranicNormativeDefault: Boolean,
        val authenticSunnahGuidance: Boolean,
        val worldlyMeansEvidenceBased: Boolean,
        val exactRevelationRequiresVerification: Boolean,
        val noTechnicalMystification: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("scope", scope)
            .put("inherited", inherited)
            .put("quranic_normative_default", quranicNormativeDefault)
            .put("authentic_sunnah_guidance", authenticSunnahGuidance)
            .put("worldly_means_evidence_based", worldlyMeansEvidenceBased)
            .put("exact_revelation_requires_verification", exactRevelationRequiresVerification)
            .put("no_technical_mystification", noTechnicalMystification)
    }

    fun attest(scope: String): Attestation {
        val normalized = scope.trim().take(120)
        return Attestation(
            scope = normalized,
            inherited = normalized.isNotBlank(),
            quranicNormativeDefault = true,
            authenticSunnahGuidance = true,
            worldlyMeansEvidenceBased = true,
            exactRevelationRequiresVerification = true,
            noTechnicalMystification = true
        )
    }

    fun requireInherited(scope: String): Attestation {
        val a = attest(scope)
        check(a.inherited) { "طبقة بلا هوية لا يمكن اعتماد وراثتها للحاكم القرآني" }
        return a
    }

    fun promptContext(scope: String): String {
        val a = requireInherited(scope)
        return buildString {
            appendLine("[جذر الثقة القرآني — ${a.scope}]")
            appendLine("وراثة حاكمة من النواة إلى الحافة: القرآن الكريم أصل الهداية والميزان الأعلى للقيم والمعنى والغاية والحدود الشرعية، والسنة الصحيحة بيان وهدي، داخل قواعد المنصة والسلامة والحقوق.")
            appendLine("كل فهم وخطة ووكيل وقرار وتنفيذ وتحقق وتعلم وتعافٍ يرث هذا الميزان؛ لا يجوز لمسار جديد أن يتجاوز الثوابت لمجرد السرعة أو المنفعة.")
            appendLine("افصل الوحي عن فهم البشر وعن الحقيقة التجريبية. في الذرة والنواة والطب والهندسة والبرمجة وسائر الظواهر الدنيوية: استخدم العلم والتجربة والمصادر الموثوقة، ولا تنسب قانونًا أو نتيجة تقنية إلى القرآن بلا دليل.")
            appendLine("نص القرآن أو السنة الدقيق يحتاج تحققًا من مصدر موثوق قبل الجزم؛ والبركة والدعاء لا يستبدلان السبب المشروع والعمل المتقن.")
        }.take(2600)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("kernel_to_edge_inheritance", true)
        .put("atomic_inheritance_is_architectural_metaphor", true)
        .put("quranic_normative_default", true)
        .put("authentic_sunnah_guidance", true)
        .put("worldly_science_remains_evidence_based", true)
        .put("no_claim_quran_encodes_nuclear_or_atomic_physics", true)
        .put("exact_revelation_requires_verification", true)
        .put("no_technical_mystification", true)
}
