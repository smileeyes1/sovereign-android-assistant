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
require(m and int(m.group(1))>=20055,"إصلاح self-reschedule يجب ألا يرجع قبل ٢٠٠٥٥")
candidate=int(m.group(1))
field=int(policy["current_field_version"])
require(policy["current_candidate_version"]==candidate,"السياسة لا تطابق المرشح الحالي")
require(candidate>field,"المرشح يجب أن يزيد عن خط الميدان")
require(field>=20055,"خط الميدان يجب أن يحفظ ٢٠٠٥٥ المقبول أو أحدث")
require(policy["field_evidence"]["version_code"]==field,"دليل الميدان لا يطابق current_field_version")
require(policy.get("last_verified_field_version",0)>=20055,"LAST_VERIFIED_BASELINE ٢٠٠٥٥ غير محفوظ")
require(any(x.get("version_code")==20054 for x in policy.get("superseded_field_failures",[])),
        "فشل self-reschedule في ٢٠٠٥٤ غير محفوظ تاريخياً")

print("HAKIM_20055_NO_WATCHDOG_SELF_RESCHEDULE=PASS")
print("HAKIM_20055_INHERITS_HARD_DEADLINE=PASS")
