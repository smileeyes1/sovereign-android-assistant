from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


resource = text("app/src/main/java/ps/hakim/phoneagent/HakimResourceGovernor.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
proactive = text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")
evolution = text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
workflow = text(".github/workflows/android.yml")

require("object HakimResourceGovernor" in resource, "P0: حاكم الموارد مفقود")
for mode in ["PERFORMANCE", "BALANCED", "CONSERVE", "PRESSURE"]:
    require(mode in resource, f"P0: وضع الموارد مفقود: {mode}")
for signal in ["MemoryInfo", "BATTERY_PROPERTY_CAPACITY", "isPowerSaveMode", "currentThermalStatus", "isActiveNetworkMetered"]:
    require(signal in resource, f"P0: حاكم الموارد لا يقيس: {signal}")
require("quality_and_governance_never_downgraded" in resource,
        "P0: لا توجد قاعدة تمنع خفض جودة القرار لتوفير الموارد")
require("only_nonessential_background_is_throttled" in resource,
        "P0: توفير الموارد قد يخفض العمل الجوهري")
require("no_large_on_device_model_required" in resource,
        "P0: لا توجد حماية من نموذج محلي ضخم غير لازم")
require("TRIM_MEMORY_RUNNING_LOW" in resource and "noteLowMemory" in resource,
        "P0: ضغط ذاكرة أندرويد لا يدخل الحاكم")

require("HakimUnifiedRelay.isConfigured(app)" in app,
        "P0: التطبيق قد يبدأ قناة شبكة دائمة بلا إعداد")
require("scheduleDeferredMaintenance" in app and "HakimResourceGovernor.shouldRunStartupMaintenance" in app,
        "P0: اندفاع بدء التطبيق غير محكوم بالموارد")
require("THREAD_PRIORITY_BACKGROUND" in app,
        "P0: صيانة بدء التطبيق تنافس الواجهة بأولوية عادية")
require("onTrimMemory" in app and "HakimResourceGovernor.noteTrimMemory" in app,
        "P0: التطبيق لا يستجيب لضغط الذاكرة")
require("onLowMemory" in app and "HakimResourceGovernor.noteLowMemory" in app,
        "P0: التطبيق لا يستجيب لنفاد الذاكرة")

require("DEFERRED_RESOURCE_PRESSURE" in proactive and "HakimResourceGovernor.Mode.PRESSURE" in proactive,
        "P0: المبادرة لا تؤجل الخلفية عند ضغط الموارد")
require("HakimResourceGovernor.Mode.CONSERVE" in proactive,
        "P0: المبادرة لا تملك وضع صيانة خفيف")
require("canUseRealtimeBackgroundNetwork" in proactive,
        "P0: الشبكة الخلفية الفورية لا تراعي موارد الهاتف/الشبكة")
require("resource_adaptive_background" in proactive,
        "P0: حالة المبادرة لا تثبت التكيف مع الموارد")

require("HakimResourceGovernor.snapshot" in evolution and "DEFERRED_RESOURCE_PRESSURE" in evolution,
        "P0: دورة التطور لا تؤجل نفسها عند الضغط")
require("LIGHT_PASS" in evolution and "FULL_PASS" in evolution,
        "P0: التطور لا يفرق بين الصيانة الخفيفة والكاملة")
require("THREAD_PRIORITY_BACKGROUND" in evolution,
        "P0: دورة التطور قد تنافس المهمة الحالية")

require('"resource_governor"' in fabric and "resource_governor_integrated" in fabric,
        "P0: حاكم الموارد غير مدمج في نسيج حكيم")
require("resourceOk" in fabric and "&& resourceOk &&" in fabric,
        "P0: فقد حاكم الموارد لا يسقط سلامة القلب")
require("احمِ موارد الهاتف كما تحمي صحة القرار" in governance,
        "P0: نواة حكيم لا تحمل عقد الأداء على الهاتف")
require("verify_resource_governor.py" in workflow,
        "P0: لا توجد بوابة CI لأداء الهاتف وحاكم الموارد")

print("HAKIM_RESOURCE_GOVERNOR=PASS")
