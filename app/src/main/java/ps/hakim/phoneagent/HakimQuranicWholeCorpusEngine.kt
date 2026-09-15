package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * محرك القرآن كله لحكيم.
 * يضمن أن طلب الاستقراء الشامل يمر فعليًا على السور الـ١١٤ من corpus محلي متحقق،
 * من غير إجبار كل سورة على كل مسألة ومن غير تحويل البركة إلى خاصية تقنية.
 */
object HakimQuranicWholeCorpusEngine {
    const val VERSION = "QURANIC-WHOLE-CORPUS-OPERATING-2026-09-15-v1"

    private val operatingAxes = listOf(
        "صدق النسبة والحقيقة وعدم اختلاق الدليل",
        "العدل وعدم الظلم أو التحيز لمجرد المنفعة",
        "الرحمة ودفع الضرر وحفظ كرامة الإنسان",
        "الأمانة وحفظ الحقوق والخصوصية والعهد",
        "مشروعية الغاية والوسيلة وعدم إعانة المحرم",
        "الإتقان والأخذ بالأسباب والعلم والخبرة في الوسائل الدنيوية",
        "المسؤولية وعدم تجاوز سلطة المستخدم أو أثره الجوهري"
    )

    data class MissionAttestation(
        val inherited: Boolean,
        val all114InNormativeScope: Boolean,
        val wholeCorpusRequested: Boolean,
        val verifiedCorpusReady: Boolean,
        val sourceVerificationRequired: Boolean,
        val wholeCorpusScanReady: Boolean,
        val noForcedSurahRelevance: Boolean,
        val barakahSpiritualNotTechnical: Boolean,
        val worldlyMeansEvidenceBased: Boolean,
        val axes: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("inherited", inherited)
            .put("all_114_in_normative_scope", all114InNormativeScope)
            .put("whole_corpus_requested", wholeCorpusRequested)
            .put("verified_corpus_ready", verifiedCorpusReady)
            .put("source_verification_required", sourceVerificationRequired)
            .put("whole_corpus_scan_ready", wholeCorpusScanReady)
            .put("no_forced_surah_relevance", noForcedSurahRelevance)
            .put("barakah_spiritual_not_technical", barakahSpiritualNotTechnical)
            .put("worldly_means_evidence_based", worldlyMeansEvidenceBased)
            .put("operating_axes", JSONArray(axes))
    }

    fun attestMission(context: Context, goal: String): MissionAttestation {
        HakimQuranicInvariantKernel.requireInherited("quranic_whole_corpus_mission")
        val corpus = HakimQuranicCorpusPolicy.assess(goal)
        val religious = HakimReligiousIntegrity.assess(goal)
        val ready = HakimVerifiedQuranCorpus.isReady(context)
        val sourceRequired = corpus.requiresSourceVerification || religious.exactSourceRequired
        return MissionAttestation(
            inherited = true,
            all114InNormativeScope = HakimQuranicCorpusPolicy.allSurahNumbers.size == 114,
            wholeCorpusRequested = corpus.wholeCorpusRequested,
            verifiedCorpusReady = ready,
            sourceVerificationRequired = sourceRequired,
            wholeCorpusScanReady = !corpus.wholeCorpusRequested || ready,
            noForcedSurahRelevance = true,
            barakahSpiritualNotTechnical = true,
            worldlyMeansEvidenceBased = true,
            axes = operatingAxes
        )
    }

    fun scanAllSurahs(context: Context, query: String, limit: Int = 120): HakimVerifiedQuranCorpus.WholeCorpusScan {
        HakimQuranicInvariantKernel.requireInherited("quranic_whole_corpus_scan")
        return HakimVerifiedQuranCorpus.scanAllSurahs(context, query, limit)
    }

    fun promptContext(context: Context, goal: String): String {
        val a = attestMission(context, goal)
        return buildString {
            appendLine("[محرك القرآن كله — تشغيل السور الـ١١٤]")
            appendLine("جميع سور القرآن الـ١١٤ داخل مجال الهداية والميزان الحاكم. لا تنتقِ ما يوافق نتيجة مسبقة، ولا تُجبر كل سورة على كل مسألة.")
            appendLine("قبل كل قرار راجع محاور التشغيل: ${a.axes.joinToString("؛ ")}.")
            if (a.wholeCorpusRequested) {
                if (a.verifiedCorpusReady) {
                    appendLine("طُلب استقراء شامل: افحص corpus المحلي المتحقق عبر السور الـ١١٤ كلها، ثم استخدم فقط المواضع ذات الصلة المثبتة مع سياقها ومصدرها.")
                } else {
                    appendLine("طُلب استقراء شامل لكن corpus المحلي المتحقق غير جاهز: لا تدّع فحص السور كلها؛ تحقّق من المصدر الرسمي أولًا ثم أعد الاستقراء.")
                }
            }
            if (a.sourceVerificationRequired) appendLine("هذه المهمة تحتاج تحققًا مصدريًا قبل نسبة نص أو تفسير أو سبب نزول أو معنى محدد إلى الوحي.")
            appendLine("البركة معنى شرعي يُطلب بالطاعة والنية الصالحة والدعاء والعمل المشروع والإتقان؛ ليست مقياس أداء أو خوارزمية أو ضمانًا لنتيجة تقنية أو دنيوية.")
            appendLine("في البرمجة والطب والهندسة وسائر الأسباب الدنيوية: اختر الوسيلة بالعلم والدليل والخبرة، تحت الميزان الشرعي والأخلاقي، ولا تنسب نجاح التقنية إلى سر خفي في النص.")
        }.take(7000)
    }

    fun status(context: Context): JSONObject {
        val ready = HakimVerifiedQuranCorpus.isReady(context)
        return JSONObject()
            .put("version", VERSION)
            .put("all_114_surahs_in_scope", HakimQuranicCorpusPolicy.allSurahNumbers.size == 114)
            .put("verified_whole_corpus_ready", ready)
            .put("whole_corpus_scan_available", ready)
            .put("no_cherry_picking", true)
            .put("no_forced_surah_relevance", true)
            .put("barakah_is_spiritual_not_technical", true)
            .put("worldly_means_evidence_based", true)
            .put("source_layers_separated", true)
            .put("operating_axes", JSONArray(operatingAxes))
    }
}
