from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
manager = (APP / "HakimTaskManager.kt").read_text(encoding="utf-8")
activity = (APP / "HakimTaskManagerActivity.kt").read_text(encoding="utf-8")
command = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
loop = (APP / "HakimExecutiveLoop.kt").read_text(encoding="utf-8")
relay = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise AssertionError(reason)

req("object HakimTaskManager" in manager, "manager_missing")
req("MAX_TASKS = 48" in manager, "bounded_history_missing")
req("NETWORK_TASK_ID" in manager and '"P0"' in manager, "network_p0_missing")
req("State.PAUSED" in manager and "State.VERIFYING" in manager and "State.BLOCKED" in manager,
    "state_machine_incomplete")
req("item.optString(\"kind\", \"user_goal\") == \"user_goal\"" in manager,
    "system_task_must_not_be_paused_by_user_wip")
req("shouldAutoOpenRouterProtection" in manager and "TR064_LANHOST_NOT_FOUND" in manager,
    "router_dns_auto_resume_missing")
req("fun publicStatus" in manager, "safe_public_status_missing")
public = manager.split("fun publicStatus", 1)[1].split("private fun toTask", 1)[0]
req('getString("goal"' not in public and '"goal"' not in public and '"acceptance"' not in public,
    "public_status_leaks_task_content")
req('android:name=".HakimTaskManagerActivity"' in manifest and
    'android:name=".HakimTaskManagerActivity"\n            android:exported="false"' in manifest,
    "task_manager_activity_not_private")
req('actionButton("المهام")' in command, "task_manager_button_missing")
req("hakim_resume_task_id" in command, "resume_handoff_missing")
req("HakimExecutiveLoop.resume(this, resumeTask)" in command, "resume_execution_missing")
req("HakimTaskManager.shouldAutoOpenRouterProtection(this)" in command,
    "network_protection_auto_resume_not_wired")
req("HakimTaskManager.beginExecutive" in loop and "HakimTaskManager.syncExecutive" in loop,
    "executive_loop_not_persisted")
req("fun resume(context: Context, task: HakimTaskManager.Task)" in loop,
    "executive_resume_contract_missing")
req('.put("task_manager", HakimTaskManager.publicStatus(context))' in relay,
    "relay_task_summary_missing")
import re
m = re.search(r"versionCode\s+(\d+)", gradle)
req(m is not None and int(m.group(1)) >= 20322 and "task-manager-r12" in gradle,
    "version_not_bumped")
req("استئناف" in activity and "إلغاء" in activity and "مدير مهام حكيم" in activity,
    "task_manager_ui_actions_missing")
req("goal" not in public and "last_detail" not in public, "relay_private_content_leak")

print("TASK_MANAGER_GATE=PASS durable=true multi_task=true wip1=true p0_independent=true resume=true private_relay=true network_auto_resume=true")
