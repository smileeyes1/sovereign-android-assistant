from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
intent = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentContext.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
policy = text("app/src/main/java/ps/hakim/phoneagent/HakimActionPolicy.kt")
authority = text("app/src/main/java/ps/hakim/phoneagent/HakimAuthorityEnvelope.kt")
protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
plan_exec = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
access = text("app/src/main/java/ps/hakim/phoneagent/HakimAccessibilityService.kt")
personal = text("app/src/main/java/ps/hakim/phoneagent/HakimPersonalVault.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
trust = text("app/src/main/java/ps/hakim/phoneagent/HakimSiteTrust.kt")

# أقل إشارة يجب أن تعيد سياق الويب الفعلي لا شاشة محادثة حكيم.
require("last_goal+last_url" in intent, "P0: أقل إشارة لا تستعيد الغاية مع مسار الويب")
require("حكيم — محادثة الوكلاء" in intent and 'return ""' in intent, "P0: شاشة حكيم قد تُفسَّر خطأ كحالة للموقع")
require("minimalContinuation && webContext" in agents, "P0: الإشارة القصيرة مع مسار ويب لا تُوجه لوكيل المتصفح")
require("waitForHakimBrowser" in chat, "P0: المحادثة قد تنفذ قبل استعادة المتصفح")
require("HakimRuntime.visibleWebView" in chat and "web.progress >= 70" in chat, "P0: لا يوجد تحقق من WebView الفعلي قبل التنفيذ")
require("runAutonomousCycle" in chat and "HakimIntentContext.isMinimalCue(cue)" in chat, "P0: الإشارة القصيرة تتوقف بعد فعل واحد")

# حلقة التنفيذ يجب أن تكمل التحضير وتتوقف عند الفعل النهائي لا مجرد وجود سياق عالي الأثر.
require("nextApprovalAction" in autonomous, "P0: لا يوجد كشف للفعل النهائي عالي الأثر")
require("screenHasHighImpactContext(snapshot)" not in autonomous, "P0: التنفيذ يتوقف مبكرًا لمجرد سياق عالي الأثر")
require("بعد إكمال التحضير الآمن" in autonomous, "P0: بوابة الأثر العالي ليست بعد التحضير")
require("successfully completed" in policy and "|نجاح|" not in policy, "P0: إثبات النجاح واسع وقد يعطي COMPLETE كاذبًا")
require("ambiguousHighImpactContinuationRegex" in policy, "P0: المتابعة المبهمة داخل سياق عالي الأثر قد تُنفذ تلقائيًا")
require("highImpactContext &&" in policy and "ambiguousHighImpactContinuationRegex.containsMatchIn(text)" in policy,
        "P0: بوابة المتابعة المبهمة غير مرتبطة فعليًا بالسياق عالي الأثر")

# البيانات الشخصية: النموذج يطلب الحقل، الجهاز يملك القيمة، والثقة مرتبطة بالمضيف الفعلي الدقيق.
require('"fill_profile"' in protocol, "P0: بروتوكول الاستدلال لا يدعم تعبئة الخزنة محليًا")
require("allowedProfileFields" in protocol, "P0: حقول الخزنة في الخطة غير مقيدة")
require('type == "fill_profile"' in protocol, "P0: محلل الخطة لا يتحقق من field_id")
require('"fill_profile"' in plan_exec and "HakimPersonalVault.get" in plan_exec, "P0: منفذ الخطة لا يجلب قيمة الخزنة محليًا")
require("HakimSiteTrust.canUseProfile" in plan_exec, "P0: fill_profile قد يخرج البيانات لموقع غير موثوق")
require("fill_profile{target,field_id}" in personal, "P0: سياق الخزنة لا يوجه النموذج للتعبئة المحلية")
require("explicitlyRelevant" in personal, "P0: مشاركة قيم البيانات مع الاستدلال ليست انتقائية")
require("allowedFieldIds" in personal and "key !in allowedFieldIds" in personal,
        "P0: خزنة البيانات تقبل مفاتيح خارج قائمة الحقول المعلنة")
require("index.filter { it in allowedFieldIds }" in personal,
        "P0: الخزنة قد تعيد حقولًا قديمة/غير مسموحة من الفهرس")
require("trusted_profile_hosts" in trust, "P0: قائمة ثقة المواقع مفقودة")
require("HakimRuntime.visibleWebView()?.url" in trust, "P0: ثقة الخزنة تعتمد عنوانًا مخزنًا بدل عنوان WebView الفعلي")
require("return host in set" in trust, "P0: ثقة الموقع ليست مطابقة دقيقة للمضيف")
require("candidates(host)" not in trust and "takeLast(2)" not in trust,
        "P0: ثقة نطاق أب قد تمتد ضمنيًا إلى نطاقات فرعية غير مقصودة")

# تعليمات المواقع يجب أن تتبع المضيف الظاهر الفعلي وألا تُورّث إلى نطاقات أخرى.
require("HakimRuntime.visibleWebView()?.url" in governance,
        "P0: تعليمات الموقع تعتمد last_url المخزن بدل الصفحة الفعلية")
require("siteCandidates" not in governance and "takeLast(2)" not in governance,
        "P0: تعليمات النطاق الأب قد تُطبق ضمنيًا على نطاقات فرعية")
require("val key = SITE_PREFIX + normalized.replace('.', '_')" in governance,
        "P0: قراءة تعليمات الموقع ليست مرتبطة بالمضيف الدقيق")

# خطة الاستدلال يجب أن تُفحص بعد العودة لشاشة الموقع، وغلاف السلطة يرث حاكم الأفعال المثبت.
require("HakimActionPolicy.classify" in authority, "P0: غلاف السلطة لا يرث حاكم الأفعال")
require("HakimAuthorityEnvelope.classifyUiAction" in plan_exec, "P0: منفذ الخطة لا يستخدم غلاف السلطة")
click_pos = plan_exec.find('"click_text"')
ensure_pos = plan_exec.find("ensureHakimBrowser", click_pos)
classify_pos = plan_exec.find("gateAction(target, targetSnapshot)", ensure_pos)
require(click_pos >= 0 and ensure_pos >= 0 and classify_pos > ensure_pos, "P0: النقرة تُصنَّف قبل استعادة شاشة الموقع")
set_pos = plan_exec.find('"set_text"')
ensure_set = plan_exec.find("ensureHakimBrowser", set_pos)
classify_set = plan_exec.find('gateAction("$target $value", targetSnapshot)', ensure_set)
require(set_pos >= 0 and ensure_set >= 0 and classify_set > ensure_set, "P0: الكتابة تُصنَّف قبل استعادة شاشة الموقع")

# جسر ChatGPT اختياري ومقيد بالحزمة الرسمية وبروتوكول مغلق.
require('private const val PACKAGE = "com.openai.chatgpt"' in bridge, "P0: جسر الاستدلال ليس مقيدًا بتطبيق ChatGPT الرسمي")
require('safeAutomationPackages = setOf("com.openai.chatgpt")' in access, "P0: وصول ChatGPT ليس بقائمة سماح صريحة")
require("candidates.any { it == target }" in access, "P0: زر الإرسال يستخدم مطابقة واسعة قد تنقر رسالة قديمة")
require("candidates.any { it == target ||" not in access, "P0: مطابقة زر ChatGPT ليست تامة")
require("HakimReasoningProtocol.parse" in bridge and "MAX_POLL_ATTEMPTS" in bridge, "P0: الجسر قد ينفذ دون خطة مقيدة/حد توقف")

# النظام الحاكم قد يحتوي نصوص أسرار؛ يجب تنقيحها قبل إرسال السياق.
require("redactEmbeddedSecrets" in governance, "P0: أسرار محتملة داخل النظام الحاكم لا تُنقح")
require("[سري — محجوب]" in governance and "[رقم حساس محجوب]" in governance, "P0: تنقيح النظام الحاكم غير مكتمل")

print("HAKIM_DEEP_AUTONOMY_CONTRACT=PASS")
