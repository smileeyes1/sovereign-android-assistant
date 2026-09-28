package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * طبقة الإشراف التنفيذي المرئية.
 *
 * لا تكشف تفكير النماذج الداخلي. تحفظ فقط مراحل تشغيل قابلة للمراجعة:
 * فهم المقصد، التخطيط، التوجيه، التنفيذ، التحقق، الإصلاح، الانتظار، الاكتمال.
 */
object HakimExecutiveLoop {
    private const val PREFS = "hakim_executive_loop"
    private const val MAX_EVENTS = 24
    private const val MAX_EXECUTION_WINDOW_MS = 10L * 60L * 1000L
    private const val MAX_SAME_UNCHANGED_REASON = 2

    enum class Phase {
        UNDERSTANDING,
        PLANNING,
        ROUTING,
        EXECUTING,
        VERIFYING,
        REPAIRING,
        WAITING_EXTERNAL,
        COMPLETE,
        GATED,
        CANCELLED
    }

    data class Session(
        val id: String,
        val goal: String,
        val acceptance: String,
        val cycle: Int,
        val phase: Phase
    )

    fun start(context: Context, rawGoal: String, acceptance: String): Session {
        val id = "goal-" + UUID.randomUUID().toString().take(12)
        val goal = rawGoal.trim().take(4000)
        val criteria = acceptance.trim().ifBlank { "إظهار أثر واضح قابل للتحقق للمستخدم" }.take(4000)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putString("session_id", id)
            .putString("goal", goal)
            .putString("acceptance", criteria)
            .putInt("cycle", 1)
            .putString("phase", Phase.UNDERSTANDING.name)
            .putBoolean("active", true)
            .putLong("started_at", System.currentTimeMillis())
            .putLong("last_material_gain_at", System.currentTimeMillis())
            .putString("events", "[]")
            .apply()
        HakimGoalSupervisor.begin(context, id, criteria, "android-candidate", goal)
        record(context, Phase.UNDERSTANDING, "فهم المقصد وتثبيت معيار الاكتمال")
        return current(context)!!
    }

