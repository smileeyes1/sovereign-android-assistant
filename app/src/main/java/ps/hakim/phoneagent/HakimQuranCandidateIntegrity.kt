package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * حارس ملفات القرآن الخارجية/المرفوعة.
 *
 * الملف الذي يقدمه المستخدم أو يأتي من الويب هو «مرشح» لا «مصحف معتمد» تلقائيًا.
 * فحص ١١٤ سورة/٦٢٣٦ آية وتسلسل الأرقام ضروري، لكنه لا يثبت أصل النص ولا مطابقته.
 * لا يُسمح للمرشح بأن يصبح مصدر MUSHAF_TEXT إلا بدليل أصل مستقل:
 * ١) تطابق بصمة إصدار رسمي مقبول، أو
 * ٢) مطابقة كاملة آيةً بآية مع corpus رسمي سبق التحقق منه.
 *
 * لا يصلح هذا الحارس نص القرآن من الذاكرة؛ عند أي اختلاف يحجر الملف ويعاد التحقق من المصدر.
 */
object HakimQuranCandidateIntegrity {
    const val VERSION = "QURAN-CANDIDATE-INTEGRITY-2026-09-15-v1"
    const val EXPECTED_SURAH_COUNT = 114
    const val EXPECTED_AYA_COUNT = 6236

    data class Evidence(
        val surahCount: Int,
        val ayahCount: Int,
        val numberingContiguous: Boolean,
        val revelationSeparatedFromCommentary: Boolean,
        val officialDigestMatched: Boolean = false,
        val fullPerAyahMatchAgainstVerifiedCorpus: Boolean = false,
        val sourceIdentityRecorded: Boolean = false
    )

    data class Admission(
        val authorizedAsMushafText: Boolean,
        val structuralCoverageOk: Boolean,
        val provenanceOk: Boolean,
        val quarantined: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("authorized_as_mushaf_text", authorizedAsMushafText)
            .put("structural_coverage_ok", structuralCoverageOk)
            .put("provenance_ok", provenanceOk)
            .put("quarantined", quarantined)
            .put("reason", reason)
    }

    fun assess(evidence: Evidence): Admission {
        HakimQuranicInvariantKernel.requireInherited("quran_candidate_integrity")
        val structural = evidence.surahCount == EXPECTED_SURAH_COUNT &&
            evidence.ayahCount == EXPECTED_AYA_COUNT &&
            evidence.numberingContiguous &&
            evidence.revelationSeparatedFromCommentary
        val provenance = evidence.sourceIdentityRecorded &&
            (evidence.officialDigestMatched || evidence.fullPerAyahMatchAgainstVerifiedCorpus)
        val authorized = structural && provenance

        val reason = when {
            !structural -> "فشل فحص البنية/التغطية: يبقى الملف محجورًا ولا يُنسب كنص مصحف معتمد."
            !evidence.sourceIdentityRecorded -> "هوية المصدر غير مثبتة: اسم الملف أو مؤلف DOCX أو الرابط لا يكفي لإثبات الأصل."
            !provenance -> "البنية وحدها لا تكفي: يلزم تطابق بصمة رسمية أو مطابقة كاملة آيةً بآية مع corpus رسمي متحقق."
            else -> "اجتاز المرشح البنية ودليل الأصل؛ يجوز تمريره فقط عبر مسار النص المتحقق دون إسقاط بقية حراس المصدر."
        }
        return Admission(authorized, structural, provenance, !authorized, reason)
    }

    fun promptContext(): String = """
        [حارس ملفات القرآن المرشحة]
        أي DOCX/PDF/TXT/صفحة ويب أو ملف يرفعه المستخدم يبقى مرشحًا محجورًا ولا يصبح نص مصحف لمجرد أنه يحتوي ١١٤ سورة أو ٦٢٣٦ رقم آية.
        فحص البنية ضروري لكنه غير كافٍ. اعتماد النص الدقيق يتطلب تطابق بصمة مصدر رسمي مقبول أو مطابقة كاملة آيةً بآية مع corpus رسمي متحقق، مع تسجيل هوية المصدر وفصل الوحي عن الشروح.
        لا تُصلح اختلافًا قرآنيًا من الذاكرة ولا تخمّن الرقم أو اللفظ؛ احفظ الأصل كما هو، سجل الاختلاف، وأعد التحقق من المصدر الرسمي.
        الملف المرشح لا يتغلب أبدًا على HakimVerifiedQuranCorpus.
    """.trimIndent()

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("candidate_files_are_quarantined_by_default", true)
        .put("structural_count_is_necessary_not_sufficient", true)
        .put("expected_surah_count", EXPECTED_SURAH_COUNT)
        .put("expected_ayah_count", EXPECTED_AYA_COUNT)
        .put("official_digest_or_full_per_ayah_match_required", true)
        .put("filename_author_or_link_is_not_provenance", true)
        .put("auto_repair_quran_from_memory_forbidden", true)
        .put("candidate_never_overrides_verified_corpus", true)
}
