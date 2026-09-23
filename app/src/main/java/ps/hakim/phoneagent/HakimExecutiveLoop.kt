package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    private const val MAX_CYCLES = 6

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
            .putString("events", "[]")
            .apply()
        HakimGoalSupervisor.begin(context, id, criteria, "android-candidate")
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
        val next = p.getInt("cycle", 1) + 1
        if (next > MAX_CYCLES) {
            record(context, Phase.GATED, "بلغت الدورة الحد الآمن؛ يلزم أثر جديد أو قناة تنفيذ مختلفة")
            return false
        }
        p.edit().putInt("cycle", next).apply()
        record(context, Phase.REPAIRING, reason.ifBlank { "إعادة التقدير وتغيير الوسيلة" })
        return true
    }

    fun complete(context: Context, evidence: String) {
        record(context, Phase.VERIFYING, "التحقق من الأثر")
        HakimGoalSupervisor.recordEvidence(
            context,
            HakimGoalSupervisor.EvidenceStage.UI_OBSERVED.name,
            evidence.take(4000),
            effectVerified = true
        )
        record(context, Phase.COMPLETE, "تحقق معيار الاكتمال")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("active", false).putLong("completed_at", System.currentTimeMillis()).apply()
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

    fun cancel(context: Context) {
        record(context, Phase.CANCELLED, "ألغى المستخدم العملية")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("active", false).putLong("cancelled_at", System.currentTimeMillis()).apply()
    }

    fun latestOperationText(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]")) }.getOrElse { JSONArray() }
        if (events.length() == 0) return "جاهز"
        val e = events.optJSONObject(events.length() - 1) ?: return "جاهز"
        val phase = e.optString("phase")
        val mark = when (phase) {
            Phase.COMPLETE.name -> "✓"
            Phase.GATED.name, Phase.CANCELLED.name -> "■"
            Phase.WAITING_EXTERNAL.name -> "…"
            else -> "•"
        }
        return mark + " " + label(phase) + " — " + e.optString("detail")
    }

    fun operationText(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]")) }.getOrElse { JSONArray() }
        if (events.length() == 0) return "لا توجد عملية جارية"
        val lines = mutableListOf<String>()
        val start = (events.length() - 7).coerceAtLeast(0)
        for (i in start until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val phase = e.optString("phase")
            val mark = when (phase) {
                Phase.COMPLETE.name -> "✓"
                Phase.GATED.name, Phase.CANCELLED.name -> "■"
                Phase.WAITING_EXTERNAL.name -> "…"
                else -> "•"
            }
            lines += mark + " " + label(phase) + " — " + e.optString("detail")
        }
        return lines.joinToString("\n")
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
        else -> raw
    }
}
