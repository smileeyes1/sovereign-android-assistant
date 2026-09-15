from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


web = text("app/src/main/java/ps/hakim/phoneagent/HakimWebReasoningBridge.kt")
providers = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProviderRegistry.kt")
web_actions = text("app/src/main/java/ps/hakim/phoneagent/HakimWebAutomation.kt")
bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
executor = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
natural = text("app/src/main/java/ps/hakim/phoneagent/HakimNaturalActionEngine.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
manifest = text("app/src/main/AndroidManifest.xml")
build = text("app/build.gradle")
workflow = text(".github/workflows/android.yml")

version = re.search(r"versionCode\s+(\d+)", build)
require(version is not None and int(version.group(1)) >= 20025,
        "P0: موجّه الاستدلال المتعدد ليس ضمن الإصدار ٢٠٠٢٥ أو أحدث")
# لا نربط بقاء القدرة باسم versionName؛ الاسم وصفي للإضافة الأحدث، أما القدرة
# نفسها فيثبتها عقد المزودات والجسر والتنفيذ واختبارات الانحدار أدناه.

# الاستقلال عن مزود واحد.
require("object HakimReasoningProviderRegistry" in providers, "P0: سجل مزودات الاستدلال مفقود")
for provider in ["CHATGPT_WEB", "GEMINI_WEB", "COPILOT_WEB", "LOCAL_ONLY", "AUTO"]:
    require(provider in providers, f"P0: خيار المزود {provider} مفقود")
for host in ["chatgpt.com", "gemini.google.com", "copilot.microsoft.com"]:
    require(host in providers, f"P0: مزود الويب {host} غير مسجل")
require("orderedWebProviders" in providers and "recordWebResult" in providers,
        "P0: لا يوجد ترتيب تكيفي/تعلم من نجاح وفشل المزودات")
require("cooldown" in providers and "last_success" in providers,
        "P0: المزود المتكرر فشله لا يدخل تهدئة ولا يُقدّم المزود المثبت")
require("single_external_provider_is_not_governor" in providers,
        "P0: لا توجد قاعدة صريحة تمنع مزودًا واحدًا من حكم حكيم")

# الجسر العام يجرب المزودات ثم يفتح تسجيل دخول واحدًا فقط عند الحاجة.
require("HakimReasoningProviderRegistry.orderedWebProviders" in bridge,
        "P0: جسر الاستدلال لا يستخدم موجّه المزودات")
require("tryProvider" in bridge and "interactiveFallback" in bridge,
        "P0: لا يوجد failover تلقائي بين المزودات")
require("interactiveLogin = false" in bridge and "interactiveLogin = true" in bridge,
        "P0: حكيم قد يفتح نوافذ تسجيل دخول متعددة بدل تجربة الجلسات الصامتة أولًا")
require("LOCAL_ONLY" in bridge, "P0: المستخدم لا يستطيع فرض الاستدلال المحلي فقط")

# محرك الويب عام لكنه يقفل كل جولة على أصل المزود المختار.
require("object HakimWebReasoningBridge" in web, "P0: جسر الاستدلال داخل حكيم مفقود")
require("providerId: String" in web and "webProvider(providerId)" in web,
        "P0: جسر الويب ما زال مثبتًا على مزود واحد")
require("spec.trustedHosts.contains" in web and 'uri.scheme == "https"' in web,
        "P0: لا يوجد تثبيت صارم لأصل HTTPS للمزود المختار")
require("WebView" in web and "evaluateJavascript" in web,
        "P0: الاستدلال لا يعمل داخل WebView حكيم")
require("HakimReasoningProtocol.wrap" in web and "HakimReasoningProtocol.parse" in web,
        "P0: محرك الويب يتجاوز بروتوكول حكيم المقيد")
require("CookieManager.getInstance" in web and "setAcceptCookie(true)" in web,
        "P0: جلسات تسجيل الدخول داخل حكيم لا تستمر")
require("MainActivity::class.java" in web and 'putString("last_url", spec.startUrl)' in web,
        "P0: مسار تسجيل الدخول لمرة واحدة للمزود المختار غير موجود")
require("interactiveLogin" in web and "NEEDS_LOGIN" in web,
        "P0: لا يمكن تجربة مزود بصمت قبل فتح واجهة تسجيل الدخول")
require("HakimRuntime.visibleWebView" in web,
        "P0: الجسر لا يستعيد جلسة تسجيل الدخول المرئية")
require("loadsImagesAutomatically = false" in web and "blockNetworkImage = true" in web,
        "P0: WebView الاستدلال المؤقت غير مخفف للهاتف")
require("hidden.destroy()" in web and "removeCallbacksAndMessages" in web,
        "P0: محرك الاستدلال المؤقت قد يسرب ذاكرة/حلقات")
require("MAX_RESPONSE_ATTEMPTS" in web and "MAX_LOGIN_WAIT_ATTEMPTS" in web,
        "P0: الاستدلال/تسجيل الدخول بلا حدود توقف")

# التنفيذ يبقى محليًا ومقيدًا بعد أي نموذج خارجي.
require("object HakimWebAutomation" in web_actions and "evaluateJavascript" in web_actions,
        "P0: طبقة تنفيذ WebView المحلية مفقودة")
require("clickable:clickable" in web_actions and "editable:editable" in web_actions,
        "P0: لقطة WebView لا تقدم عقد العناصر اللازم للحلقة الذاتية")
require("sensitive" in web_actions and "type === 'password'" in web_actions,
        "P0: لقطة DOM لا تحجب حقول الاعتماد الحساسة")
require("HakimWebAutomation.snapshot" in executor and "HakimWebAutomation.clickText" in executor and "HakimWebAutomation.setText" in executor,
        "P0: منفذ الخطة لا يستخدم WebView حكيم كمسار التنفيذ الأول")
require("web == null && service == null" in executor,
        "P0: لا توجد بوابة تثبت فشل جميع مسارات التنفيذ قبل التوقف")
require("جولة تحقق مستقلة" in executor and "يلزم تحقق جديد" in executor,
        "P0: المنفذ قد يعلن الاكتمال بعد أفعال دون جولة تحقق")
require("requestedDone" in protocol and "requestedDone && actions.isEmpty()" in protocol,
        "P0: خطة تحتوي أفعالًا ما زالت قادرة على إعلان done=true قبل التنفيذ والتحقق")

require("HakimWebAutomation.snapshot" in autonomous and "HakimWebAutomation.clickText" in autonomous and "HakimWebAutomation.setText" in autonomous,
        "P0: الحلقة الذاتية لا تستخدم WebView حكيم كمسار أول")
require("service?.uiSnapshot" in autonomous and "HakimRuntime.visibleWebView" in autonomous,
        "P0: مسار Accessibility لم يتحول إلى احتياط بعد WebView")

# منع عودة الفجوة القديمة: فشل المسار المباشر لا يساوي فشل الغاية.
require("HakimRuntime.visibleWebView()" in natural,
        "P0: محرك الأفعال المباشرة لا يراعي WebView حكيم")
for phrase in [
    "أسلّم الرجوع لمسار حكيم الذاتي",
    "أسلّم الضغط لمسار WebView الأقل صلاحية مع التحقق",
    "أسلّم الكتابة لمسار WebView الأقل صلاحية مع التحقق",
    "خدمة الوصول غير متاحة؛ أسلّم الضغط لمسار حكيم الذاتي",
    "خدمة الوصول غير متاحة؛ أسلّم الكتابة لمسار حكيم الذاتي",
]:
    require(phrase in natural, f"P0: تسليم الأوامر المباشرة انحدر أو اختفى: {phrase}")
require(natural.count("return Result(false, false") >= 8,
        "P0: محرك الأفعال المباشرة قد يحول تعذر الوسيلة إلى handled=true ويوقف الغاية")
require("if (local.handled)" in chat and "runAutonomousCycle(cue, preferred, resolvedGoal)" in chat,
        "P0: واجهة المحادثة لا تسلّم handled=false إلى الحلقة الذاتية")

require('android:name=".HakimAccessibilityService"' not in manifest,
        "P0: النسخة الميدانية تعيد إعلان Accessibility وتكسر ملف Play Protect الآمن")
require("allowedTypes" in protocol and "arr.length() > 8" in protocol and "containsSecret" in protocol,
        "P0: خطة الاستدلال الداخلي غير مقيدة بما يكفي")
require("verify_in_app_reasoning.py" in workflow,
        "P0: لا توجد بوابة CI للاستدلال داخل واجهة حكيم")
require('FIELD_APK="app/build/outputs/apk/release/hakim-field-${VERSION_CODE}.apk"' in workflow,
        "P0: مسار إصدار CI لا يتبع versionCode ديناميكيًا")

print("HAKIM_MULTI_PROVIDER_IN_APP_REASONING=PASS")
