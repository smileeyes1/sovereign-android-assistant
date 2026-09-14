from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


proactive = text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
evolution = text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
integration = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")

# الافتراضي: المبادرة مفعلة، لكن السلطة لا تتوسع.
require('putBoolean("enabled", true)' in proactive, "P0: المبادرة الذاتية ليست مفعلة افتراضيًا")
require('"beneficial_safe_actions_auto", true' in proactive, "P0: الأعمال المفيدة الآمنة ليست تلقائية")
require('"high_impact_never_silently_authorized", true' in proactive, "P0: لا توجد حماية صريحة من تفويض الأثر العالي بصمت")
require('"silence_not_consent", true' in proactive, "P0: السكوت قد يتحول إلى موافقة عبر المبادرة")
require('"no_secret_or_permission_escalation", true' in proactive, "P0: المبادرة قد توسع سرًا أو صلاحية")

# الخلفية تنفذ صيانة آمنة فقط ولا تتلاعب بواجهات المستخدم مباشرة.
for token in ["HakimLearning.maintenance", "HakimAdaptiveLearning.consolidate", "HakimConnectionResilience.recover", "HakimConstraintDoctor.run", "AutoUpdater.checkAsync"]:
    require(token in proactive, f"P0: دورة المبادرة الخلفية فقدت {token}")
require("HakimAutonomousExecutor.run" not in proactive, "P0: محرك الخلفية ينفذ واجهة تفاعلية بلا Activity/سياق")
require("service.action(" not in proactive, "P0: محرك المبادرة ينقر/يكتب مباشرة في الخلفية")
require("startActivity(" not in proactive, "P0: محرك المبادرة يفتح واجهات من الخلفية بدل استخدام بوابة النظام")

# الاستئناف التلقائي في الواجهة يمر مجددًا بالمصفوفة ويستبعد الحالات المحمية.
require("foregroundOpportunity" in proactive and "HakimDecisionMatrix.evaluate" in proactive, "P0: الاستئناف التلقائي لا يعاد تصنيفه")
for phase in ["WAITING_APPROVAL", "WAITING_CREDENTIAL", "WAITING_TRUST", "CANCELLED", "BLOCKED"]:
    require(phase in proactive, f"P0: الاستئناف التلقائي لا يحمي حالة {phase}")
require("maybeResumeProactively" in chat and "HakimProactiveEngine.foregroundOpportunity" in chat, "P0: الواجهة لا تستأنف المهمة الآمنة تلقائيًا")
require("plan.needsApproval" in chat and "plan.sensitiveInputDetected" in chat, "P0: الاستئناف التلقائي لا يعيد تطبيق بوابات الأثر/الأسرار")

# يعمل عند بدء التطبيق وفي دورة التطور دون طلب جديد.
require("HakimProactiveEngine.initialize(this)" in app, "P0: المبادرة لا تبدأ مع التطبيق")
require('runSafeBackground(this, "app_start")' in app, "P0: لا توجد دورة مبادرة عند بدء التطبيق")
require("HakimProactiveEngine.runSafeBackground" in evolution, "P0: المبادرة ليست جزءًا من الدورة الدورية")

# المستخدم يحتفظ بحق الإيقاف العام للمبادرة.
require("proactiveEnabled" in settings, "P0: لا يوجد مفتاح سيادي للمبادرة")
require("HakimProactiveEngine.setEnabled" in settings, "P0: إعداد المبادرة لا يغير حالتها الفعلية")

# المبادرة جزء من القلب والفحص والقرار، لا طبقة جانبية.
require('"proactive_engine"' in integration and "proactive_engine_integrated" in integration, "P0: المبادرة غير مدمجة في نسيج التكامل")
require("HakimProactiveEngine.status" in selfcheck and "المبادرة الذاتية مدمجة" in selfcheck, "P0: الفحص الذاتي لا يحرس المبادرة")
require("HakimProactiveEngine.promptContext" in sovereign and "ابحث ذاتيًا عن كل مكسب مفيد آمن" in sovereign, "P0: القرار السيادي لا يحمل مبدأ المبادرة")

print("HAKIM_PROACTIVE_AUTONOMY=PASS")
