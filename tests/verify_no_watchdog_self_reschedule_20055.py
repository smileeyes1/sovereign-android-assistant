from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
def text(path): return (ROOT/path).read_text(encoding="utf-8")
def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

doctor=text("app/src/main/java/ps/hakim/phoneagent/HakimConstraintDoctor.kt")
job=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require('reason == "periodic_watchdog"' in doctor, "ConstraintDoctor لا يميز مسار watchdog")
require('reason.startsWith("periodic_watchdog_")' in doctor, "أسباب watchdog الفرعية غير محمية")
require('if (!invokedByConnectionWatchdog)' in doctor, "جدولة الخدمات ليست محجوبة داخل watchdog")
require('constraint_doctor_watchdog_schedule_suppressed_at' in doctor, "منع إعادة الجدولة غير مرصود")
require("HakimConnectionResilience.schedule(app)" in doctor, "جدولة الاستعادة ضاعت كليًا")
require("HARD_TIMEOUT_MS = 15_000L" in job, "حد ٢٠٠٥٤ الصريح ضاع")
require("HakimConstraintDoctor.runAsync" in job, "ConstraintDoctor عاد متزامنًا")
require("completion.compareAndSet(false, true)" in job, "single completion ضاع")

m=re.search(r"versionCode\s+(\d+)",build)
require(m and int(m.group(1))==20055,"الإصدار يجب أن يكون ٢٠٠٥٥")
require(policy["current_field_version"]==20054,"خط الميدان يجب أن يسجل ٢٠٠٥٤")
require(policy["current_candidate_version"]==20055,"السياسة لا تسجل ٢٠٠٥٥")
require(policy["field_evidence"]["version_code"]==20054,"دليل الميدان لا يطابق ٢٠٠٥٤")
require(policy["field_evidence"]["stability_evidence"].get("root_cause","").startswith("ConstraintDoctor"),
        "سبب self-reschedule غير محفوظ")

print("HAKIM_20055_NO_WATCHDOG_SELF_RESCHEDULE=PASS")
print("HAKIM_20055_INHERITS_HARD_DEADLINE=PASS")
