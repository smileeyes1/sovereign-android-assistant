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
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require('android:name=".HakimProfessionalHubActivity"' in manifest, "مساحة العمل غير مسجلة")
require('android:name=".HakimQuranActivity"' in manifest, "واجهة القرآن غير مسجلة")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "يجب بقاء مشغل واحد")
require("HakimProfessionalHubActivity::class.java" in chat, "المحادثة لا تصل لمساحة العمل")
require("HakimQuranActivity::class.java" in chat, "المحادثة لا تصل للقرآن المحلي")
require("HakimVerifiedQuranCorpus.fullCorpusScan" in quran, "بحث القرآن لا يستخدم corpus المتحقق")
require("HakimVerifiedQuranCorpus.ayah" in quran, "مرجع الآية لا يستخدم corpus المتحقق")
require("THREAD_PRIORITY_BACKGROUND" in quran and "Thread {" in quran, "بحث/تهيئة القرآن قد يحجب main thread")
require("البحث اللفظي ليس تفسيراً" in quran, "حد البحث اللفظي غير ظاهر للمستخدم")
require("HakimSelfCheck.run" in hub and "Thread {" in hub, "الفحص الاحترافي ليس محلياً/خلفياً")
require("HakimUnifiedRelay.isConfigured" in hub, "المركز لا يعرض حالة القناة الآمنة")
require("AutoUpdater.checkAsync" in hub, "المركز لا يصل للتحديث الموثوق")
require("compose" not in build.lower() and "RecyclerView" not in build, "أضيفت طبقة UI ثقيلة دون حاجة")
m=re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) == 20052, "الإصدار يجب أن يكون ٢٠٠٥٢")
require(policy["current_candidate_version"] == 20052, "السياسة لا تسجل ٢٠٠٥٢ كمرشح")
require(policy["professional_candidate_hold"] == "WAIT_20051_JOBS_AND_EXIT_INFO_STABILITY_GATE",
        "٢٠٠٥٢ غير محجوز خلف بوابة استقرار ٢٠٠٥١")
require(policy["current_field_version"] == 20051, "خط الميدان يجب أن يعكس ٢٠٠٥١ المثبت")
print("HAKIM_20052_PROFESSIONAL_WORKSPACE=PASS")
print("HAKIM_20052_VERIFIED_QURAN_UI=PASS")
print("HAKIM_20052_FIELD_HOLD_GUARD=PASS")
