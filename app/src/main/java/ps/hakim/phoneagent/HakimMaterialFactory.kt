package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * مصنع حكيم للمادة: عقد تنفيذ متعدد المقاييس يفصل بين التصميم والتصنيع والتحقق.
 * لا يُعلن منتج مادي ناجحًا لمجرد اكتمال التصميم أو البناء البرمجي.
 */
object HakimMaterialFactory {
    const val VERSION = "MATERIAL-FACTORY-2026-09-23-v2"

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

    data class Proof(
        val fabricated: Boolean = false,
        val measured: Boolean = false,
        val acceptancePassed: Boolean = false,
        val sameArtifact: Boolean = false,
        val reference: String = ""
    )

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

    private val transitions = mapOf(
        State.INTENT to setOf(State.SPECIFIED),
        State.SPECIFIED to setOf(State.DESIGNED),
        State.DESIGNED to setOf(State.SIMULATED, State.FABRICATION_READY),
        State.SIMULATED to setOf(State.FABRICATION_READY),
        State.FABRICATION_READY to setOf(State.FABRICATED_UNVERIFIED),
        State.FABRICATED_UNVERIFIED to setOf(State.MATERIAL_VERIFIED),
        State.MATERIAL_VERIFIED to emptySet()
    )

    fun matches(raw: String): Boolean {
        val s = raw.lowercase()
        return listOf(
            "منتج مادي", "جهاز", "روبوت", "قطعة", "ملموس", "طابعة ثلاثية",
            "cnc", "ليزر", "نانو", "ذري", "ذرة", "ميكرو", "حساس", "محرك",
            "إلكترونيات", "لوحة دوائر", "تصنيع"
        ).any { s.contains(it) }
    }

    fun plan(context: Context, raw: String): FactoryPlan {
        val goal = raw.trim().replace(Regex("\\s+"), " ").take(1000)
        val lower = goal.lowercase()
        val scale = detectScale(lower)
        val requiresPhysical = scale != Scale.DIGITAL || matches(lower)

        val required = mutableListOf(
            "تعريف المنتج ومعيار القبول",
            "تصميم قابل للتصنيع",
            "تحقق أو محاكاة قبل التصنيع",
            "سجل حالة ودليل قابل للتتبع"
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
            Scale.MICRO -> required += "معدات تصنيع وقياس ميكروية متخصصة"
            Scale.NANO -> required += "معدات تصنيع وقياس نانوية متخصصة"
            Scale.ATOMIC -> required += "منظومة مختبرية للتحكم والقياس الذري"
            else -> Unit
        }

        val evidence = mutableListOf(
            "المواصفات أو التصميم النهائي",
            "نتيجة التحقق أو المحاكاة"
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
        if (requiresPhysical) blockers += "التصميم الرقمي وحده ليس منتجًا ماديًا مصنوعًا"
        if (scale == Scale.NANO || scale == Scale.ATOMIC) {
            blockers += "توفر المعدات المتخصصة وصلاحيتها يجب أن يثبت قبل التنفيذ"
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
            .putString("state", State.INTENT.name)
            .putLong("last_plan_at", System.currentTimeMillis())
            .apply()
        return plan
    }

    fun advance(context: Context, target: State, proof: Proof = Proof()): Boolean {
        val prefs = context.getSharedPreferences("hakim_material_factory", Context.MODE_PRIVATE)
        val current = runCatching {
            State.valueOf(prefs.getString("state", State.INTENT.name) ?: State.INTENT.name)
        }.getOrDefault(State.INTENT)

        if (target !in transitions.getValue(current)) return false
        if (target == State.FABRICATED_UNVERIFIED && !proof.fabricated) return false
        if (target == State.MATERIAL_VERIFIED &&
            !(proof.fabricated && proof.measured && proof.acceptancePassed && proof.sameArtifact)
        ) return false

        prefs.edit()
            .putString("state", target.name)
            .putString("last_evidence_ref", proof.reference.take(256))
            .putLong("last_transition_at", System.currentTimeMillis())
            .apply()
        return true
    }

    fun governedContext(context: Context, raw: String): String {
        val p = plan(context, raw)
        return buildString {
            appendLine("[مصنع حكيم للمادة — عقد التصنيع]")
            appendLine("الإصدار: $VERSION")
            appendLine("المقياس: ${p.scale.label}")
            appendLine("الحالة الابتدائية: ${p.state.name}")
            appendLine("المنتج المادي مطلوب: ${if (p.requiresPhysicalFabrication) "نعم" else "لا"}")
            appendLine("قاعدة حاكمة: التصميم≠التصنيع، التصنيع≠التحقق، ولا يُعلن المنتج المادي مصنوعًا أو ناجحًا بلا دليل ميداني من النسخة نفسها.")
            appendLine("السلسلة: مقصد→مواصفات→تصميم→محاكاة/تحقق→جاهزية تصنيع→تنفيذ مادي→قياس→اختبار قبول→انحدار→تسليم نفس المختبر.")
            appendLine("استخدم أبسط مقياس تصنيع يحقق الغاية؛ لا تنتقل للميكرو/النانو/الذري لمجرد كونه أعلى تقنية.")
            appendLine("لا تفترض توفر طابعة أو CNC أو مختبر أو مواد أو قناة تحكم؛ أثبت التوفر قبل ترقية الحالة.")
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
            .put("state", p.getString("state", State.INTENT.name))
            .put("last_plan", p.getString("last_plan", ""))
            .put("last_plan_at", p.getLong("last_plan_at", 0L))
            .put("last_transition_at", p.getLong("last_transition_at", 0L))
            .put("last_evidence_ref", p.getString("last_evidence_ref", ""))
            .put("physical_claim_requires_field_evidence", true)
            .put("digital_design_is_not_physical_product", true)
            .put("same_artifact_required", true)
            .put("fail_closed_promotion", true)
    }

    private fun detectScale(s: String): Scale = when {
        listOf("ذرة", "ذري", "ذرات", "atomic").any { s.contains(it) } -> Scale.ATOMIC
        listOf("نانو", "nanometer", "nanometre", "nano").any { s.contains(it) } -> Scale.NANO
        listOf("ميكرو", "micron", "micro").any { s.contains(it) } -> Scale.MICRO
        matches(s) -> Scale.MACRO
        else -> Scale.DIGITAL
    }
}
