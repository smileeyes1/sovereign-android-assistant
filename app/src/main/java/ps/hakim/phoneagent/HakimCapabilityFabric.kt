package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * نسيج القدرات R28.
 *
 * المدخل العام في هذه النسخة قراءة فقط. الأفعال الكتابية تبقى في المسارات
 * الحالية وبوابات الموافقة؛ القدرات المخططة لا تصبح متاحة بمجرد تسجيلها.
 */
object HakimCapabilityFabric {
    const val VERSION = "CAPABILITY-FABRIC-2026-10-05-r28"
    private val REQUEST_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")

    fun status(context: Context): JSONObject = JSONObject()
        .put("ok", true)
        .put("capability_fabric", true)
        .put("version", VERSION)
        .put("read_only_r28", true)
        .put("new_android_permissions", false)
        .put("writes_blocked", true)
        .put("unknown_capability_denied", true)
        .put("registry", HakimCapabilityRegistry.summary(context))
        .put("capability_kernel", HakimCapabilityKernel.status(context))
        .put("last_receipt", HakimExecutionReceipt.last(context))

    fun executeReadOnly(context: Context, requestId: String, payload: JSONObject): JSONObject {
        val startedAt = System.currentTimeMillis()
        if (!REQUEST_ID.matches(requestId)) {
            return JSONObject().put("ok", false).put("error", "invalid_request_id")
        }

        val capabilityId = payload.optString("capability").trim()
        val contract = HakimCapabilityRegistry.contract(capabilityId)
            ?: return denied(context, requestId, capabilityId, "unknown_capability", startedAt)

        if (!contract.implemented) return denied(context, requestId, capabilityId, "planned_not_implemented", startedAt)
        if (!contract.readOnly) return denied(context, requestId, capabilityId, "write_capability_blocked_in_r28", startedAt)
        if (!contract.remoteReadable) return denied(context, requestId, capabilityId, "remote_read_not_allowed", startedAt)

        val availability = HakimCapabilityRegistry.availability(context, capabilityId)
        if (!availability.optBoolean("available", false)) {
            return denied(context, requestId, capabilityId, availability.optString("reason", "runtime_dependency_unavailable"), startedAt)
        }

        val authorization = HakimCapabilityKernel.authorize(
            context,
            contract.kernelCapability,
            capabilityId,
            "capability_read"
        )
        if (authorization.optString("verdict") != "allow") {
            return denied(context, requestId, capabilityId, "kernel_" + authorization.optString("verdict"), startedAt)
        }

        val result = when (capabilityId) {
            "system.status" -> HakimOrchestrator.status(context).put("ok", true)
            "browser.read" -> HakimService.readActiveBrowser()
            "ui.observe" -> {
                val service = HakimAccessibilityService.instance
                if (service == null) JSONObject().put("ok", false).put("error", "accessibility_unavailable")
                else JSONObject().put("ok", true).put("nodes", service.uiSnapshot())
            }
            "notifications.read" -> {
                if (!HakimNotificationListener.isConnected()) JSONObject().put("ok", false).put("error", "notification_listener_unavailable")
                else JSONObject().put("ok", true).put("notifications", HakimNotificationListener.snapshot())
            }
            "termux.status" -> HakimTermuxControl.status(context).put("ok", true)
            "video.capabilities" -> HakimVideoFactory.capabilities(context)
            "video.plan" -> HakimVideoFactory.plan(payload)
            "video.runtime" -> HakimVideoRuntime.probe(context)
            "video.job.read" -> HakimVideoRuntime.job(context, payload.optString("job_id"))
            else -> JSONObject().put("ok", false).put("error", "adapter_not_available")
        }

        val verification = HakimCapabilityVerifier.verify(capabilityId, result)
        val verified = verification.optBoolean("verified", false)
        val state = when {
            verified -> "VERIFIED"
            result.optBoolean("ok", false) -> "EXECUTED_UNVERIFIED"
            else -> "FAILED"
        }
        val receipt = HakimExecutionReceipt.record(
            context, requestId, capabilityId, state, result, verified,
            verification.optString("reason", "unknown"), startedAt
        )

        if (!verified) {
            return JSONObject()
                .put("ok", false)
                .put("error", if (result.optBoolean("ok", false)) "verification_failed" else result.optString("error", "execution_failed"))
                .put("capability", capabilityId)
                .put("receipt", receipt)
        }
        return JSONObject()
            .put("ok", true)
            .put("capability", capabilityId)
            .put("result", result)
            .put("receipt", receipt)
    }

    private fun denied(
        context: Context,
        requestId: String,
        capabilityId: String,
        reason: String,
        startedAt: Long
    ): JSONObject {
        val result = JSONObject().put("ok", false).put("error", reason)
        val receipt = HakimExecutionReceipt.record(
            context, requestId, capabilityId, "DENIED", result, false, reason, startedAt
        )
        return JSONObject()
            .put("ok", false)
            .put("error", reason)
            .put("capability", capabilityId)
            .put("receipt", receipt)
    }
}
