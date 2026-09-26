from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


manifest = text("app/src/main/AndroidManifest.xml")
build = text("app/build.gradle")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
pair = text("app/src/main/java/ps/hakim/phoneagent/HakimPairingActivity.kt")
relay = text("app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt")
accessibility = text("app/src/main/java/ps/hakim/phoneagent/HakimAccessibilityService.kt")
notifications = text("app/src/main/java/ps/hakim/phoneagent/HakimNotificationListener.kt")
local_pairing = text("app/src/main/java/ps/hakim/phoneagent/HakimLocalPairing.kt")
local_adb = text("app/src/main/java/ps/hakim/phoneagent/HakimAdbConnectionManager.kt")
home = text("app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt")
boot = text("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt")
arabic_policy = text("app/src/main/java/ps/hakim/phoneagent/HakimArabicPolicy.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
main_activity = text("app/src/main/java/ps/hakim/phoneagent/MainActivity.kt")
command_center = text("app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: تغيرت هوية تطبيق حكيم")
require("versionCode 20018" in build, "P0: رقم إصدار التوحيد غير مثبت")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب أن يبقى لحكيم مُشغّل واحد فقط")
require('android:name=".UnifiedHomeActivity"' in manifest, "P0: الواجهة الموحدة ليست نقطة الدخول")
require('android:scheme="hakim" android:host="pair"' in manifest, "P0: رابط اقتران حكيم غير مسجل")
require('android:name=".HakimPairingActivity"' in manifest, "P0: بوابة الاقتران غير معلنة")
require('android:name=".HakimPairingReceiver"' in manifest, "P0: مستقبل الاقتران المحلي غير معلن")
require('android:name=".HakimAccessibilityService"' in manifest, "P0: خدمة الواجهة غير معلنة")
require('android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest, "P0: ربط خدمة الوصول مفقود")
require('android:name=".HakimNotificationListener"' in manifest, "P0: مستمع الإشعارات غير معلن")
require('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' in manifest, "P0: ربط مستمع الإشعارات مفقود")
require('HakimUnifiedRelay.start(this)' in app, "P0: القناة الموحدة لا تبدأ مع حكيم")
require('HakimUnifiedRelay.configure' in pair, "P0: الاقتران لا يهيئ القناة الموحدة")
require('AES/GCM/NoPadding' in relay and 'HmacSHA256' in relay, "P0: HC1 لا يحقق تشفير GCM وتوثيق HMAC")
require('request_expired' in relay and 'duplicate_request' in relay, "P0: حواجز الانتهاء/الإعادة مفقودة")
require('READ_ONLY_OPS' in relay and 'showApproval' in relay, "P0: بوابة الموافقة للأفعال المتغيرة مفقودة")
require('"[مخفي]"' in accessibility and 'isPassword' in accessibility, "P0: تنقيح الحقول الحساسة مفقود")
require('[رمز مخفي]' in notifications, "P0: تنقيح رموز التحقق في الإشعارات مفقود")
require('AndroidKeyStore' in local_adb and 'hakim_native_local_adb_v1' in local_adb, "P0: هوية ADB المحلية ليست محفوظة في AndroidKeyStore")
require('RemoteInput' in local_pairing and 'إدخال رمز الاقتران' in local_pairing, "P0: إدخال رمز الاقتران داخل حكيم مفقود")
require('reconnectAsync' in local_pairing and 'HakimLocalPairing.reconnectAsync(context)' in boot, "P0: التعافي التلقائي للقناة المحلية مفقود")
require('تأسيس ADB المحلي' in home and 'مركز القيادة' in home, "P0: الواجهة الموحدة لا تعرض مسار التأسيس والقيادة")

all_runtime = "\n".join([manifest, build, app, pair, relay, accessibility, notifications, local_pairing, local_adb, home, boot])
require("org.hakim.omega.companion" not in all_runtime, "P0: تسرب اعتماد التطبيق الموازي القديم")
require("ps.hakim.stable" in relay, "P0: إجراءات القناة ليست مربوطة بحكيم الوحيد")

# العقد العربي الافتراضي: يمنع الانحدار إلى واجهة/مخرجات مختلطة أو مقلوبة.
require('android:supportsRtl="true"' in manifest, "P0: دعم RTL على مستوى تطبيق حكيم غير مثبت")
require("ARABIC-FIRST-RTL-2026-09-26-v1" in arabic_policy, "P0: نسخة العقد العربي المركزي مفقودة")
require("arabic_default" in arabic_policy and "rtl_default" in arabic_policy, "P0: العربية/RTL ليستا افتراضيتين")
require("right_alignment_default" in arabic_policy, "P0: المحاذاة العربية الافتراضية غير مثبتة")
require("٠١٢٣٤٥٦٧٨٩" in arabic_policy, "P0: الأرقام الشرقية غير مثبتة في العقد العربي")
require("math_bidi_isolation_required" in arabic_policy and "\\u2066" in arabic_policy and "\\u2069" in arabic_policy, "P0: عزل الرياضيات عن BiDi غير مثبت")
require("technical_ltr_exception" in arabic_policy, "P0: استثناء URL/الكود من RTL غير مثبت")
require('<html lang=\\\"ar\\\" dir=\\\"rtl\\\">' in arabic_policy, "P0: عقد HTML العربي RTL مفقود")
require("STUDENT-EYE" in arabic_policy and "OVERLAP" in arabic_policy and "CLIP" in arabic_policy, "P0: فحص عين الطالب/الهندسة مفقود")
require("HakimArabicPolicy.install(context)" in constitution, "P0: العقد العربي لا يُثبت مع دستور حكيم")
require("HakimArabicPolicy.promptContract()" in constitution, "P0: العقد العربي لا يصل إلى محرك النية/النموذج")
require('"arabic_policy"' in constitution, "P0: حالة العقد العربي غير مكشوفة في حالة الدستور")
for ui_name, ui_text in [
    ("MainActivity", main_activity),
    ("UnifiedHomeActivity", home),
    ("CommandCenterActivity", command_center),
]:
    require("HakimArabicPolicy.applyUiDefaults(root)" in ui_text, f"P0: {ui_name} لا يطبق العربية/RTL مركزيًا")

print("UNIFIED_HAKIM_CONTRACT=PASS")
