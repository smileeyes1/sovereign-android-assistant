package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نواة مصنع حكيم للمادة.
 *
 * الغاية: تحويل مقصد "اصنع" إلى عقد تصنيع قابل للتتبع، مع منع الخلط بين
 * التصميم الرقمي والمنتج المادي. لا تُرقّى الحالة إلى MATERIAL_VERIFIED
 * إلا بوجود دليل ميداني فعلي من قناة تصنيع/قياس.
 */
object HakimMaterialFactory {
    const val VERSION = "MATERIAL-FACTORY-2026-09-23-v1"

    enum class Scale(val label: String) {
        DIGITAL("رقمي"),
        MACRO("ماكرو/مرئي"),
        MICRO("ميكروي"),
        NANO("نانوي"),
        ATOMIC("ذري")
    }

    enum class State {
        INTENT,
        SPECIFIED,
        DESIGNED,
        SIMULATED,
        FABRICATION_READY,
        FABRICATED_UNVERIFIED,
        MATERIAL_VERIFIED
    }

    data class FactoryPlan(
        val goal: String,
        val scale: Scale,
        val state: State,
        val requiresPhysicalFabrication: Boolean,
        val requiredCapabilities: List<String>,
        val acceptanceEvidence: List<String>,
        val blockers: List<String>
    ) {
        fun asJson(): JSONObject = JSONObject()
            .put("factory_version", VERSION)
            .put("goal", goal)
            .put("scale", scale.name)
            .put("scale_label", scale.label)
            .put("state", state.name)
            .put("requires_physical_fabrication", requiresPhysicalFabrication)
            .put("required_capabilities", JSONArray(requiredCapabilities))
            .put("acceptance_evidence", JSONArray(acceptanceEvidence))
            .put("blockers", JSONArray(blockers))
    }

    fun plan(context: Context, raw: String): FactoryPlan {
        val goal = raw.trim().replace(Regex("\\s+"), " ").take(1000)
        val lower = goal.lowercase()
        val scale = detectScale(lower)
        val requiresPhysical = scale != Scale.DIGITAL || containsPhysicalIntent(lower)

        val required = mutableListOf(
            "تعريف المنتج ومعيار القبول",
            "تصميم قابل للتصنيع",
            "تحقق/محاكاة قبل التصنيع",
            "سجل أدلة وحالة قابل للتتبع"
        )

        if (requiresPhysical) {
            required += listOf(
                "مادة أولية مناسبة",
                "طاقة وأداة تصنيع فعلية",
                "قناة تنفيذ مادية مأذونة",
                "قياس ميداني مستقل للمنتج النهائي"
            )
        }

        when (scale) {
            Scale.MICRO -> required += "معدات تصنيع/قياس ميكروية متخصصة"
            Scale.NANO -> required += "معدات تصنيع وقياس نانوية متخصصة"
            Scale.ATOMIC -> required += "منظومة مختبرية للتحكم/القياس الذري"
            else -> Unit
        }

        val evidence = mutableListOf(
            "ملف التصميم أو المواصفات النهائية",
            "نتيجة تحقق التصميم/المحاكاة"
        )
        if (requiresPhysical) {
            evidence += listOf(
                "دليل تنفيذ مادي فعلي",
                "قياسات من المنتج نفسه",
                "اختبار قبول بعد التصنيع",
                "تطابق النسخة المختبرة مع النسخة المسلّمة"
            )
        }

        val blockers = mutableListOf<String>()
        if (requiresPhysical) {
            blockers += "لا يجوز وصف التصميم الرقمي وحده بأنه منتج مادي مصنوع"
        }
        if (scale == Scale.NANO || scale == Scale.ATOMIC) {
            blockers += "المعدات المتخصصة ليست مفترضة؛ يجب إثبات توفرها وصلاحيتها قبل التنفيذ"
        }

        val plan = FactoryPlan(
            goal = if (goal.isBlank()) "غير محددة" else goal,
            scale = scale,
            state = State.INTENT,
            requiresPhysicalFabrication = requiresPhysical,
            requiredCapabilities = required.distinct(),
            acceptanceEvidence = evidence.distinct(),
            blockers = blockers.distinct()
        )

        context.getSharedPreferences("hakim_material_factory", Context.MODE_PRIVATE)
            .edit()
            .putString("factory_version", VERSION)
            .putString("last_plan", plan.asJson().toString())
            .putLong("last_plan_at", System.currentTimeMillis())
            .apply()

        return plan
    }

    fun governedContext(context: Context, raw: String): String {
        val p = plan(context, raw)
        return buildString {
            appendLine("[مصنع حكيم للمادة — عقد التصنيع]")
            appendLine("الإصدار: $VERSION")
            appendLine("المقياس: ${p.scale.label}")
            appendLine("الحالة الابتدائية: ${p.state.name}")
            appendLine("المنتج المادي مطلوب: ${if (p.requiresPhysicalFabrication) "نعم" else "لا"}")
            appendLine("قاعدة حاكمة: التصميم≠التصنيع، التصنيع≠التحقق، والمنتج المادي لا يُعلن مصنوعًا أو ناجحًا بلا دليل ميداني من النسخة نفسها.")
            appendLine("السلسلة: مقصد→مواصفات→تصميم→محاكاة/تحقق→جاهزية تصنيع→تنفيذ مادي→قياس→اختبار قبول→انحدار→تسليم نفس المختبر.")
            appendLine("استخدم أبسط مقياس تصنيع يحقق الغاية؛ لا تنتقل للميكرو/النانو/الذري لمجرد كونه أعلى تقنية.")
            appendLine("لا تفترض توفر طابعة أو CNC أو مختبر أو نانو/ذري أو مواد أو قناة تحكم؛ أثبت التوفر قبل ترقية الحالة.")
            appendLine("[القدرات المطلوبة]")
            p.requiredCapabilities.forEach { appendLine("• $it") }
            appendLine("[دليل القبول]")
            p.acceptanceEvidence.forEach { appendLine("• $it") }
            if (p.blockers.isNotEmpty()) {
                appendLine("[حواجز منع الادعاء]")
                p.blockers.forEach { appendLine("• $it") }
            }
        }.take(6000)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences("hakim_material_factory", Context.MODE_PRIVATE)
        return JSONObject()
            .put("material_factory", true)
            .put("version", p.getString("factory_version", VERSION))
            .put("last_plan", p.getString("last_plan", ""))
            .put("last_plan_at", p.getLong("last_plan_at", 0L))
            .put("physical_claim_requires_field_evidence", true)
            .put("digital_design_is_not_physical_product", true)
    }

    private fun detectScale(s: String): Scale = when {
        listOf("ذرة", "ذري", "ذرات", "atomic").any { s.contains(it) } -> Scale.ATOMIC
        listOf("نانو", "nanometer", "nanometre", "nano").any { s.contains(it) } -> Scale.NANO
        listOf("ميكرو", "micron", "micro").any { s.contains(it) } -> Scale.MICRO
        containsPhysicalIntent(s) -> Scale.MACRO
        else -> Scale.DIGITAL
    }

    private fun containsPhysicalIntent(s: String): Boolean =
        listOf(
            "اصنع", "صنع", "منتج", "جهاز", "روبوت", "قطعة", "ملموس",
            "طابعة", "طباعة ثلاثية", "cnc", "ليزر", "حساس", "محرك", "مادة"
        ).any { s.contains(it) }
}
