from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

manifest=text("app/src/main/AndroidManifest.xml")
chat=text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
quran=text("app/src/main/java/ps/hakim/phoneagent/HakimQuranActivity.kt")
hub=text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalHubActivity.kt")
job=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
doctor=text("app/src/main/java/ps/hakim/phoneagent/HakimConstraintDoctor.kt")
health=text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require('android:name=".HakimProfessionalHubActivity"' in manifest,"مساحة العمل غير مسجلة")
require('android:name=".HakimQuranActivity"' in manifest,"واجهة القرآن غير مسجلة")
require(manifest.count('android.intent.category.LAUNCHER')==1,"يجب بقاء مشغل واحد")
require("HakimProfessionalHubActivity::class.java" in chat,"المحادثة لا تصل لمساحة العمل")
require("HakimQuranActivity::class.java" in chat,"المحادثة لا تصل للقرآن")
require("HakimVerifiedQuranCorpus.fullCorpusScan" in quran,"بحث القرآن لا يستخدم corpus المتحقق")
require("HakimVerifiedQuranCorpus.ayah" in quran,"مرجع الآية لا يستخدم corpus المتحقق")
require("THREAD_PRIORITY_BACKGROUND" in quran and "Thread {" in quran,"بحث القرآن قد يحجب main thread")
require("البحث اللفظي ليس تفسيراً" in quran,"حد البحث اللفظي غير ظاهر")
require("HakimSelfCheck.run" in hub and "Thread {" in hub,"الفحص الاحترافي ليس خلفياً")
require("HakimUnifiedRelay.isConfigured" in hub,"المركز لا يعرض القناة الآمنة")
require("AutoUpdater.checkAsync" in hub,"المركز لا يصل للتحديث الموثوق")

# وراثة استقرار ٢٠٠٥٥.
require("HARD_TIMEOUT_MS = 15_000L" in job,"فقد حد watchdog الصريح")
require("HakimHealthBeacon.sendAsync" in job and "HakimHealthBeacon.sendNow" not in job,"فقد health async")
require("HakimConstraintDoctor.runAsync" in job,"ConstraintDoctor عاد متزامناً")
require('reason == "periodic_watchdog"' in doctor and 'if (!invokedByConnectionWatchdog)' in doctor,
        "فقد منع self-reschedule")
require("asyncInFlight = AtomicBoolean(false)" in health,"فقد coalescing نبضات الصحة")

m=re.search(r"versionCode\s+(\d+)",build)
require(m and int(m.group(1))==20056,"الإصدار يجب أن يكون ٢٠٠٥٦")
require(policy["current_field_version"]==20055,"خط الميدان يجب أن يكون ٢٠٠٥٥")
require(policy["current_candidate_version"]==20056,"السياسة لا تسجل ٢٠٠٥٦")
require(policy["field_evidence"]["version_code"]==20055 and policy["field_evidence"]["stability"]=="PASS",
        "قبول ٢٠٠٥٥ الميداني غير محفوظ")
parent=policy.get("professional_parent_candidate",{})
require(parent.get("version_code")==20055 and parent.get("field_status")=="FIELD_ACCEPTED",
        "٢٠٠٥٦ ليس مبنياً على خط ميدان مقبول")
require(policy.get("professional_candidate_hold")=="WAIT_20056_SOURCE_AND_FIELD_ACCEPTANCE",
        "حجز ٢٠٠٥٦ غير مثبت")

print("HAKIM_20056_PROFESSIONAL_WORKSPACE=PASS")
print("HAKIM_20056_VERIFIED_QURAN_UI=PASS")
print("HAKIM_20056_INHERITS_20055_STABILITY=PASS")
