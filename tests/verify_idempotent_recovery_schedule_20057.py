from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

resilience=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt")
job=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
doctor=text("app/src/main/java/ps/hakim/phoneagent/HakimConstraintDoctor.kt")
proactive=text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

gen=re.search(r"SCHEDULE_GENERATION\s*=\s*(\d+)",resilience)
require(gen and int(gen.group(1))>=20057,"إصلاح الجدولة idempotent يجب ألا يرجع قبل ٢٠٠٥٧")
require('EXTRA_SCHEDULE_GENERATION = "hakim_recovery_schedule_generation"' in resilience,
        "مفتاح جيل الجدولة مفقود")
require("scheduler.getPendingJob(JOB_ID)" in resilience,"الجدولة لا تفحص JobInfo الموجودة")
require("existing.service == component" in resilience,"الجدولة لا تتحقق من service الموجودة")
require("existingGeneration == SCHEDULE_GENERATION" in resilience,"الجدولة لا تتحقق من الجيل")
require('"kept_existing"' in resilience,"لا يوجد دليل على إبقاء الجدولة الموجودة")
require("return" in resilience[resilience.index("if (existing != null"):resilience.index("val extras = PersistableBundle")],
        "المسار idempotent لا يخرج قبل schedule جديدة")
require("PersistableBundle" in resilience and ".setExtras(extras)" in resilience,
        "JobInfo الجديدة لا تحمل جيل الجدولة")
require(resilience.count("scheduler.schedule(info)") == 1,
        "يوجد أكثر من موضع لاستبدال JobInfo داخل schedule()")
require("HARD_TIMEOUT_MS = 15_000L" in job,"حد watchdog الصريح ضاع")
require("completion.compareAndSet(false, true)" in job,"single completion ضاع")
require('reason == "periodic_watchdog"' in doctor and 'if (!invokedByConnectionWatchdog)' in doctor,
        "منع self-reschedule المباشر ضاع")
require("HakimConstraintDoctor.run(app, \"proactive_" in proactive,
        "المسار المتوازي الذي كشف العلة اختفى بدل تحصين schedule() جذريًا")

m=re.search(r"versionCode\s+(\d+)",build)
require(m and int(m.group(1))>=20057,"إصلاح الجدولة idempotent يجب ألا يرجع قبل ٢٠٠٥٧")
candidate=int(m.group(1))
field=int(policy["current_field_version"])
require(policy["current_candidate_version"]==candidate,"السياسة لا تطابق المرشح الحالي")
require(candidate>field,"المرشح يجب أن يزيد عن خط الميدان")
require(field>=20057,"خط الميدان يجب أن يحفظ ٢٠٠٥٧ المثبت أو أحدث")
require(policy["field_evidence"]["version_code"]==field,"دليل الميدان لا يطابق current_field_version")
require(policy.get("last_verified_field_version",0)>=20055,"LAST_VERIFIED_BASELINE ٢٠٠٥٥ غير محفوظ")
require(any(x.get("version_code")==20057 for x in policy.get("superseded_field_failures",[])),
        "فشل ٢٠٠٥٧ الميداني غير محفوظ")

print("HAKIM_20057_IDEMPOTENT_RECOVERY_SCHEDULE=PASS")
print("HAKIM_20057_INHERITS_PROFESSIONAL_UI=PASS")
print("HAKIM_20057_INHERITS_HARD_WATCHDOG=PASS")
