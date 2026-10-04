package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * واجهة الأوركسترا المركزية.
 *
 * HakimTaskManager هو سجل الحالة الحي القانوني للمهام. ExecutiveLoop يبقى
 * دفتر انتقالات/أحداث وإشراف ولا يُستخدم كمخزن منافس للحالة النهائية.
 * التنفيذ الهاتفي يمر عبر Secure Relay ثم HakimToolRouter.
 */
object HakimOrchestrator {
    const val VERSION = "ORCHESTRATOR-2026-10-04-v1"

    fun recover(context: Context, reason: String): JSONObject {
        HakimExecutionFabric.recover(context, reason)
        HakimTaskManager.syncSystemTasks(context)
        val resume = HakimTaskManager.consumeResumeRequest(context)
        if (resume != null && resume.resumable) {
            HakimExecutiveLoop.resume(context, resume)
        }
        return status(context)
    }

    fun status(context: Context): JSONObject {
        val fabric = HakimExecutionFabric.status(context)
        val tasks = HakimTaskManager.publicStatus(context)
        return JSONObject()
            .put("orchestrator", true)
            .put("version", VERSION)
            .put("canonical_live_state", "HakimTaskManager")
            .put("transition_journal", "HakimExecutiveLoop")
            .put("phone_connector", "SecureRelay")
            .put("resume_after_disconnect", true)
            .put("idempotency", true)
            .put("fault_containment", HakimFaultContainment.status(context))
            .put("execution_fabric", fabric)
            .put("live_state", tasks)
            .put("operation_view", HakimExecutiveLoop.publicStatus(context))
            .put("tool_router", HakimToolRouter.status(context))
            .put("network_or_dns_policy_changed", false)
            .put("permissions_changed", false)
    }
}
