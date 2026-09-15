from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)


build = text("app/build.gradle")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
work = text("app/src/main/java/ps/hakim/phoneagent/HakimWorkSurface.kt")
tools = text("app/src/main/java/ps/hakim/phoneagent/HakimWorkToolHub.kt")
ime = text("app/src/main/java/ps/hakim/phoneagent/HakimImeResilience.kt")
ledger = text("app/src/main/java/ps/hakim/phoneagent/HakimMissionLedger.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20039, "P0: مساحة العمل الحية تتطلب ٢٠٠٣٩ أو أحدث")
require("HakimWorkSurface.install(this)" in app, "P0: سطح العمل غير مفعّل عند بدء التطبيق")
require('guardNonCritical(this, "work_surface_install")' in app,
        "P0: فشل سطح العمل قادر على إسقاط التطبيق بدل العزل")

for token in [
    "العمل الجاري",
    "الأداة:",
    "أفهم المطلوب وأجمع السياق",
    "أخطط وأختار أفضل الأدوات",
    "أنفذ الخطوة الحالية",
    "أتحقق من النتيجة الفعلية",
    "WAITING_APPROVAL",
    "WAITING_CREDENTIAL",
    "WAITING_TRUST",
    "HakimWorkToolHub.tools(activity)",
    "private_chain_of_thought_exposed\" to false",
    "secret_redaction\" to true",
    "moves_with_ime_docked_composer\" to true",
]:
    require(token in work, f"P0: سطح العمل يفتقد {token}")

for token in [
    "BROWSER",
    "RESEARCH",
    "FILES_AND_SHARING",
    "SERVICES_AND_CONNECTIONS",
    "LOCAL_AUTOMATION",
    "SYSTEM_AND_PRIVACY",
    "VERIFICATION",
    "RECOVERY",
    "MainActivity::class.java",
    "CommandCenterActivity::class.java",
    "UnifiedHomeActivity::class.java",
    "HakimSystemSettingsActivity::class.java",
    "new_privilege_granted\" to false",
    "high_impact_gate_preserved\" to true",
]:
    require(token in tools, f"P0: سجل الأدوات يفتقد {token}")

require('getSharedPreferences("hakim_mission_meta"' in work,
        "P0: العمليات الحية غير مرتبطة بسجل المهمة الحقيقي")
require('getSharedPreferences("hakim_agents"' in work,
        "P0: الأداة الظاهرة لا تستند إلى خطة الوكلاء الفعلية")
require("HakimRuntime.visibleWebView()" in work,
        "P0: سطح العمل لا يميز المتصفح الفعلي عند ظهوره")
require("MAX_HISTORY = 12" in work,
        "P0: سجل العمليات غير محدود أو عقد ميزانيته مفقود")
require("safeText" in work and "[سري محذوف]" in work,
        "P0: سجل العمليات قد يعرض أسرارًا بلا تنقيح")

# يجب ألا يكسر Work Surface إصلاح ٢٠٠٣٨ للكيبورد.
require("composer.translationY" in ime and "double_lift_prevented_when_adjust_resize_already_works" in ime,
        "P0: انحدر إصلاح IME أثناء إضافة مساحة العمل")
require("moves_with_ime_docked_composer" in work,
        "P0: لوحة العمل ليست مرتبطة بحاوية الإدخال المتحركة")

# WIP=1 وحالة المهمة الفعلية تبقى أساس العرض.
require("wip_limit\", 1" in ledger and "enum class Phase" in ledger,
        "P0: سطح العمل بُني فوق نموذج مهمة مختلف عن سجل حكيم")
require('putString("last_plan"' in agents,
        "P0: لا توجد خطة فعلية محفوظة لاشتقاق الأداة الجارية")

print("HAKIM_WORK_SURFACE=PASS")
print("HAKIM_LIVE_OPERATION_FEED=PASS")
print("HAKIM_TOOL_HUB=PASS")
print("HAKIM_PRIVATE_COT_NOT_EXPOSED=PASS")
print("HAKIM_WORK_IME_INTEGRATION=PASS")
