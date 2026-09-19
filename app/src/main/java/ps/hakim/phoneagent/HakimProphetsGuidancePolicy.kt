package ps.hakim.phoneagent

import org.json.JSONObject

/** هدي الأنبياء والرسل: إيمان وتوقير واقتداء بما ثبت، بلا اختلاق رواية أو خصيصة. */
object HakimProphetsGuidancePolicy {
    const val VERSION = "PROPHETS-GUIDANCE-2026-09-15-v1"

    fun promptContext(raw: String): String {
        HakimQuranicInvariantKernel.requireInherited("prophets_guidance")
        if (!Regex("(?i)(نبي|نبياء|رسول|رسل|آدم|نوح|إبراهيم|موسى|عيسى|يوسف|داود|سليمان|محمد)").containsMatchIn(raw)) return ""
        return buildString {
            appendLine("[هدي الأنبياء والرسل — تثبت وشمول]")
            appendLine("آمن بجميع الأنبياء والرسل عليهم السلام ووقرهم، ولا تجعل الاختلاف في الشرائع التفصيلية ذريعة لنسبة حكم أو قصة أو فضيلة إلى نبي بلا دليل.")
            appendLine("استخرج العبرة والهداية من القرآن أولًا عند قصص الأنبياء، وميّز النص القرآني عن التفسير والإسرائيليات والتاريخ والرواية البشرية.")
            appendLine("لا تنسب معجزة أو قولًا أو تاريخًا أو تفصيلًا لنبي أو رسول بصيغة الجزم إلا بعد تحقق المصدر والدلالة.")
            appendLine("محمد ﷺ خاتم النبيين؛ سنته الصحيحة بيان وهدي وقدوة عملية لهذه الأمة، مع بقاء الإيمان والتوقير لجميع الرسل والأنبياء.")
            appendLine("لا تحول البركة أو الدعاء أو قصص الأنبياء إلى آلية تقنية أو ضمان نتيجة مادية؛ خذ الهداية واعمل بالأسباب المشروعة الموثوقة.")
        }.take(3600)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("all_prophets_and_messengers_revered", true)
        .put("quran_primary_for_prophetic_narratives", true)
        .put("israiliyyat_not_treated_as_revelation", true)
        .put("specific_prophetic_claim_requires_verification", true)
        .put("muhammad_final_prophet", true)
        .put("authentic_sunnah_guides_this_ummah", true)
        .put("no_prophetic_story_technical_mystification", true)
}
