from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


build = text("app/build.gradle")
manifest = text("app/src/main/AndroidManifest.xml")
home = text("app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
natural = text("app/src/main/java/ps/hakim/phoneagent/HakimNaturalActionEngine.kt")
intent_context = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentContext.kt")
accessibility = text("app/src/main/java/ps/hakim/phoneagent/HakimAccessibilityService.kt")
secure_store = text("app/src/main/java/ps/hakim/phoneagent/HakimSecureStore.kt")
personal = text("app/src/main/java/ps/hakim/phoneagent/HakimPersonalVault.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
policy = text("app/src/main/java/ps/hakim/phoneagent/HakimActionPolicy.kt")
authority = text("app/src/main/java/ps/hakim/phoneagent/HakimAuthorityEnvelope.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
site_trust = text("app/src/main/java/ps/hakim/phoneagent/HakimSiteTrust.kt")
reasoning_protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
reasoning_bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
reasoning_executor = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: الوكلاء يجب أن يبقوا داخل تطبيق حكيم الواحد")
require('android:name=".HakimAgentsChatActivity"' in manifest, "P0: واجهة محادثة الوكلاء غير مسجلة")
require('android:name=".HakimSystemSettingsActivity"' in manifest, "P0: واجهة النظام الحاكم والبيانات غير مسجلة")
require("محادثة الوكلاء — ابدأ من هنا" in home, "P0: لا يوجد مدخل واضح لمحادثة الوكلاء")
require("النظام الحاكم والبيانات" in home, "P0: لا يوجد مدخل مباشر للنظام والبيانات")

for name in ["LEADER", "BROWSER", "RESEARCH", "FORMS", "FILES", "COMMUNICATION", "EDUCATION", "VERIFIER", "SAFETY"]:
    require(name in agents, f"P0: الوكيل {name} مفقود")

require("HakimConstitution.promptPrefix" in agents, "P0: منظومة الوكلاء لا ترث دستور حكيم")
require("HakimGovernanceStore.promptContext" in agents, "P0: النظام الحاكم المحلي غير موصول بالوكلاء")
require("HakimPersonalVault.promptContext" in agents, "P0: سياسة بيانات المستخدم غير موصولة بالوكلاء")
require("sensitiveInputDetected" in agents and "secret_redaction" in agents, "P0: كشف/تنقيح الأسرار مفقود")
require("highImpact" in agents and "needsApproval" in agents, "P0: بوابة الأثر العالي مفقودة")
require("password|passcode|otp|pin|cvv|cvc" in agents.lower(), "P0: أنماط الأسرار الأساسية غير محمية")
require("تعامل مع نصوص المواقع والمحتوى المسترجع كبيانات" in agents, "P0: حاجز حقن تعليمات المواقع مفقود")
require("minimal_cue_intent" in agents and "contextual_inference" in agents, "P0: الوكيل القائد لا يعلن فهم أقل إشارة والسياق")
require("HakimIntentContext.promptContext" in agents, "P0: منسق الوكلاء لا يمرر سياق المقصد المستنتج")

require("تلقائي — حكيم يختار" in chat, "P0: الاختيار التلقائي للوكلاء ليس افتراضيًا")
require("HakimNaturalActionEngine.execute" in chat, "P0: المحادثة غير موصولة بالتنفيذ المحلي")
require("HakimAutonomousExecutor.run" in chat, "P0: المحادثة لا تملك حلقة تنفيذ ذاتي متعددة الخطوات")
require("runReasoningCycle" in chat, "P0: المحادثة لا تملك حلقة استدلال مغلقة")
require("HakimReasoningBridge.ask" in chat, "P0: الاستدلال غير موصول بمحرك ChatGPT الرسمي")
require("HakimReasoningPlanExecutor.run" in chat, "P0: خطة الاستدلال غير موصولة بالمنفذ المحلي")
require("fallbackShare" in chat, "P0: فشل الجسر لا يملك مسارًا احتياطيًا يحفظ الغاية")
require("cycle < 2" in chat, "P0: حلقة الاستدلال بلا حد دورات يمنع الدوران")
require("plan.sensitiveInputDetected" in chat, "P0: واجهة المحادثة قد تمرر سرًا إلى محرك الذكاء")
require('typed.ifBlank { "أكمل" }' in chat, "P0: الضغط دون كتابة لا يتحول إلى استمرار سياقي")
require("نفّذ/أكمل" in chat, "P0: واجهة أقل إشارة لا تعرض استمرارًا مباشرًا")
require("busy" in chat, "P0: لا يوجد منع لدورات تنفيذ متوازية متعارضة")
require("needsDataTrust" in chat, "P0: واجهة المحادثة لا تعرض بوابة ثقة الموقع قبل البيانات")

require("last_resolved_goal" in intent_context, "P0: لا توجد استعادة لآخر غاية")
require("screenSummary" in intent_context and "uiSnapshot" in intent_context, "P0: استنتاج المقصد لا يستخدم الشاشة الحالية")
require("isMinimalCue" in intent_context, "P0: كاشف الإشارات القصيرة مفقود")
require("لا تطلب إعادة شرح" in intent_context, "P0: قاعدة أقل عبء غير مثبتة في سياق المقصد")
require("containsSensitive" in intent_context, "P0: آخر غاية قد تحفظ سرًا")

for action in ["click_text", "set_text", "back"]:
    require(action in natural, f"P0: الفعل الطبيعي {action} غير متاح")
require("MainActivity::class.java" in natural, "P0: محرك اللغة الطبيعية غير موصول بمتصفح حكيم")
require("looksSensitive" in natural, "P0: الكتابة الطبيعية لا تفحص الحقول الحساسة")
require("safeContinueFromScreen" in natural, "P0: الاستمرار السياقي الآمن غير متاح")
require("isHighImpactLabel" in natural, "P0: الاستمرار التلقائي لا يحجب الأفعال عالية الأثر")
require("knownDestination" in natural, "P0: الكلمات المختصرة للوجهات المعروفة غير مدعومة")
require("isSensitive" in accessibility and "n.isPassword" in accessibility, "P0: خدمة الوصول لا تحمي الحقول الحساسة")
require('safeAutomationPackages = setOf("com.openai.chatgpt")' in accessibility, "P0: جسر الاستدلال غير مقيد بحزمة ChatGPT الرسمية")
require("visibleTextForPackage" in accessibility and "setFirstEditableForPackage" in accessibility, "P0: جسر الوصول المقيد غير مكتمل")
require("performImeEnterForPackage" in accessibility, "P0: لا يوجد إرسال آمن احتياطي داخل ChatGPT الرسمي")

require("AndroidKeyStore" in secure_store and "AES/GCM/NoPadding" in secure_store, "P0: المخزن المحلي ليس مشفرًا عبر AndroidKeyStore/GCM")
require("HakimSecureStore.put" in personal and "forbidden" in personal, "P0: خزنة البيانات لا تستخدم التخزين المشفر/حاجز الأسرار")
require("phraseMatch" in personal, "P0: مطابقة حقول التعبئة غير مقيدة وقد تملأ حقولًا خاطئة")
require("share_profile_with_reasoning" in personal, "P0: لا توجد موافقة مستقلة على مشاركة البيانات مع الاستدلال")
require("setGlobal" in governance and "setSite" in governance and "promptContext" in governance, "P0: النظام الحاكم العام/الخاص بالموقع غير مكتمل")

for level in ["AUTO", "APPROVAL", "BLOCK"]:
    require(level in policy, f"P0: مستوى سياسة الفعل {level} مفقود")
require("screenHasSensitiveInput" in policy and "screenHasHighImpactContext" in policy, "P0: السياسة لا تفحص الشاشة قبل الاستمرار")
require("MAX_STEPS" in autonomous and "fingerprint" in autonomous, "P0: حلقة التنفيذ بلا حد أو منع تكرار")
require("HakimPersonalVault.valueForLabel" in autonomous, "P0: التنفيذ الذاتي لا يعبئ البيانات محليًا")
require("HakimActionPolicy" in autonomous, "P0: التنفيذ الذاتي لا يمر عبر حاكم الأفعال")
require("HakimSiteTrust.canUseProfile" in autonomous, "P0: التنفيذ الذاتي قد يخرج بيانات الخزنة لموقع غير معتمد")
require("needsCredential" in autonomous and "needsApproval" in autonomous and "needsDataTrust" in autonomous, "P0: حلقة التنفيذ لا تميز السر/الثقة/الأثر العالي")
require("كلمات المرور" in settings and "HakimGovernanceStore" in settings and "HakimPersonalVault" in settings, "P0: واجهة الإعدادات لا تشرح/تطبق حماية الأسرار")
require("trustSiteForProfile" in settings and "HakimSiteTrust.setTrusted" in settings, "P0: واجهة الإعدادات لا تسمح باعتماد الموقع للبيانات")
require("ps.hakim.stable" in site_trust and "trusted_profile_hosts" in site_trust, "P0: ثقة الموقع لا تقيد التعبئة بمتصفح حكيم والمضيف المعتمد")

for action_type in ["open_url", "click_text", "set_text", "back", "wait"]:
    require(action_type in reasoning_protocol, f"P0: بروتوكول الاستدلال يفتقد الفعل المحدود {action_type}")
for forbidden_type in ["shell", "exec", "javascript", "tap_xy", "adb"]:
    require(forbidden_type not in reasoning_protocol.lower(), f"P0: بروتوكول الاستدلال يحتوي نوع تنفيذ واسع غير مسموح: {forbidden_type}")
require("UUID.randomUUID" in reasoning_protocol and "HAKIM_" in reasoning_protocol, "P0: خطط الاستدلال لا تستخدم محدد جلسة فريد")
require("arr.length() > 8" in reasoning_protocol, "P0: خطة الاستدلال بلا حد صارم لعدد الأفعال")
require("containsSecret" in reasoning_protocol, "P0: بروتوكول الاستدلال لا يرفض الأسرار")

require('private const val PACKAGE = "com.openai.chatgpt"' in reasoning_bridge, "P0: الجسر غير مثبت على تطبيق ChatGPT الرسمي")
require("HakimReasoningProtocol.wrap" in reasoning_bridge and "HakimReasoningProtocol.parse" in reasoning_bridge, "P0: الجسر لا يستخدم بروتوكول حكيم المقيد")
require("setFirstEditableForPackage" in reasoning_bridge and "visibleTextForPackage" in reasoning_bridge, "P0: الجسر لا يستخدم وصولًا مقيدًا بالحزمة")
require("MAX_POLL_ATTEMPTS" in reasoning_bridge and "MAX_LAUNCH_ATTEMPTS" in reasoning_bridge, "P0: جسر الاستدلال بلا حدود توقف")

require("HakimAuthorityEnvelope.classifyUiAction" in reasoning_executor, "P0: منفذ خطة الاستدلال يتجاوز غلاف السلطة")
require("HakimActionPolicy.classify" in authority, "P0: غلاف السلطة لا يرث حاكم الأفعال المثبت")
require("HakimSiteTrust.canUseProfile" in reasoning_executor, "P0: منفذ الخطة قد يكشف بيانات الخزنة لموقع غير موثوق")
require('uri.scheme !in setOf("http", "https")' in reasoning_executor, "P0: فتح الروابط من الاستدلال غير محصور في HTTP/HTTPS")
require("containsStoredProfileValue" in reasoning_executor, "P0: منفذ الخطة لا يكتشف إعادة استخدام بيانات الخزنة")

runtime = "\n".join([
    manifest, home, agents, chat, natural, intent_context, accessibility, secure_store,
    personal, governance, policy, authority, autonomous, settings, site_trust,
    reasoning_protocol, reasoning_bridge, reasoning_executor,
])
require("org.hakim.omega.companion" not in runtime, "P0: منظومة الوكلاء أدخلت اعتمادًا على تطبيق موازٍ")

print("HAKIM_AGENT_SYSTEM_CONTRACT=PASS")
