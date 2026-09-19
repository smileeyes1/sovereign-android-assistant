package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * حارس معاملة تنفيذية محلية يمنع تكرار الخطة بعد تنفيذ محتمل أو انقطاع.
 * لا يخزن قيم الأفعال الخام؛ يحفظ بصمة مشفرة وبيانات حالة غير حساسة فقط.
 */
object HakimExecutionTransaction {
    private const val META_PREFS = "hakim_execution_txn_meta"
    private const val SECURE_PREFS = "hakim_execution_txn_secure"
    private const val KEY_FINGERPRINT = "fingerprint"

    data class Gate(val proceed: Boolean, val requiresVerification: Boolean, val reason: String)

    fun prepare(
        context: Context,
        mission: HakimMissionLedger.Mission?,
        plan: HakimReasoningProtocol.Plan
    ): Gate {
        if (plan.phase != "execute" || plan.actions.isEmpty()) return Gate(true, false, "لا توجد معاملة تغيير")
        val missionId = mission?.id.orEmpty()
        val fp = fingerprint(mission, plan)
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val previousFp = HakimSecureStore.get(context, SECURE_PREFS, KEY_FINGERPRINT).orEmpty()
        val previousMission = meta.getString("mission_id", "").orEmpty()
        val state = meta.getString("state", "NONE").orEmpty()

        if (previousMission == missionId && previousFp == fp) {
            return when (state) {
                "PREPARED" -> Gate(true, false, "المعاملة محضرة ولم يثبت بدء فعل؛ يسمح بالاستمرار")
                "ATTEMPTING", "EXECUTING", "AWAITING_VERIFICATION" ->
                    Gate(false, true, "منع إعادة تنفيذ خطة قد تكون أحدثت أثرًا؛ يلزم التحقق من الحالة الفعلية أولًا")
                "VERIFIED" -> Gate(false, false, "الخطة نفسها متحققة سابقًا؛ لا تُكرر")
                else -> Gate(false, true, "حالة المعاملة غير محسومة؛ تحقق قبل إعادة التنفيذ")
            }
        }

        if (!HakimSecureStore.put(context, SECURE_PREFS, KEY_FINGERPRINT, fp)) {
            return Gate(false, true, "تعذر حفظ بصمة المعاملة بأمان؛ التنفيذ مغلق احترازيًا")
        }
        meta.edit()
            .putString("mission_id", missionId)
            .putString("plan_id", plan.planId.take(96))
            .putString("state", "PREPARED")
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        return Gate(true, false, "المعاملة محضرة دون تنفيذ")
    }

    fun markAttempting(context: Context, mission: HakimMissionLedger.Mission?, plan: HakimReasoningProtocol.Plan) =
        update(context, mission, plan, "ATTEMPTING")

    fun markProgressed(context: Context, mission: HakimMissionLedger.Mission?, plan: HakimReasoningProtocol.Plan) =
        update(context, mission, plan, "EXECUTING")

    fun markAwaitingVerification(context: Context, mission: HakimMissionLedger.Mission?, plan: HakimReasoningProtocol.Plan) =
        update(context, mission, plan, "AWAITING_VERIFICATION")

    fun markRecovering(context: Context, mission: HakimMissionLedger.Mission?, plan: HakimReasoningProtocol.Plan) =
        update(context, mission, plan, "RECOVER")

    fun markVerified(context: Context, mission: HakimMissionLedger.Mission?) {
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        if (mission != null && meta.getString("mission_id", "") != mission.id) return
        meta.edit()
            .putString("state", "VERIFIED")
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("state", meta.getString("state", "NONE"))
            .put("plan_id", meta.getString("plan_id", ""))
            .put("mission_id_present", !meta.getString("mission_id", "").isNullOrBlank())
            .put("fingerprint_present", !HakimSecureStore.get(context, SECURE_PREFS, KEY_FINGERPRINT).isNullOrBlank())
            .put("raw_action_values_persisted", false)
            .put("duplicate_execution_fail_closed", true)
    }

    private fun update(
        context: Context,
        mission: HakimMissionLedger.Mission?,
        plan: HakimReasoningProtocol.Plan,
        state: String
    ) {
        if (plan.phase != "execute" || plan.actions.isEmpty()) return
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        if (mission != null && meta.getString("mission_id", "") != mission.id) return
        meta.edit()
            .putString("state", state)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    private fun fingerprint(
        mission: HakimMissionLedger.Mission?,
        plan: HakimReasoningProtocol.Plan
    ): String {
        val canonical = buildString {
            append(mission?.goalHash.orEmpty())
            append('|').append(plan.phase)
            append('|').append(plan.idempotencyKey)
            append('|').append(plan.planId)
            plan.actions.forEach { action ->
                append('|').append(action.type)
                append('|').append(action.args.toString())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
