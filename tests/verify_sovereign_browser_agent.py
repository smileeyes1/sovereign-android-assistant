from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

build = text("app/build.gradle")
manifest = text("app/src/main/AndroidManifest.xml")
runtime = text("app/src/main/java/ps/hakim/phoneagent/HakimRuntime.kt")
web = text("app/src/main/java/ps/hakim/phoneagent/HakimWebAutomation.kt")
agent = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignBrowserAgent.kt")
tabs = text("app/src/main/java/ps/hakim/phoneagent/HakimBrowserTabs.kt")
registry = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignToolRegistry.kt")
skills = text("app/src/main/java/ps/hakim/phoneagent/HakimSkillFactory.kt")
browser_runtime = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignBrowserRuntime.kt")
downloads = text("app/src/main/java/ps/hakim/phoneagent/HakimBrowserDownloadLedger.kt")
work = text("app/src/main/java/ps/hakim/phoneagent/HakimWorkToolHub.kt")
legacy_auto = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
legacy_plan = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
identity = text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20040, "P0: المتصفح الوكيل يتطلب الإصدار ٢٠٠٤٠ أو أحدث")
require("2.0.40-sovereign-browser-agent" in build, "P0: اسم إصدار ٢٠٠٤٠ غير متزامن")
require('"current_candidate_version": 20040' in identity, "P0: هوية مرشح التوقيع ليست ٢٠٠٤٠")

# طبقات التحكم المطلوبة وترتيبها، مع fallback مأذون لا افتراضي.
for token in ["DOM", "AUTHORIZED_JS", "COORDINATE", "ANDROID_UI", "clickLayered", "coordinateClick", "androidUiFallback"]:
    require(token in agent, f"P0: طبقة تحكم مفقودة: {token}")
require('JSONArray(listOf("DOM", "AUTHORIZED_JS", "COORDINATE", "ANDROID_UI_AUTHORIZED"))' in agent,
        "P0: ترتيب طبقات التحكم غير مثبت في الحالة")
require("HakimAccessibilityService.instance" in agent, "P0: fallback Android UI لا يتحقق من التفويض الفعلي")
require('android:name=".HakimAccessibilityService"' not in manifest,
        "P0: تم تفعيل AccessibilityService افتراضيًا خلاف أقل صلاحية")

# DOM غني دون تسريب قيم الحقول.
for token in ["data-hakim-ref", "bounds", "selected", "checked", "scrollable", "clickRef", "setTextByRef", "selectOption", "elementCenter", "verifyText"]:
    require(token in web, f"P0: DOM automation يفتقد {token}")
require("el.removeAttribute('value')" in web and "contenteditable" in web,
        "P0: لقطة الصفحة قد تعيد قيم الحقول القابلة للتحرير")
require("arbitrary_javascript" in agent and '.put("arbitrary_javascript", false)' in agent,
        "P0: لا يوجد عقد يمنع JavaScript الحر")

# المسارات القديمة يجب أن ترث DOM→JS→الإحداثيات قبل Android UI، لا تتجاوز BrowserAgent الجديد.
for token in ["resolveRef(web, target", "semanticThenCoordinate", "semanticClick", "coordinateTap", "MotionEvent.ACTION_DOWN", "MotionEvent.ACTION_UP"]:
    require(token in web, f"P0: مسار التوافق متعدد الطبقات يفتقد {token}")
require("HakimWebAutomation.clickText" in legacy_auto and "HakimAccessibilityService.instance" in legacy_auto,
        "P0: المنفذ الذاتي القديم لم يعد يرث WebView ثم Android UI")
require("HakimWebAutomation.clickText" in legacy_plan and "HakimAccessibilityService.instance" in legacy_plan,
        "P0: منفذ خطة الاستدلال القديم لم يعد يرث WebView ثم Android UI")
require(web.index("resolveRef(web, target") < web.index("semanticThenCoordinate") < web.index("coordinateTap"),
        "P0: ترتيب DOM ثم JS ثم الإحداثيات في مسار التوافق غير مثبت")

# تبويبات وجلسات محلية.
for token in ["MAX_TABS = 8", "newTab", "switchTo", "close", "CookieManager.getInstance().flush", "single_webview_memory_efficient"]:
    require(token in tabs, f"P0: التبويبات تفتقد {token}")
