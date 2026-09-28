from pathlib import Path

app = Path(__file__).resolve().parents[1] / "app/src/main/java/ps/hakim/phoneagent"
relay = (app / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
service = (app / "HakimService.kt").read_text(encoding="utf-8")
read_only = relay.split("private val READ_ONLY_OPS", 1)[1].split("private val ALLOWED_OPS", 1)[0]
handler = relay.split("private fun handleCarrier", 1)[1].split("private fun decryptCarrier", 1)[0]
approval = relay.split("fun handleApproval(context: Context, requestId: String, approved: Boolean)", 1)[1].split("private fun decodePayload", 1)[0]
pending = relay.split("private fun takePending(context: Context, requestId: String)", 1)[1].split("private fun showApproval", 1)[0]
back = service.split("private fun backBrowser()", 1)[1].split("private fun sendSnapshot", 1)[0]

def require(condition, reason):
    if not condition:
        raise SystemExit("APPROVED_BROWSER_BACK=FAIL reason=" + reason)

require('"browser_back"' not in read_only, "approval_bypass")
require('setOf("action", "launch", "browser_back")' in relay, "operation_not_allowed")
require(handler.index("claimRemoteRequest(context, requestId)") < handler.index("savePending(context, envelope, resultTopic)"),
        "duplicate_request_can_be_approved")
require("showApproval(context, requestId, op)" in handler, "phone_approval_missing")
require("pollExecutor.execute { loop(app, generation) }" in relay and
        "controlExecutor.execute {" in approval and
        "pollExecutor.execute" not in approval,
        "approved_action_starved_by_poll_loop")
require('remove("$requestId.envelope").remove("$requestId.result_topic").commit()' in pending,
        "approval_can_replay_after_process_restart")
require(approval.count('envelope.optLong("expires_at_ms", 0L) <= System.currentTimeMillis()') >= 2,
        "queued_approval_can_execute_after_expiry")
require('"browser_back" -> HakimService.backActiveBrowser()' in relay, "missing_dispatch")
require("!target.canGoBack()" in back and "activeBrowserTaskId != null" in back, "unsafe_navigation")
require("done.await(6, TimeUnit.SECONDS)" in back and '"browser_navigation_unverified"' in back,
        "unbounded_or_unverified_navigation")
require("val observed = readBrowser()" in back and "if (!observed.optBoolean(\"ok\", false)) return observed" in back,
        "page_privacy_or_failure_bypass")
require("currentAddress == previousAddress.get()" in back, "navigation_effect_not_checked")

print("APPROVED_BROWSER_BACK=PASS approval=true replay_guard=true bounded=true observed=true private_page_gate=true")
