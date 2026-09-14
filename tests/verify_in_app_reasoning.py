from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


web = text("app/src/main/java/ps/hakim/phoneagent/HakimWebReasoningBridge.kt")
web_actions = text("app/src/main/java/ps/hakim/phoneagent/HakimWebAutomation.kt")
bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
executor = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
manifest = text("app/src/main/AndroidManifest.xml")
build = text("app/build.gradle")
workflow = text(".github/workflows/android.yml")

require("versionCode 20022" in build and "2.0.22-capability-mesh" in build,
        "P0: الاستدلال الداخلي ليس ضمن إصدار شبكة التفوق ٢٠٠٢٢")
require("object HakimWebReasoningBridge" in web, "P0: جسر الاستدلال داخل حكيم مفقود")
require('CHAT_URL = "https://chatgpt.com/"' in web, "P0: محرك الويب ليس مثبتًا على أصل ChatGPT الآمن")
require("WebView" in web and "evaluateJavascript" in web, "P0: الاستدلال لا يعمل داخل WebView حكيم")
require("HakimReasoningProtocol.wrap" in web and "HakimReasoningProtocol.parse" in web,
        "P0: محرك الويب يتجاوز بروتوكول حكيم المقيد")
require('uri.scheme == "https"' in web and '== "chatgpt.com"' in web,
        "P0: لا يوجد تثبيت صارم للأصل الموثوق")
require("CookieManager.getInstance" in web and "setAcceptCookie(true)" in web,
        "P0: جلسة تسجيل الدخول داخل حكيم لا تستمر")
require("MainActivity::class.java" in web and 'putString("last_url", CHAT_URL)' in web,
        "P0: مسار تسجيل الدخول لمرة واحدة داخل متصفح حكيم غير موجود")
require("HakimRuntime.visibleWebView" in web, "P0: الجسر لا يستعيد جلسة تسجيل الدخول المرئية")
require("loadsImagesAutomatically = false" in web and "blockNetworkImage = true" in web,
        "P0: WebView الاستدلال المؤقت غير مخفف للهاتف")
require("hidden.destroy()" in web and "removeCallbacksAndMessages" in web,
        "P0: محرك الاستدلال المؤقت قد يسرب ذاكرة/حلقات")
require("MAX_RESPONSE_ATTEMPTS" in web and "MAX_LOGIN_WAIT_ATTEMPTS" in web,
        "P0: الاستدلال/تسجيل الدخول بلا حدود توقف")
require("HakimWebReasoningBridge.ask" in bridge,
        "P0: الجسر العام لا يبدأ بالاستدلال داخل حكيم")
pos_web = bridge.find("HakimWebReasoningBridge.ask")
pos_access = bridge.find("HakimAccessibilityService.instance")
require(pos_web >= 0 and pos_access > pos_web,
        "P0: Accessibility ما زال شرط الاستدلال الأول بدل مسار داخلي احتياطي")

require("object HakimWebAutomation" in web_actions and "evaluateJavascript" in web_actions,
        "P0: طبقة تنفيذ WebView المحلية مفقودة")
require("clickable:clickable" in web_actions and "editable:editable" in web_actions,
        "P0: لقطة WebView لا تقدم عقد العناصر اللازم للحلقة الذاتية")
require("sensitive" in web_actions and "type === 'password'" in web_actions,
        "P0: لقطة DOM لا تحجب حقول الاعتماد الحساسة")
require("HakimWebAutomation.snapshot" in executor and "HakimWebAutomation.clickText" in executor and "HakimWebAutomation.setText" in executor,
        "P0: منفذ الخطة لا يستخدم WebView حكيم كمسار التنفيذ الأول")
require("خدمة الوصول غير مفعلة أثناء خطة الاستدلال" not in executor,
        "P0: منفذ الخطة ما زال يفشل مبكرًا لمجرد غياب Accessibility")
require("web == null && service == null" in executor,
        "P0: لا توجد بوابة تثبت فشل جميع المسارات قبل التوقف")
require("جولة تحقق مستقلة" in executor and "يلزم تحقق جديد" in executor,
        "P0: المنفذ قد يعلن الاكتمال بعد أفعال دون جولة تحقق")
require("requestedDone" in protocol and "requestedDone && actions.isEmpty()" in protocol,
        "P0: خطة تحتوي أفعالًا ما زالت قادرة على إعلان done=true قبل التنفيذ والتحقق")

require("HakimWebAutomation.snapshot" in autonomous and "HakimWebAutomation.clickText" in autonomous and "HakimWebAutomation.setText" in autonomous,
        "P0: الحلقة الذاتية لا تستخدم WebView حكيم كمسار أول")
require('recordVerification(activity, false, "خدمة الوصول غير مفعلة")' not in autonomous,
        "P0: الحلقة الذاتية ما زالت تتوقف فورًا عند غياب Accessibility")
require("service?.uiSnapshot" in autonomous and "HakimRuntime.visibleWebView" in autonomous,
        "P0: مسار Accessibility لم يتحول إلى احتياط بعد WebView")

require('android:name=".HakimAccessibilityService"' not in manifest,
        "P0: النسخة الميدانية تعيد إعلان Accessibility وتكسر ملف Play Protect الآمن")
require("allowedTypes" in protocol and "arr.length() > 8" in protocol and "containsSecret" in protocol,
        "P0: خطة الاستدلال الداخلي غير مقيدة بما يكفي")
require("verify_in_app_reasoning.py" in workflow,
        "P0: لا توجد بوابة CI للاستدلال داخل واجهة حكيم")
require("hakim-field-20022.apk" in workflow,
        "P0: مسار إصدار CI لم يرتفع إلى ٢٠٠٢٢")

print("HAKIM_IN_APP_REASONING=PASS")