require("HakimSovereignBrowserRuntime.attach" in runtime, "P0: المتصفح الفعلي غير مربوط ببيئة ٢٠٠٤٠")
for token in ["تبويبات", "أدوات", "HakimBrowserTabs", "HakimSovereignToolRegistry", "installEnhancedDownload"]:
    require(token in browser_runtime, f"P0: واجهة التنفيذ الفعلية تفتقد {token}")

# رفع/تنزيل/جلسات: الرفع يبقى WebChromeClient القائم، التنزيل أصبح قابلاً للتحقق.
main = text("app/src/main/java/ps/hakim/phoneagent/MainActivity.kt")
require("onShowFileChooser" in main and "ACTION_OPEN_DOCUMENT" in main, "P0: رفع الملفات غير موجود")
require("CookieManager.getInstance().getCookie" in browser_runtime, "P0: تنزيل الجلسة لا يحمل كوكيز الموقع المأذونة")
for token in ["recordStart", "DownloadManager.Query", "STATUS_SUCCESSFUL", "authorization_headers_persisted"]:
    require(token in downloads, f"P0: سجل التنزيل والتحقق يفتقد {token}")

# سجل أدوات موحد بالمواصفات الحاكمة.
for token in ["permissionRequirements", "successCondition", "verification", "rollback", "Impact", "localFirst", "freeFirst"]:
    require(token in registry, f"P0: ToolSpec يفتقد {token}")
for tool_id in ["browser.dom", "browser.js", "browser.coordinate", "android.ui.authorized", "browser.tabs", "browser.upload", "browser.download", "browser.session", "files", "services", "verification", "recovery", "skill.factory"]:
    require(tool_id in registry, f"P0: سجل الأدوات يفتقد {tool_id}")

# مصنع المهارات: DSL محدود، اختبار قبل التسجيل، لا صلاحيات/كود/JS حر.
for token in ["sandboxValidate", "proposeAndRegister", "VERIFIED", "value_ref", "NEEDS_SIGNED_BUILD", "permission_expansion", "nativeGapDisposition"]:
    require(token in skills, f"P0: مصنع المهارات يفتقد {token}")
require("allowedActions" in skills and "navigate" in skills and "verify_text" in skills, "P0: DSL المهارات غير محدد")
require('"arbitrary_native_code_execution", false' in skills and '"arbitrary_javascript_execution", false' in skills,
        "P0: مصنع المهارات يسمح بتنفيذ كود حر")
require("القيم الخام لا تُحفظ" in skills, "P0: المهارات قد تحفظ قيمًا حساسة")

# local-first/free-first/offline/resume والحواجز الحاكمة.
for token in ["LOAD_CACHE_ELSE_NETWORK", "pending_safe_navigation", "resumePendingNavigation", 'put("paid_api_required", false)', 'put("captcha_bypass", false)', 'put("authentication_bypass", false)', 'put("payment_bypass", false)']:
    require(token in agent, f"P0: الاستمرارية/السيادة تفتقد {token}")
require("governedBarrier" in agent and "CAPTCHA" in agent.upper(), "P0: حاجز CAPTCHA/الدفع غير صريح")
require("لا توسع الصلاحيات" in work and "مصنع المهارات" in work, "P0: واجهة Work لا تشرح حدود مصنع الأدوات")

# سجل العمليات حيّ ولا يحفظ value.
for token in ["live_state", "live_action", "live_layer", "live_evidence", 'put("value_persisted", false)']:
    require(token in agent, f"P0: سجل العمليات الحي يفتقد {token}")
require("private_chain_of_thought" not in agent.lower() or 'private_chain_of_thought_exposed' not in agent,
        "P0: الوكيل لا يحتاج سلسلة التفكير الخاصة")

print("HAKIM_SOVEREIGN_BROWSER_AGENT=PASS")
print("HAKIM_BROWSER_LAYERED_CONTROL=PASS")
print("HAKIM_LEGACY_EXECUTORS_INHERIT_LAYERING=PASS")
print("HAKIM_BROWSER_TABS_SESSIONS=PASS")
print("HAKIM_BROWSER_UPLOAD_DOWNLOAD=PASS")
print("HAKIM_SOVEREIGN_TOOL_REGISTRY=PASS")
print("HAKIM_SKILL_FACTORY=PASS")
print("HAKIM_LOCAL_FREE_OFFLINE_FIRST=PASS")
print("HAKIM_BROWSER_GOVERNED_BARRIERS=PASS")
