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
chatgpt = text("app/src/main/java/ps/hakim/phoneagent/HakimChatGptController.kt")
chatgpt_index = text("app/src/main/java/ps/hakim/phoneagent/HakimChatGptIndex.kt")
video_factory = text("app/src/main/java/ps/hakim/phoneagent/HakimVideoFactory.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: تغيرت هوية تطبيق حكيم")
require("versionCode 20091" in build, "P0: رقم إصدار القناة المباشرة غير مثبت")
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
require('fun sendHealthBeacon(context: Context, reason: String): Boolean' in relay, "P0: نبضة الصحة الآمنة مفقودة")
health = text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
require('HakimUnifiedRelay.isConfigured(context)' in health and 'HakimUnifiedRelay.sendHealthBeacon(context, reason)' in health, "P0: نبضة الصحة لا تفضل القناة الآمنة")
health_send = health.split('fun sendNow(context: Context, reason: String): Boolean {', 1)[1]
secure_call = health_send.find('HakimUnifiedRelay.sendHealthBeacon(context, reason)')
secure_success = health_send.find('if (secureOk) return true')
legacy_start = health_send.find('val topic = prefs.getString("result_topic"')
require(0 <= secure_call < secure_success < legacy_start, "P0: النقل القديم ليس fallback لنبضة الصحة")
result_send = relay.split('private fun sendResult(context: Context, resultTopic: String, requestId: String, status: String, result: JSONObject): Boolean {', 1)[1]
require(0 <= result_send.find('/device/v1/results') < result_send.find('https://ntfy.sh/'), "P0: نتيجة القناة الآمنة لا تفضل الجسر المباشر")
require('relay_result_topic' in pair and 'bridge_base' in pair, "P0: الاقتران المباشر لا يحمل موضوع النتيجة وأصل الجسر")
require('KEY_BRIDGE_BASE' in relay and 'DEFAULT_BRIDGE_BASE' in relay, "P0: خط أساس الجسر المباشر مفقود")
require('/device/v1/commands' in relay and '/device/v1/results' in relay, "P0: نقاط القناة المباشرة مفقودة")
require('Authorization' in relay and 'Bearer ' in relay, "P0: مصادقة القناة المباشرة مفقودة")
require('direct_connected' in relay and 'legacy_fallback' in relay, "P0: التعافي من القناة المباشرة إلى الاحتياط غير مثبت")
require('https://ntfy.sh/' in relay, "P0: مسار الرجوع المشفر القديم حُذف قبل إثبات القناة المباشرة")
require('AES/GCM/NoPadding' in relay and 'HmacSHA256' in relay, "P0: HC1 لا يحقق تشفير GCM وتوثيق HMAC")
require('request_expired' in relay and 'duplicate_request' in relay, "P0: حواجز الانتهاء/الإعادة مفقودة")
require('READ_ONLY_OPS' in relay and 'showApproval' in relay, "P0: بوابة الموافقة للأفعال المتغيرة مفقودة")
require('"[مخفي]"' in accessibility and 'isPassword' in accessibility, "P0: تنقيح الحقول الحساسة مفقود")
require('fun isActivePackage(expectedPackage: String)' in accessibility, "P0: حارس نطاق التطبيق النشط مفقود")
require('"chatgpt_read"' in relay and '"chatgpt_action"' in relay, "P0: مسار ChatGPT غير موصول بالقناة الموحدة")
require('READ_ONLY_OPS = setOf("status", "ui", "notifications", "screenshot", "chatgpt_read", "video_capabilities", "video_plan")' in relay, "P0: عمليات القراءة الآمنة لا تتضمن مصنع الفيديو كما يجب")
require('const val CHATGPT_PACKAGE = "com.openai.chatgpt"' in chatgpt, "P0: نطاق ChatGPT غير مثبت")
require('isActivePackage(CHATGPT_PACKAGE)' in chatgpt and 'chatgpt_not_active' in chatgpt, "P0: ChatGPT controller لا يفشل مغلقًا خارج التطبيق")
require('visible_only' in chatgpt and 'previously_visible_titles_only' in chatgpt, "P0: حدود تغطية القراءة غير معلنة")
require('AndroidKeyStore' in chatgpt_index and 'AES/GCM/NoPadding' in chatgpt_index, "P0: فهرس ChatGPT المحلي غير مشفر")
require('لا يخزن نصوص المحادثات' in chatgpt_index, "P0: سياسة تقليل بيانات فهرس ChatGPT مفقودة")
require('"video_capabilities" -> HakimVideoFactory.capabilities(context)' in relay, "P0: قدرات مصنع الفيديو غير موصولة بالقناة")
require('"video_plan" -> HakimVideoFactory.plan(payload)' in relay, "P0: تخطيط الفيديو غير موصول بالقناة")
require('"video_factory"' in relay and 'HakimVideoFactory.capabilities(context)' in relay, "P0: حالة مصنع الفيديو غير ظاهرة في status")
require('VIDEO-FACTORY-2026-09-30-v1' in video_factory, "P0: عقد مصنع الفيديو غير مثبت")
require('provider_router' in video_factory and 'quality_gates' in video_factory, "P0: موجّه المزود أو بوابات الجودة مفقودة")
require('paid_without_explicit_approval' in video_factory and 'claim_render_success_without_artifact' in video_factory, "P0: حارس الكلفة أو منع النجاح الوهمي مفقود")
require('no_provider_lock_in' in video_factory and 'retry_failed_segment_only' in video_factory, "P0: الاستقلال عن المزود أو إصلاح المقطع فقط مفقود")
require('RIGHTS_AND_LICENSE' in video_factory and 'FINAL_ARTIFACT_PLAYBACK' in video_factory, "P0: بوابة الحقوق أو تشغيل الملف النهائي مفقودة")
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