    fun current(context: Context): Session? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return null
        val id = p.getString("session_id", "").orEmpty()
        if (id.isBlank()) return null
        val phase = runCatching {
            Phase.valueOf(p.getString("phase", Phase.UNDERSTANDING.name).orEmpty())
        }.getOrDefault(Phase.UNDERSTANDING)
        return Session(
            id = id,
            goal = p.getString("goal", "").orEmpty(),
            acceptance = p.getString("acceptance", "").orEmpty(),
            cycle = p.getInt("cycle", 1),
            phase = phase
        )
    }

    fun record(context: Context, phase: Phase, detail: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]")) }.getOrElse { JSONArray() }
        val event = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("phase", phase.name)
            .put("detail", detail.trim().take(300))
            .put("cycle", p.getInt("cycle", 1))
        events.put(event)
        while (events.length() > MAX_EVENTS) events.remove(0)
        p.edit()
            .putString("events", events.toString())
            .putString("phase", phase.name)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun advanceCycle(context: Context, reason: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val startedAt = p.getLong("started_at", now)
        if (now - startedAt > MAX_EXECUTION_WINDOW_MS) {
            record(
                context,
                Phase.GATED,
                "انتهت ميزانية التنفيذ الزمنية الحالية؛ تُحفظ الحالة ويحتاج الاستئناف إلى مسار/نافذة تنفيذ جديدة، لا إلى تكرار أعمى."
            )
            return false
        }

        val normalized = reason.trim().replace(Regex("\\s+"), " ").take(300)
        val previous = p.getString("last_cycle_reason", "").orEmpty()
        val sameCount = if (previous == normalized && normalized.isNotBlank()) {
            p.getInt("same_cycle_reason_count", 0) + 1
        } else {
            1
        }

        if (sameCount > MAX_SAME_UNCHANGED_REASON) {
            HakimGoalSupervisor.toolFailed(
                context,
                "repeated_without_causal_change:" + normalized
            )
            record(
                context,
                Phase.GATED,
                "تكرر السبب نفسه دون تغيير سببي؛ يُمنع تكرار المحاولة ويلزم مسار مختلف أو دليل جديد."
            )
            p.edit()
                .putString("last_cycle_reason", normalized)
                .putInt("same_cycle_reason_count", sameCount)
                .apply()
            return false
        }

        val next = p.getInt("cycle", 1) + 1
        p.edit()
            .putInt("cycle", next)
            .putString("last_cycle_reason", normalized)
            .putInt("same_cycle_reason_count", sameCount)
            .apply()
        record(context, Phase.REPAIRING, normalized.ifBlank { "إعادة التقدير وتغيير الوسيلة" })
        return true
    }

    fun noteMaterialGain(context: Context, evidence: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_material_gain_at", System.currentTimeMillis())
            .putString("last_material_gain", evidence.take(1000))
            .putInt("same_cycle_reason_count", 0)
            .apply()
    }

    fun complete(
        context: Context,
        evidence: String,
        stage: HakimGoalSupervisor.EvidenceStage = HakimGoalSupervisor.EvidenceStage.UI_OBSERVED
    ): Boolean {
        record(context, Phase.VERIFYING, "التحقق من الأثر")
        val goal = current(context)?.goal.orEmpty()
        val accepted = HakimEvidencePolicy.accepts(goal, stage)

        HakimGoalSupervisor.recordEvidence(
            context,
            stage.name,
            evidence.take(4000),
            effectVerified = accepted
        )

        if (!accepted || !HakimGoalSupervisor.canClose(context)) {
            record(
                context,
                Phase.GATED,
                "الدليل الحالي لا يكفي لإغلاق هذا المقصد عالي الأثر؛ يلزم مستوى تحقق أعلى."
            )
            return false
        }

        HakimAcceptanceGate.markDelivered(context)
        noteMaterialGain(context, evidence)
        record(context, Phase.COMPLETE, "تحقق معيار الاكتمال واجتازت النتيجة بوابة الاعتماد")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("active", false).putLong("completed_at", System.currentTimeMillis()).apply()
        return true
    }

    fun waitExternal(context: Context, providerLabel: String) {
        record(context, Phase.WAITING_EXTERNAL, "بانتظار أثر من " + providerLabel.take(80) + "؛ فتح القناة وحده ليس نجاحًا")
        HakimGoalSupervisor.recover(
            context,
            HakimGoalSupervisor.Recovery.WAIT_RESUMABLE,
            "external_provider_dispatched",
            "external_result_or_user_return"
        )
    }

    /**
     * ACTION_SEND/share intents do not define a contract that returns an AI answer.
     * Therefore a successful launch is a handoff, not an executing/waiting state.
     */
    fun externalHandoff(context: Context, providerLabel: String) {
        record(
            context,
            Phase.GATED,
            "تم تسليم المحتوى إلى " + providerLabel.take(80) +
                "؛ لا توجد قناة رجوع مضمونة للنتيجة، لذلك لم تُعتبر المهمة مكتملة أو منتظرة."
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("active", false)
            .putLong("external_handoff_at", System.currentTimeMillis())
            .apply()
    }

    fun cancel(context: Context) {
        record(context, Phase.CANCELLED, "ألغى المستخدم العملية")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("active", false).putLong("cancelled_at", System.currentTimeMillis()).apply()
    }

    fun latestOperationText(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]")) }.getOrElse { JSONArray() }
        if (events.length() == 0) {
            return HakimCloudContinuity.compactText(context)
                ?.let { "حالة العملية: $it" }
                ?: "حالة العملية: لا توجد عملية جارية"
        }
        val e = events.optJSONObject(events.length() - 1) ?: return "حالة العملية: لا توجد عملية جارية"
        val phase = e.optString("phase")
        val ageMs = (System.currentTimeMillis() - p.getLong("updated_at", 0L)).coerceAtLeast(0L)
        if (p.getBoolean("active", false) && ageMs > 120_000L &&
            phase != Phase.WAITING_EXTERNAL.name && phase != Phase.GATED.name
        ) {
            return "حالة العملية: انقطع تحديث ${label(phase)} منذ ${ageMs / 60_000L} دقيقة؛ لم يثبت الاكتمال"
        }
        return "حالة العملية: ${label(phase)}" +
            if (phase == Phase.GATED.name) "؛ راجع سبب التوقف في المحادثة" else ""
    }

    /** Safe relay summary: phases and timing only, never goals or stored event details. */
    fun publicStatus(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updatedAt = p.getLong("updated_at", 0L).coerceAtLeast(0L)
        val phase = p.getString("phase", "").orEmpty()
            .takeIf { raw -> Phase.values().any { it.name == raw } }.orEmpty()
        val active = p.getBoolean("active", false)
        val stale = active && updatedAt > 0L &&
            System.currentTimeMillis() - updatedAt > 120_000L &&
            phase != Phase.WAITING_EXTERNAL.name && phase != Phase.GATED.name
        val state = when {
            updatedAt == 0L -> "idle"
            stale -> "stale"
            phase == Phase.GATED.name -> "blocked"
            phase == Phase.WAITING_EXTERNAL.name -> "waiting"
            phase == Phase.COMPLETE.name -> "completed"
            phase == Phase.CANCELLED.name -> "cancelled"
            active -> "active"
            else -> "idle"
        }
        return JSONObject()
            .put("state", state)
            .put("phase", phase)
            .put("cycle", p.getInt("cycle", 1).coerceAtLeast(1))
            .put("updated_at_ms", updatedAt)
    }

    fun operationText(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]")) }.getOrElse { JSONArray() }
        val cloudLines = HakimCloudContinuity.panelLines(context)
        if (events.length() == 0) {
            return if (cloudLines.isEmpty()) {
                "لا توجد عملية جارية"
            } else {
                (listOf("لا توجد عملية محلية جارية", "— الاستمرارية السحابية —") + cloudLines)
                    .joinToString("\n")
            }
        }

        val phase = p.getString("phase", "").orEmpty()
        val goal = p.getString("goal", "").orEmpty().trim().replace(Regex("\\s+"), " ").take(180)
        val lastGain = p.getString("last_material_gain", "").orEmpty()
            .trim().replace(Regex("\\s+"), " ").take(180)
        val latestDetail = events.optJSONObject(events.length() - 1)
            ?.optString("detail").orEmpty().trim().replace(Regex("\\s+"), " ").take(180)
        val fabricState = runCatching {
            HakimExecutionFabric.status(context).optString("state", "UNKNOWN")
        }.getOrDefault("UNKNOWN")

        val lines = mutableListOf<String>()
        if (goal.isNotBlank()) lines += "المقصد: $goal"
        lines += "المرحلة: ${label(phase)} · محاولة ${p.getInt("cycle", 1).coerceAtLeast(1)}"
        lines += "قناة التنفيذ: " + when (fabricState) {
            "ONLINE" -> "متصلة"
            "DEGRADED" -> "متاحة جزئيًا"
            "OFFLINE" -> "غير متصلة — الحالة محفوظة للاستئناف"
            else -> "قيد التحقق"
        }
        if (lastGain.isNotBlank()) lines += "آخر تقدم مثبت: $lastGain"
        if ((phase == Phase.GATED.name || phase == Phase.WAITING_EXTERNAL.name) && latestDetail.isNotBlank()) {
            lines += (if (phase == Phase.GATED.name) "المانع: " else "الانتظار: ") + latestDetail
        }
        lines += "الخطوة التالية: ${nextStepLabel(phase)}"
        if (cloudLines.isNotEmpty()) {
            lines += "— الاستمرارية السحابية —"
            lines += cloudLines
        }

        val start = (events.length() - 4).coerceAtLeast(0)
        for (i in start until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val at = android.text.format.DateFormat.getTimeFormat(context)
                .format(Date(e.optLong("at", 0L)))
            lines += "$at · ${label(e.optString("phase"))}"
        }
        lines += latestOperationText(context)
        return lines.joinToString("\n")
    }

    private fun nextStepLabel(raw: String): String = when (raw) {
        Phase.UNDERSTANDING.name -> "تثبيت معيار الاكتمال ثم اختيار الخطة"
        Phase.PLANNING.name -> "اختيار المسار الأقل عبئًا والأعلى أثرًا"
        Phase.ROUTING.name -> "بدء التنفيذ عبر المسار المختار"
        Phase.EXECUTING.name -> "التحقق من الأثر وعدم الاكتفاء ببدء التنفيذ"
        Phase.VERIFYING.name -> "إغلاق المقصد بالدليل أو الانتقال إلى الإصلاح"
        Phase.REPAIRING.name -> "إعادة التنفيذ بعد تغيير سببي ثم إعادة الاختبار"
        Phase.WAITING_EXTERNAL.name -> "الاستئناف عند وصول الأثر دون إعادة الطلب"
        Phase.GATED.name -> "معالجة المانع المثبت أو اختيار مسار بديل مأذون"
        Phase.COMPLETE.name -> "لا توجد خطوة تالية؛ النتيجة مكتملة"
        Phase.CANCELLED.name -> "لا توجد خطوة تالية؛ العملية ملغاة"
        else -> "تحديث حالة العملية"
    }

    fun providerInstruction(context: Context, raw: String): String {
        return HakimIntentDirector.build(context, raw).instruction
    }

    private fun label(raw: String): String = when (raw) {
        Phase.UNDERSTANDING.name -> "فهم المقصد"
        Phase.PLANNING.name -> "اختيار الخطة"
        Phase.ROUTING.name -> "اختيار المحرك/الأداة"
        Phase.EXECUTING.name -> "تنفيذ"
        Phase.VERIFYING.name -> "تحقق"
        Phase.REPAIRING.name -> "إصلاح وإعادة المحاولة"
        Phase.WAITING_EXTERNAL.name -> "انتظار أثر خارجي"
        Phase.COMPLETE.name -> "اكتمل"
        Phase.GATED.name -> "مانع مثبت"
        Phase.CANCELLED.name -> "أُلغي"
        else -> "غير معروف"
    }
}
