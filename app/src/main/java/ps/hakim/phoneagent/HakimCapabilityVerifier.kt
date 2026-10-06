package ps.hakim.phoneagent

import org.json.JSONObject

/** التحقق منفصل عن التنفيذ؛ نجاح الدالة وحده لا يساوي تحقق النتيجة. */
object HakimCapabilityVerifier {
    fun verify(capabilityId: String, result: JSONObject): JSONObject {
        if (!result.optBoolean("ok", false)) {
            return JSONObject()
                .put("verified", false)
                .put("reason", result.optString("error", "executor_reported_failure").take(120))
        }
        val verified = when (capabilityId) {
            "system.status" ->
                result.optBoolean("orchestrator", false) && result.optJSONObject("execution_fabric") != null
            "browser.read" -> result.optJSONObject("page") != null
            "ui.observe" -> result.has("nodes")
            "notifications.read" -> result.has("notifications")
            "termux.status" ->
                result.optBoolean("termux_local_control", false) &&
                result.optBoolean("fixed_profiles_only", false) &&
                !result.optBoolean("arbitrary_shell_exposed", true)
            else -> false
        }
        return JSONObject()
            .put("verified", verified)
            .put("reason", if (verified) "expected_observation_present" else "verification_contract_not_satisfied")
    }
}
