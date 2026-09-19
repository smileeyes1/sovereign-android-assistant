from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")
def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit("P0: " + msg)

job=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("HARD_TIMEOUT_MS = 15_000L" in job, "لا يوجد حد زمني صريح للwatchdog")
require("AtomicBoolean(false)" in job, "إغلاق JobScheduler غير محمي من الإنهاء المزدوج")
require("completion.compareAndSet(false, true)" in job, "الإغلاق الأحادي غير مثبت")
require("HakimConstraintDoctor.runAsync" in job, "ConstraintDoctor ما زال يحبس العامل")
require("HakimConstraintDoctor.run(app" not in job, "لا يزال هناك تشغيل تشخيص متزامن")
require("HakimHealthBeacon.sendAsync" in job, "نبضة الصحة ليست async")
require("HakimSelfCheck.runAsync" in job, "الفحص الذاتي ليس async")
require('name = "HakimConnectionWatchdogTimeout"' in job, "حارس المهلة غير قابل للتشخيص")
require('recordState("timed_out"' in job, "انتهاء المهلة غير مرصود")
require("thread.interrupt()" in job, "المهلة لا تقاطع العامل")
require("jobFinished(params, false)" in job, "المهلة لا تغلق JobScheduler")
require('"coalesced"' in job, "التشغيل المتداخل غير مدمج")

m=re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20054, "إصلاح الحد الصريح يجب ألا يرجع قبل ٢٠٠٥٤")
candidate=int(m.group(1))
require(policy["current_field_version"] >= 20054, "خط الميدان أقدم من ٢٠٠٥٤ المثبت")
require(policy["current_candidate_version"] == candidate, "السياسة لا تطابق المرشح الحالي")
require(policy["field_evidence"]["version_code"] == policy["current_field_version"], "دليل الميدان لا يطابق current_field_version")
require(any(x.get("version_code") == 20054 for x in policy.get("superseded_field_failures", [])),
        "فشل ٢٠٠٥٤ الميداني غير محفوظ")

print("HAKIM_20054_HARD_JOB_DEADLINE=PASS")
print("HAKIM_20054_SECONDARY_WORK_ASYNC=PASS")
print("HAKIM_20054_SINGLE_COMPLETION=PASS")
