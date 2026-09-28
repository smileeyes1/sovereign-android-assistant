from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = root / "app/src/main/java/ps/hakim/phoneagent"
ui = (app / "CommandCenterActivity.kt").read_text(encoding="utf-8")
loop = (app / "HakimExecutiveLoop.kt").read_text(encoding="utf-8")
relay = (app / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
latest = loop.split("fun latestOperationText", 1)[1].split("fun publicStatus", 1)[0]
probe = loop.split("fun publicStatus", 1)[1].split("fun operationText", 1)[0]
local_panel = loop.split("fun operationText", 1)[1].split("fun providerInstruction", 1)[0]

def require(ok, reason):
    if not ok:
        raise SystemExit("VISIBLE_OPERATIONS=FAIL reason=" + reason)

require("operations.visibility = View.VISIBLE" in ui, "view_hidden")
require("operationsExpanded = !operationsExpanded" in ui, "cannot_expand")
require("browserHandler.postDelayed(operationRefresh, 15_000L)" in ui, "no_live_refresh")
require("browserHandler.removeCallbacks(operationRefresh)" in ui, "refresh_not_stopped")
require('e.optString("detail")' not in latest, "latest_summary_exposes_detail")
require('p.getString("goal"' not in latest, "latest_summary_exposes_goal")
require("انقطع تحديث" in latest and "لم يثبت الاكتمال" in latest, "stale_operation_looks_active")
require("Date(e.optLong(\"at\"" in local_panel, "history_has_no_time")
require('p.getString("goal"' in local_panel, "local_goal_not_visible")
require('"آخر تقدم مثبت:"' in local_panel, "last_verified_not_visible")
require('"الخطوة التالية:"' in local_panel, "next_step_not_visible")
require('"قناة التنفيذ:"' in local_panel, "execution_channel_not_visible")
require('put("operation", HakimExecutiveLoop.publicStatus(context))' in relay, "probe_missing")
require(all(f'put("{field}"' in probe for field in ("state", "phase", "cycle", "updated_at_ms")),
        "probe_missing_fields")
require('getString("goal"' not in probe and 'getString("events"' not in probe and
        'getString("acceptance"' not in probe, "probe_leaks_private_content")
require('"stale"' in probe and '"blocked"' in probe and '"waiting"' in probe,
        "probe_overstates_execution")

print("VISIBLE_OPERATIONS=PASS visible=true expandable=true timed=true stale_guard=true local_goal=true next_step=true status_probe=true relay_private=true")
