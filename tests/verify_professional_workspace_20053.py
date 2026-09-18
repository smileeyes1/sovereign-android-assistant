from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
def text(p): return (ROOT / p).read_text(encoding="utf-8")
def require(ok, msg):
    if not ok: raise SystemExit("P0: " + msg)

manifest=text("app/src/main/AndroidManifest.xml")
chat=text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
quran=text("app/src/main/java/ps/hakim/phoneagent/HakimQuranActivity.kt")
hub=text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalHubActivity.kt")
job=text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
health=text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require('android:name=".HakimProfessionalHubActivity"' in manifest, "مساحة العمل غير مسجلة")
require('android:name=".HakimQuranActivity"' in manifest, "واجهة القرآن غير مسجلة")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "يجب بقاء مشغل واحد")
require("HakimProfessionalHubActivity::class.java" in chat, "المحادثة لا تصل لمساحة العمل")
require("HakimQuranActivity::class.java" in chat, "المحادثة لا تصل للقرآن المحلي")
require("HakimVerifiedQuranCorpus.fullCorpusScan" in quran, "بحث القرآن لا يستخدم corpus المتحقق")
require("HakimVerifiedQuranCorpus.ayah" in quran, "مرجع الآية لا يستخدم corpus المتحقق")
require("THREAD_PRIORITY_BACKGROUND" in quran and "Thread {" in quran, "بحث القرآن قد يحجب main thread")
require("البحث اللفظي ليس تفسيراً" in quran, "حد البحث اللفظي غير ظاهر")
require("HakimSelfCheck.run" in hub and "Thread {" in hub, "الفحص الذاتي ليس خلفياً")
require("HakimUnifiedRelay.isConfigured" in hub, "المركز لا يعرض القناة الآمنة")
require("AutoUpdater.checkAsync" in hub, "المركز لا يصل للتحديث الموثوق")

# وراثة ٢٠٠٥٢ إلزامية.
require("HakimHealthBeacon.sendAsync" in job and "HakimHealthBeacon.sendNow" not in job,
        "٢٠٠٥٣ فقد إصلاح watchdog غير الحاجب")
require("asyncInFlight = AtomicBoolean(false)" in health,
        "٢٠٠٥٣ فقد منع تكدس نبضات الصحة")

m=re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) == 20053, "الإصدار يجب أن يكون ٢٠٠٥٣")
require(policy["current_candidate_version"] == 20053, "السياسة لا تسجل ٢٠٠٥٣")
require(policy["current_field_version"] == 20051, "خط الميدان يجب أن يبقى ٢٠٠٥١ حتى قبول ٢٠٠٥٢")
parent=policy.get("professional_parent_candidate", {})
require(parent.get("version_code") == 20052 and parent.get("source_status") == "QUALIFIED_5_OF_5_CI",
        "مرشح الاستقرار ٢٠٠٥٢ غير مثبت كأساس ٢٠٠٥٣")
require(policy.get("professional_candidate_hold") == "WAIT_20052_FIELD_ACCEPTANCE",
        "٢٠٠٥٣ غير محجوز خلف قبول ٢٠٠٥٢")

print("HAKIM_20053_PROFESSIONAL_WORKSPACE=PASS")
print("HAKIM_20053_VERIFIED_QURAN_UI=PASS")
print("HAKIM_20053_INHERITS_20052_WATCHDOG=PASS")
print("HAKIM_20053_FIELD_HOLD_GUARD=PASS")
