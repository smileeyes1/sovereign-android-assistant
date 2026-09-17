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
identity = text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20040, "P0: المتصفح الوكيل يتطلب الإصدار ٢٠٠٤٠ أو أحدث")
require("sovereign-browser-agent" in build or "hakim-sovereign-home-local-first" in build, "P0: اسم الإصدار لا يحفظ خط المتصفح السيادي")
require(f'"current_candidate_version": {int(m.group(1))}' in identity, "P0: هوية مرشح التوقيع لا تطابق الإصدار الحالي")

for token in ["DOM", "AUTHORIZED_JS", "COORDINATE", "ANDROID_UI", "clickLayered", "coordinateClick", "androidUiFallback"]:
    require(token in agent, f"P0: طبقة تحكم مفقودة: {token}")
order = agent.index('JSONArray(listOf("DOM", "AUTHORIZED_JS", "COORDINATE", "ANDROID_UI_AUTHORIZED"))')
require(order >= 0, "P0: ترتيب طبقات التحكم غير مثبت في الحالة")
require("HakimAccessibilityService.instance" in agent, "P0: fallback Android UI لا يتحقق من التفويض الفعلي")
require('android:name=".HakimAccessibilityService"' not in manifest,
        "P0: تم تفعيل AccessibilityService افتراضيًا خلاف أقل صلاحية")

for token in ["data-hakim-ref", "bounds", "selected", "checked", "scrollable", "clickRef", "setTextByRef", "selectOption", "elementCenter", "verifyText"]:
    require(token in web, f"P0: DOM automation يفتقد {token}")
require("el.removeAttribute('value')" in web and "contenteditable" in web,
        "P0: لقطة الصفحة قد تعيد قيم الحقول القابلة للتحرير")
require("arbitrary_javascript" in agent and '.put("arbitrary_javascript", false)' in agent,
        "P0: لا يوجد عقد يمنع JavaScript الحر")

for token in ["MAX_TABS = 8", "newTab", "switchTo", "close", "CookieManager.getInstance().flush", "single_webview_memory_efficient"]:
    require(token in tabs, f"P0: التبويبات تفتقد {token}")
require("HakimSovereignBrowserRuntime.attach" in runtime, "P0: المتصفح الفعلي غير مربوط ببيئة ٢٠٠٤٠")
for token in ["تبويبات", "أدوات", "HakimBrowserTabs", "HakimSovereignToolRegistry", "installEnhancedDownload"]:
    require(token in browser_runtime, f"P0: واجهة التنفيذ الفعلية تفتقد {token}")

main = text("app/src/main/java/ps/hakim/phoneagent/MainActivity.kt")
require("onShowFileChooser" in main and "ACTION_OPEN_DOCUMENT" in main, "P0: رفع الملفات غير موجود")
require("CookieManager.getInstance().getCookie" in browser_runtime, "P0: تنزيل الجلسة لا يحمل كوكيز الموقع المأذونة")
for token in ["recordStart", "DownloadManager.Query", "STATUS_SUCCESSFUL", "authorization_headers_persisted"]:
    require(token in downloads, f"P0: سجل التنزيل والتحقق يفتقد {token}")

for token in ["permissionRequirements", "successCondition", "verification", "rollback", "Impact", "localFirst", "freeFirst"]:
    require(token in registry, f"P0: ToolSpec يفتقد {token}")
for tool_id in ["browser.dom", "browser.js", "browser.coordinate", "android.ui.authorized", "browser.tabs", "browser.upload", "browser.download", "browser.session", "files", "services", "verification", "recovery", "skill.factory"]:
    require(tool_id in registry, f"P0: سجل الأدوات يفتقد {tool_id}")

for token in ["sandboxValidate", "proposeAndRegister", "VERIFIED", "value_ref", "NEEDS_SIGNED_BUILD", "permission_expansion", "nativeGapDisposition"]:
    require(token in skills, f"P0: مصنع المهارات يفتقد {token}")
require("allowedActions" in skills and "navigate" in skills and "verify_text" in skills, "P0: DSL المهارات غير محدد")
require('"arbitrary_native_code_execution", false' in skills and '"arbitrary_javascript_execution", false' in skills,
        "P0: مصنع المهارات يسمح بتنفيذ كود حر")
require("القيم الخام لا تُحفظ" in skills, "P0: المهارات قد تحفظ قيمًا حساسة")

for token in ["LOAD_CACHE_ELSE_NETWORK", "pending_safe_navigation", "resumePendingNavigation", 'put("paid_api_required", false)', 'put("captcha_bypass", false)', 'put("authentication_bypass", false)', 'put("payment_bypass", false)']:
    require(token in agent, f"P0: الاستمرارية/السيادة تفتقد {token}")
require("governedBarrier" in agent and "CAPTCHA" in agent.upper(), "P0: حاجز CAPTCHA/الدفع غير صريح")
require("لا توسع الصلاحيات" in work and "مصنع المهارات" in work, "P0: واجهة Work لا تشرح حدود مصنع الأدوات")

for token in ["live_state", "live_action", "live_layer", "live_evidence", 'put("value_persisted", false)']:
    require(token in agent, f"P0: سجل العمليات الحي يفتقد {token}")
require("private_chain_of_thought" not in agent.lower() or 'private_chain_of_thought_exposed' not in agent,
        "P0: الوكيل لا يحتاج سلسلة التفكير الخاصة")

print("HAKIM_SOVEREIGN_BROWSER_AGENT=PASS")
print("HAKIM_BROWSER_LAYERED_CONTROL=PASS")
print("HAKIM_BROWSER_TABS_SESSIONS=PASS")
print("HAKIM_BROWSER_UPLOAD_DOWNLOAD=PASS")
print("HAKIM_SOVEREIGN_TOOL_REGISTRY=PASS")
print("HAKIM_SKILL_FACTORY=PASS")
print("HAKIM_LOCAL_FREE_OFFLINE_FIRST=PASS")
print("HAKIM_BROWSER_GOVERNED_BARRIERS=PASS")
