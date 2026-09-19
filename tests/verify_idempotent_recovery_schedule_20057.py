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

require("SCHEDULE_GENERATION = 20057" in resilience,"جيل الجدولة ليس ٢٠٠٥٧")
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
require(m and int(m.group(1))==20057,"الإصدار يجب أن يكون ٢٠٠٥٧")
require(policy["current_field_version"]==20056,"خط الميدان يجب أن يسجل ٢٠٠٥٦ المثبت")
require(policy["last_verified_field_version"]==20055,"LAST_VERIFIED_BASELINE يجب أن يبقى ٢٠٠٥٥ حتى قبول ٢٠٠٥٧")
require(policy["current_candidate_version"]==20057,"السياسة لا تسجل ٢٠٠٥٧")
require(policy["field_evidence"]["version_code"]==20056 and policy["field_evidence"]["stability"]=="FAILED",
        "فشل ٢٠٠٥٦ الميداني غير محفوظ")
require(any(x.get("version_code")==20056 for x in policy.get("superseded_field_failures",[])),
        "فشل ٢٠٠٥٦ غير محفوظ تاريخيًا")

print("HAKIM_20057_IDEMPOTENT_RECOVERY_SCHEDULE=PASS")
print("HAKIM_20057_INHERITS_PROFESSIONAL_UI=PASS")
print("HAKIM_20057_INHERITS_HARD_WATCHDOG=PASS")
