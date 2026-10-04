package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * الموجّه المركزي لقدرات حكيم.
 *
 * لا يمنح صلاحية جديدة ولا يغيّر بوابات الموافقة. يقرر فقط إن كانت العملية
 * معروفة وقابلة للتنفيذ بالقدرات الحية الحالية، ويغلق الأثر العالي عند وجود
 * عطل حرج مثبت.
 */
object HakimToolRouter {
    const val VERSION = "TOOL-ROUTER-2026-10-04-v1"

    data class Decision(
        val allowed: Boolean,
        val requiresApproval: Boolean,
        val capability: String,
        val reason: String
    ) {
        fun json(): JSONObject = JSONObject()
            .put("allowed", allowed)
            .put("requires_approval", requiresApproval)
            .put("capability", capability)
            .put("reason", reason)
    }

    private val READ_ONLY = setOf(
        "status", "ui", "notifications", "screenshot",
        "browser_read", "chatgpt_read", "chatgpt_navigate"
    )
    private val MUTATING = setOf("action", "launch", "browser_back", "chatgpt_action")
    private val SUPPORTED = READ_ONLY + MUTATING

    fun decide(context: Context, operation: String): Decision {
        val op = operation.trim()
        if (op !in SUPPORTED) return Decision(false, false, "none", "unsupported_operation")

        val approval = op in MUTATING
        if (approval && !HakimFaultContainment.canExecuteHighImpact(context)) {
            return Decision(false, true, capability(op), "fault_containment_blocked")
        }

        val available = when (op) {
            "ui", "screenshot", "action" -> HakimAccessibilityService.instance != null
            "notifications" -> HakimNotificationListener.isConnected()
            "browser_read", "browser_back", "chatgpt_read", "chatgpt_navigate", "chatgpt_action" ->
                HakimUnifiedRelay.isConfigured(context)
            else -> true
        }
        return if (available) {
            Decision(true, approval, capability(op), "ready")
        } else {
            Decision(false, approval, capability(op), "capability_unavailable")
        }
    }

    fun status(context: Context): JSONObject {
        val capabilities = JSONArray()
        SUPPORTED.sorted().forEach { op ->
            capabilities.put(JSONObject().put("operation", op).put("decision", decide(context, op).json()))
        }
        return JSONObject()
            .put("tool_router", true)
            .put("version", VERSION)
            .put("least_privilege", true)
            .put("approval_boundary_preserved", true)
            .put("permissions_added", false)
            .put("capabilities", capabilities)
    }

    private fun capability(op: String): String = when (op) {
        "ui", "screenshot", "action" -> "accessibility"
        "notifications" -> "notification_listener"
        "browser_read", "browser_back" -> "browser"
        "chatgpt_read", "chatgpt_navigate", "chatgpt_action" -> "chatgpt_web"
        "launch" -> "android_launch"
        "status" -> "status"
        else -> "none"
    }
}
