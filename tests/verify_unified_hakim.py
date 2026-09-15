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
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
mesh = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityMesh.kt")
boot = text("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt")
resilience = text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: تغيرت هوية تطبيق حكيم")
version = re.search(r"versionCode\s+(\d+)", build)
require(version is not None and int(version.group(1)) >= 20022,
        "P0: إصدار حكيم أقدم من خط شبكة التفوق")
require("versionName '" in build, "P0: اسم إصدار حكيم مفقود")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب أن يبقى لحكيم مُشغّل واحد فقط")
launcher_block = manifest.split('android.intent.category.LAUNCHER')[0][-900:]
require('android:name=".HakimAgentsChatActivity"' in launcher_block, "P0: محادثة حكيم ليست واجهة التشغيل الرئيسية")
require('android:name=".UnifiedHomeActivity"' in manifest, "P0: مركز حكيم/الاتصال المحلي مفقود")
require('android:scheme="hakim" android:host="pair"' in manifest, "P0: رابط اقتران حكيم غير مسجل")
require('android:name=".HakimPairingActivity"' in manifest, "P0: بوابة الاقتران غير معلنة")
require('android:name=".HakimPairingReceiver"' in manifest, "P0: مستقبل الاقتران المحلي غير معلن")

require('"[مخفي]"' in accessibility and 'isPassword' in accessibility, "P0: تنقيح الحقول الحساسة مفقود من محرك الوصول المرجعي")
require('[رمز مخفي]' in notifications, "P0: تنقيح رموز التحقق مفقود من مستمع الإشعارات المرجعي")
require('android:name=".HakimAccessibilityService"' not in manifest, "P0: ملف التثبيت الآمن يعيد إعلان AccessibilityService")
require('android.permission.BIND_ACCESSIBILITY_SERVICE' not in manifest, "P0: ملف التثبيت الآمن يعيد طلب ربط Accessibility")
require('android:name=".HakimNotificationListener"' not in manifest, "P0: ملف التثبيت الآمن يعيد إعلان NotificationListener")
require('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' not in manifest, "P0: ملف التثبيت الآمن يعيد طلب الوصول للإشعارات")

require('HakimUnifiedRelay.start(this)' in app, "P0: القناة الموحدة لا تبدأ مع حكيم عند تهيئتها")
require('HakimUnifiedRelay.configure' in pair, "P0: الاقتران لا يهيئ القناة الموحدة")
require('AES/GCM/NoPadding' in relay and 'HmacSHA256' in relay, "P0: HC1 لا يحقق تشفير GCM وتوثيق HMAC")
require('request_expired' in relay and 'duplicate_request' in relay, "P0: حواجز الانتهاء/الإعادة مفقودة")
require('READ_ONLY_OPS' in relay and 'showApproval' in relay, "P0: بوابة الموافقة للأفعال المتغيرة مفقودة")

# HC1 يجب أن يستعيد نفسه بعد الإقلاع حتى لو لم تعد القناة القديمة مهيأة.
require('HakimUnifiedRelay.isConfigured(context)' in boot and 'HakimUnifiedRelay.start(context)' in boot,
        "P0: HC1 لا يستعيد الاستماع تلقائيًا بعد الإقلاع")
require(boot.index('HakimUnifiedRelay.start(context)') < boot.index('if (disabled || !legacyPaired) return'),
        "P0: استعادة HC1 ما زالت مشروطة خطأ بوجود القناة القديمة")

# حارس الاتصال نفسه يجب أن يكون مستقلًا عن القناة القديمة: HC1 والمحلي مساران أصيلان.
require('val securePaired = HakimUnifiedRelay.isConfigured(app)' in resilience,
        "P0: حارس الاتصال لا يعترف باقتران HC1")
require('val localPaired = p.getBoolean("local_adb_paired", false)' in resilience,
        "P0: حارس الاتصال لا يعترف بالاقتران المحلي")
require('val anyPaired = legacyPaired || securePaired || localPaired' in resilience,
        "P0: الاستعادة ما زالت أسيرة القناة القديمة")
require('if (securePaired) HakimUnifiedRelay.start(app)' in resilience,
        "P0: حارس الاتصال لا يعيد تشغيل HC1 ذاتيًا")
require('if (localPaired) HakimLocalPairing.reconnectAsync(app)' in resilience,
        "P0: حارس الاتصال لا يعيد وصل المسار المحلي ذاتيًا")
require('secure_reconnect_requested' in resilience and 'secure_relay_state' in resilience,
        "P0: تشخيص استعادة HC1 غير قابل للرصد")

require('AndroidKeyStore' in local_adb and 'hakim_native_local_adb_v1' in local_adb, "P0: هوية ADB المحلية ليست محفوظة في AndroidKeyStore")
require('RemoteInput' in local_pairing and 'إدخال رمز الاقتران' in local_pairing, "P0: إدخال رمز الاقتران داخل حكيم مفقود")
require('reconnectAsync' in local_pairing and 'HakimLocalPairing.reconnectAsync(context)' in boot, "P0: التعافي التلقائي للقناة المحلية مفقود")
require('تأسيس ADB المحلي' in home and 'مركز القيادة' in home, "P0: مركز حكيم لا يعرض مسار التأسيس والقيادة")
require('مركز حكيم والاتصال المحلي' in chat, "P0: واجهة المحادثة لا تصل إلى مركز الاتصال المحلي")
require('object HakimCapabilityMesh' in mesh and 'rank(context' in mesh, "P0: شبكة التفوق/الأدوات غير مدمجة")

all_runtime = "\n".join([manifest, build, app, pair, relay, accessibility, notifications, local_pairing, local_adb, home, chat, mesh, boot, resilience])
require("org.hakim.omega.companion" not in all_runtime, "P0: تسرب اعتماد التطبيق الموازي القديم")
require("ps.hakim.stable" in relay, "P0: إجراءات القناة ليست مربوطة بحكيم الوحيد")

print("UNIFIED_HAKIM_CONTRACT=PASS")
