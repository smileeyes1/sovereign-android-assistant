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

require("applicationId 'ps.hakim.stable'" in build, "P0: تغيرت هوية تطبيق حكيم")
require("versionCode 20018" in build, "P0: رقم إصدار التوحيد غير مثبت")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب أن يبقى لحكيم مُشغّل واحد فقط")
require('android:name=".UnifiedHomeActivity"' in manifest, "P0: الواجهة الموحدة ليست نقطة الدخول")
require('android:scheme="hakim" android:host="pair"' in manifest, "P0: رابط اقتران حكيم غير مسجل")
require('android:name=".HakimPairingActivity"' in manifest, "P0: بوابة الاقتران غير معلنة")
require('android:name=".HakimPairingReceiver"' in manifest, "P0: مستقبل الاقتران المحلي غير معلن")

# يبقى كود الوصول/الإشعارات موجودًا كمرجع محمي، لكنه لا يُعلن في ملف التثبيت
# الافتراضي حتى لا يحفز Play Protect Enhanced Fraud Protection عند sideload.
require('"[مخفي]"' in accessibility and 'isPassword' in accessibility, "P0: تنقيح الحقول الحساسة مفقود من محرك الوصول المرجعي")
require('[رمز مخفي]' in notifications, "P0: تنقيح رموز التحقق مفقود من مستمع الإشعارات المرجعي")
require('android:name=".HakimAccessibilityService"' not in manifest, "P0: ملف التثبيت الآمن يعيد إعلان AccessibilityService")
require('android.permission.BIND_ACCESSIBILITY_SERVICE' not in manifest, "P0: ملف التثبيت الآمن يعيد طلب ربط Accessibility")
require('android:name=".HakimNotificationListener"' not in manifest, "P0: ملف التثبيت الآمن يعيد إعلان NotificationListener")
require('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' not in manifest, "P0: ملف التثبيت الآمن يعيد طلب الوصول للإشعارات")

require('HakimUnifiedRelay.start(this)' in app, "P0: القناة الموحدة لا تبدأ مع حكيم")
require('HakimUnifiedRelay.configure' in pair, "P0: الاقتران لا يهيئ القناة الموحدة")
require('AES/GCM/NoPadding' in relay and 'HmacSHA256' in relay, "P0: HC1 لا يحقق تشفير GCM وتوثيق HMAC")
require('request_expired' in relay and 'duplicate_request' in relay, "P0: حواجز الانتهاء/الإعادة مفقودة")
require('READ_ONLY_OPS' in relay and 'showApproval' in relay, "P0: بوابة الموافقة للأفعال المتغيرة مفقودة")
require('AndroidKeyStore' in local_adb and 'hakim_native_local_adb_v1' in local_adb, "P0: هوية ADB المحلية ليست محفوظة في AndroidKeyStore")
require('RemoteInput' in local_pairing and 'إدخال رمز الاقتران' in local_pairing, "P0: إدخال رمز الاقتران داخل حكيم مفقود")
require('reconnectAsync' in local_pairing and 'HakimLocalPairing.reconnectAsync(context)' in boot, "P0: التعافي التلقائي للقناة المحلية مفقود")
require('تأسيس ADB المحلي' in home and 'مركز القيادة' in home, "P0: الواجهة الموحدة لا تعرض مسار التأسيس والقيادة")

all_runtime = "\n".join([manifest, build, app, pair, relay, accessibility, notifications, local_pairing, local_adb, home, boot])
require("org.hakim.omega.companion" not in all_runtime, "P0: تسرب اعتماد التطبيق الموازي القديم")
require("ps.hakim.stable" in relay, "P0: إجراءات القناة ليست مربوطة بحكيم الوحيد")

print("UNIFIED_HAKIM_CONTRACT=PASS")
