from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit("P0: " + msg)

job = text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
health = text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("HakimHealthBeacon.sendAsync" in job,
        "watchdog يجب أن يرسل الصحة دون حبس JobScheduler")
require("HakimHealthBeacon.sendNow" not in job,
        "watchdog ما زال يستدعي الإرسال الشبكي المتزامن")
require("THREAD_PRIORITY_BACKGROUND" in job,
        "خيط watchdog ليس منخفض الأولوية")
require("worker?.interrupt()" in job and "onStopJob" in job,
        "إيقاف JobScheduler لا يوقف عامل watchdog")
require('putString("last_connection_watchdog_state"' in job,
        "حالة watchdog غير قابلة للرصد")
require('putLong("last_connection_watchdog_duration_ms"' in job,
        "مدة watchdog غير قابلة للقياس")
require('"coalesced"' in job,
        "تشغيل watchdog المتداخل لا يُدمج")
require("jobFinished(params, false)" in job,
        "watchdog لا يغلق JobScheduler عند اكتمال العمل")
require("asyncInFlight = AtomicBoolean(false)" in health,
        "نبضات الصحة يمكن أن تتكدس بلا حارس")
require("compareAndSet(false, true)" in health,
        "حارس نبضات الصحة لا يمنع التوازي")
require("asyncInFlight.set(false)" in health,
        "حارس نبضة الصحة لا يتحرر بعد الانتهاء")
require('name = "HakimHealthBeacon"' in health,
        "خيط الصحة غير قابل للتشخيص بالاسم")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20052,
        "إصلاح watchdog يجب ألا يرجع قبل ٢٠٠٥٢")
candidate = int(m.group(1))
require(policy["current_field_version"] >= 20052,
        "خط الميدان أقدم من ٢٠٠٥٢ المثبت فعليًا")
require(policy["current_candidate_version"] == candidate,
        "سياسة التوقيع لا تطابق المرشح الحالي")
require(policy["field_evidence"]["version_code"] == policy["current_field_version"],
        "دليل الميدان لا يطابق current_field_version")
require(policy.get("superseded_field_failures", [{}])[0].get("version_code") == 20050,
        "فشل ٢٠٠٥٠ التاريخي ضاع من سجل الانحدار")
require(any(x.get("version_code") == 20052 for x in policy.get("superseded_field_failures", [])),
        "فشل ٢٠٠٥٢ الميداني غير محفوظ")

print("HAKIM_20052_BOUNDED_WATCHDOG=PASS")
print("HAKIM_20052_HEALTH_BEACON_COALESCING=PASS")
print("HAKIM_FIELD_FAILURE_HISTORY_PRESERVED=PASS")
