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
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")

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
require("sendToReasoningEngine" in chat, "P0: المهام المركبة لا تملك مسار استدلال")
require("plan.sensitiveInputDetected" in chat, "P0: واجهة المحادثة قد تمرر سرًا إلى محرك الذكاء")
require('typed.ifBlank { "أكمل" }' in chat, "P0: الضغط دون كتابة لا يتحول إلى استمرار سياقي")
require("نفّذ/أكمل" in chat, "P0: واجهة أقل إشارة لا تعرض استمرارًا مباشرًا")
require("busy" in chat, "P0: لا يوجد منع لدورات تنفيذ متوازية متعارضة")

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
require("needsCredential" in autonomous and "needsApproval" in autonomous, "P0: حلقة التنفيذ لا تميز السر عن الأثر العالي")
require("كلمات المرور" in settings and "HakimGovernanceStore" in settings and "HakimPersonalVault" in settings, "P0: واجهة الإعدادات لا تشرح/تطبق حماية الأسرار")

runtime = "\n".join([manifest, home, agents, chat, natural, intent_context, secure_store, personal, governance, policy, autonomous, settings])
require("org.hakim.omega.companion" not in runtime, "P0: منظومة الوكلاء أدخلت اعتمادًا على تطبيق موازٍ")

print("HAKIM_AGENT_SYSTEM_CONTRACT=PASS")
