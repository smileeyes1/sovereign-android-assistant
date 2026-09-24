from pathlib import Path
import re

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
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimExecutionFabric.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: تغيرت هوية تطبيق حكيم")
version_match = re.search(r"versionCode\s+(\d+)", build)
require(version_match is not None, "P0: رقم إصدار حكيم مفقود")
require(int(version_match.group(1)) >= 20087, "P0: خفض إصدار حكيم دون خط الأساس الميداني")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب أن يبقى لحكيم مُشغّل واحد فقط")
require('android:name=".CommandCenterActivity"' in manifest, "P0: مركز قيادة حكيم غير معلن")
require('android:name=".UnifiedHomeActivity"' in manifest, "P0: إدارة الجهاز الموحدة غير معلنة")
launcher_pattern = re.compile(
    r'<activity\s+android:name="\.CommandCenterActivity"[\s\S]*?'
    r'<action android:name="android.intent.action.MAIN" />[\s\S]*?'
    r'<category android:name="android.intent.category.LAUNCHER" />'
)
require(launcher_pattern.search(manifest) is not None, "P0: مركز قيادة حكيم ليس نقطة الدخول الوحيدة")
require('android:scheme="hakim" android:host="pair"' in manifest, "P0: رابط اقتران حكيم غير مسجل")
require('android:name=".HakimPairingActivity"' in manifest, "P0: بوابة الاقتران غير معلنة")
require('android:name=".HakimPairingReceiver"' in manifest, "P0: مستقبل الاقتران المحلي غير معلن")
require('android:name=".HakimAccessibilityService"' not in manifest, "P0: خدمة الوصول الحساسة لا يجوز إعلانها في ملف التثبيت الآمن")
require('android.permission.BIND_ACCESSIBILITY_SERVICE' not in manifest, "P0: ربط الوصول الحساس ما زال مكشوفًا")
require('android:name=".HakimNotificationListener"' not in manifest, "P0: مستمع الإشعارات الحساس لا يجوز إعلانُه في ملف التثبيت الآمن")
require('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' not in manifest, "P0: ربط الإشعارات الحساس ما زال مكشوفًا")
require(
    ('HakimUnifiedRelay.start(this)' in app) or
    ('HakimExecutionFabric.recover(this, "app_start")' in app and 'HakimUnifiedRelay.start(app)' in fabric),
    "P0: القناة الموحدة لا تبدأ مع حكيم مباشرة أو عبر نسيج التنفيذ"
)
require('HakimUnifiedRelay.configure' in pair, "P0: الاقتران لا يهيئ القناة الموحدة")
require('AES/GCM/NoPadding' in relay and 'HmacSHA256' in relay, "P0: HC1 لا يحقق تشفير GCM وتوثيق HMAC")
require('request_expired' in relay and 'duplicate_request' in relay, "P0: حواجز الانتهاء/الإعادة مفقودة")
require('READ_ONLY_OPS' in relay and 'showApproval' in relay, "P0: بوابة الموافقة للأفعال المتغيرة مفقودة")
require('"[مخفي]"' in accessibility and 'isPassword' in accessibility, "P0: تنقيح الحقول الحساسة مفقود")
require('[رمز مخفي]' in notifications, "P0: تنقيح رموز التحقق في الإشعارات مفقود")
require('AndroidKeyStore' in local_adb and 'hakim_native_local_adb_v1' in local_adb, "P0: هوية ADB المحلية ليست محفوظة في AndroidKeyStore")
require('RemoteInput' in local_pairing and 'إدخال رمز الاقتران' in local_pairing, "P0: إدخال رمز الاقتران داخل حكيم مفقود")
require('reconnectAsync' in local_pairing and 'HakimLocalPairing.reconnectAsync(context)' in boot, "P0: التعافي التلقائي للقناة المحلية مفقود")
require('HakimLocalPairing.reconnectAsync' in local_pairing, "P0: قدرة ADB المحلية مفقودة")
require('الإعدادات' in home and 'العودة إلى حكيم' in home, "P0: إعدادات المنتج غير قابلة للاستخدام")
require('تأسيس ADB المحلي' not in home, "P0: تسرب عنصر تطويري ADB إلى إعدادات العميل")

all_runtime = "\n".join([manifest, build, app, pair, relay, accessibility, notifications, local_pairing, local_adb, home, boot, fabric])
require("org.hakim.omega.companion" not in all_runtime, "P0: تسرب اعتماد التطبيق الموازي القديم")
require("ps.hakim.stable" in relay, "P0: إجراءات القناة ليست مربوطة بحكيم الوحيد")

print("UNIFIED_HAKIM_CONTRACT=PASS")
