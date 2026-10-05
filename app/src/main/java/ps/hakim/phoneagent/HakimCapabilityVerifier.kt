package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * تحقق متناسب مع القدرة. التنفيذ وحده لا يكفي للترقية إلى verified.
 */
object HakimCapabilityVerifier {
    fun verify(capabilityId: String, result: JSONObject): JSONObject {
        if (!result.optBoolean("ok", false)) {
            return JSONObject()
                .put("verified", false)
                .put("reason", result.optString("error", "executor_reported_failure").take(120))
        }

        val verified = when (capabilityId) {
            "system.status" ->
                result.has("execution_fabric") && result.has("capability_kernel")
            "browser.read" ->
                result.optJSONObject("page") != null
            "ui.observe" ->
                result.has("nodes")
            "notifications.read" ->
                result.has("notifications")
            else -> false
        }

        return JSONObject()
            .put("verified", verified)
            .put("reason", if (verified) "expected_observation_present" else "verification_contract_not_satisfied")
    }
}
