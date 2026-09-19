package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * فهرس موارد قرآنية موثقة المصدر.
 *
 * هذا الفهرس لا يخلط طبقات المعرفة: نص المصحف والقراءات شيء، والتفسير وغريب القرآن
 * والتجويد موارد علمية/تعليمية مستقلة. وجود المورد في الفهرس يثبت هوية الملف وبصمته
 * المنشورة فقط، ولا يجعل التفسير أو الشرح نصًا من الوحي ولا يغني عن سياق الاستدلال.
 */
object HakimQuranicResourceCatalog {
    const val VERSION = "QURANIC-RESOURCE-CATALOG-KFGQPC-2026-09-15-v1"
    const val OFFICIAL_HOST = "qurancomplex.gov.sa"
    const val OFFICIAL_SOURCE = "مجمع الملك فهد لطباعة المصحف الشريف"

    enum class Kind { MUSHAF_TEXT, QIRAAT, TAFSIR, GHAREEB, TAJWEED }

    data class Resource(
        val id: String,
        val title: String,
        val kind: Kind,
        val narration: String?,
        val md5: String,
        val sha1: String,
        val interpretive: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("kind", kind.name)
            .put("narration", narration ?: JSONObject.NULL)
            .put("md5", md5.lowercase())
            .put("sha1", sha1.lowercase())
            .put("interpretive", interpretive)
            .put("official_source", OFFICIAL_SOURCE)
            .put("official_host", OFFICIAL_HOST)
    }

    val resources: List<Resource> = listOf(
        Resource("KFGQPC_HAFS_SMART_V6", "خط الرسم العثماني — رواية حفص — للأجهزة الذكية", Kind.MUSHAF_TEXT, "حفص عن عاصم", "53d82b553e5fe919ca1a732e35bf4eb0", "1dbeae3847880b1c21a956dcfdc0a2d9d490e729", false),
        Resource("KFGQPC_HAFS_UNICODE_V13", "خط الرسم العثماني — رواية حفص — Unicode", Kind.MUSHAF_TEXT, "حفص عن عاصم", "cf6841aea5b1d1fd70d032b43ff08278", "36ea5ab0d7ea1702f17ff43f9b50924cccd77ebf", false),
        Resource("KFGQPC_WARSH_UNICODE", "خط الرسم العثماني — رواية ورش", Kind.QIRAAT, "ورش عن نافع", "4701e8bbf053098220cf2cf4cda206a1", "44ecea8feb23817fdc01a8ee2162a6a0cf08cae7", false),
        Resource("KFGQPC_SHUBAH_UNICODE", "خط الرسم العثماني — رواية شعبة", Kind.QIRAAT, "شعبة عن عاصم", "5cda29121bf0d7234e039002e1fbf600", "8d66bdf0cab96dc7d1032792c19f77980ca6682a", false),
        Resource("KFGQPC_QALOUN_UNICODE", "خط الرسم العثماني — رواية قالون", Kind.QIRAAT, "قالون عن نافع", "964208ff04c8aadd3ddc1be262d8cfd3", "81733666be17742e13c9fa4c7d26d42b1adc67c8", false),
        Resource("KFGQPC_DOURI_UNICODE", "خط الرسم العثماني — رواية الدوري", Kind.QIRAAT, "الدوري عن أبي عمرو", "a60bdd18397b3e27e4617478968a35c8", "8049482f04b4ff1053a7859f96b2b113b9771efb", false),
        Resource("KFGQPC_SOUSI_UNICODE", "خط الرسم العثماني — رواية السوسي", Kind.QIRAAT, "السوسي عن أبي عمرو", "1bf6023e29b7622a52b6171232c17096", "e52dbc6d8b43797a8faa0fd1ec1d8e5000265674", false),
        Resource("KFGQPC_TAFSIR_MUYASSAR", "التفسير الميسر للقرآن الكريم", Kind.TAFSIR, null, "5601682965e32f4dd6992c7600fdccc3", "5f533113c2f54f32eded734bb49e6a5837965722", true),
        Resource("KFGQPC_GHAREEB_MUYASSAR", "الميسر في غريب القرآن الكريم", Kind.GHAREEB, null, "7e22381eedb152ee7ed6488f2395c6cd", "055a908c6ec7f06912c33bd00920406c665cc5f9", true),
        Resource("KFGQPC_TAJWEED_MUYASSAR", "التجويد الميسر", Kind.TAJWEED, null, "b4a265a810c0ce4a722019791910b67e", "d2496382fc5e843ccb693b94dd19407eaa174bea", true)
    )

    fun byId(id: String): Resource? = resources.firstOrNull { it.id == id }

    fun matchingDigest(md5: String, sha1: String): Resource? = resources.firstOrNull {
        it.md5.equals(md5.trim(), ignoreCase = true) && it.sha1.equals(sha1.trim(), ignoreCase = true)
    }

    fun promptContext(): String = buildString {
        appendLine("[فهرس الموارد القرآنية الموثقة]")
        appendLine("المصدر الرسمي المفضّل: $OFFICIAL_SOURCE. الموارد الموثقة في الفهرس تشمل نص حفص، وعددًا من الروايات، والتفسير الميسر، وغريب القرآن، والتجويد الميسر ببصمات منشورة.")
        appendLine("لا تُسَوِّ بين الطبقات: المصحف/القراءة نصٌّ منقول مضبوط، أما التفسير والغريب والتجويد فموارد علمية تُنسب إلى مصدرها ولا تُدمج في ألفاظ الوحي.")
        appendLine("عدم وجود مورد في هذا الفهرس لا يعني بطلانه؛ يعني فقط أنه لم يُعتمد هنا بعد، فيلزم مصدر متخصص موثوق قبل الجزم.")
    }.take(2200)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("official_source", OFFICIAL_SOURCE)
        .put("official_host", OFFICIAL_HOST)
        .put("resource_count", resources.size)
        .put("mushaf_text_resources", resources.count { it.kind == Kind.MUSHAF_TEXT })
        .put("qiraat_resources", resources.count { it.kind == Kind.QIRAAT })
        .put("tafsir_resources", resources.count { it.kind == Kind.TAFSIR })
        .put("ghareeb_resources", resources.count { it.kind == Kind.GHAREEB })
        .put("tajweed_resources", resources.count { it.kind == Kind.TAJWEED })
        .put("revelation_and_interpretation_separated", true)
        .put("unknown_resource_requires_new_verification", true)
        .put("resources", JSONArray(resources.map { it.toJson() }))
}
