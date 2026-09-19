from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

resilience=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt")
corpus=text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("@Volatile private var sharedDbHelper: Db? = null" in corpus,
        "قاعدة القرآن لا تملك helper مشتركًا")
require("private fun database(context: Context): Db" in corpus,
        "دالة helper المشتركة مفقودة")
require("sharedDbHelper ?: Db(context.applicationContext).also" in corpus,
        "helper المشترك لا يُنشأ من applicationContext")
require("Db(context.applicationContext).readableDatabase" not in corpus,
        "ما زال query ينشئ SQLiteOpenHelper مؤقتًا")
require("Db(app).readableDatabase" not in corpus,
        "ما زال corpus scan/status ينشئ helper مؤقتًا")
require("val db = Db(context).writableDatabase" not in corpus,
        "ما زال مسار الكتابة ينشئ helper مؤقتًا")
require(corpus.count("private class Db(")==1,"تعريف Db غير متوقع")

require("SCHEDULE_GENERATION = 20058" in resilience,"جيل الجدولة يجب أن يكون ٢٠٠٥٨")
require("SCHEDULE_HEARTBEAT_MS = 60_000L" in resilience,"نبض self-heal غير محدد")
require("installScheduleHeartbeat(app)" in resilience,"self-heal لا يبدأ مع resilience")
require("handler.postDelayed(this, SCHEDULE_HEARTBEAT_MS)" in resilience,
        "نبض self-heal لا يعيد نفسه")
require('last_recovery_schedule_heartbeat_at' in resilience,"نبض self-heal غير مرصود")
require("schedule(context)" in resilience,"network callback لا يتحقق من الجدولة")
recover_block=resilience[resilience.index("fun recover"):resilience.index("fun status")]
require("schedule(app)" in recover_block,"مسارات recovery لا تتحقق من وجود job")
require("scheduler.getPendingJob(JOB_ID)" in resilience and '"kept_existing"' in resilience,
        "self-heal فقد idempotency")

m=re.search(r"versionCode\s+(\d+)",build)
require(m and int(m.group(1))>=20058,"إصلاح ٢٠٠٥٨ يجب أن يبقى موروثًا")
require(policy["current_field_version"]>=20058,"خط الميدان يجب أن يسجل ٢٠٠٥٨ المثبت أو أحدث")
require(policy["last_verified_field_version"]==20055,"LAST_VERIFIED_BASELINE يجب أن يبقى ٢٠٠٥٥")
require(policy["current_candidate_version"]==int(m.group(1)),"السياسة لا تطابق المرشح الحالي")
require(policy["field_evidence"]["version_code"]>=20058 and policy["field_evidence"]["stability"]=="FAILED",
        "فشل خط الميدان الحالي غير محفوظ")
fail57=next((x for x in policy.get("superseded_field_failures",[]) if x.get("version_code")==20057),None)
require(fail57 is not None and fail57.get("sqlite_leak_warnings_observed",0)>=1,
        "دليل تسريب SQLite في ٢٠٠٥٧ غير محفوظ تاريخيًا")
require(any(x.get("version_code")==20058 for x in policy.get("superseded_field_failures",[])),
        "فشل استدامة ٢٠٠٥٨ غير محفوظ تاريخيًا")

print("HAKIM_20058_QURAN_DB_HELPER_LIFECYCLE=PASS")
print("HAKIM_20058_JOB_SELFHEAL=PASS")
print("HAKIM_20058_INHERITS_IDEMPOTENT_SCHEDULE=PASS")
