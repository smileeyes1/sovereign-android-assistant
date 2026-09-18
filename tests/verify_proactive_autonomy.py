from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


proactive = text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")
quran_bootstrap = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranBootstrap.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
boot = text("app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt")
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
for token in ["HakimLearning.maintenance", "HakimAdaptiveLearning.consolidate", "HakimConnectionResilience.recover", "HakimConstraintDoctor.run", "AutoUpdater.checkAsync", "AutoUpdater.startRealtimeListener"]:
    require(token in proactive, f"P0: دورة المبادرة الخلفية فقدت {token}")
require("HakimAutonomousExecutor.run" not in proactive, "P0: محرك الخلفية ينفذ واجهة تفاعلية بلا Activity/سياق")
require("service.action(" not in proactive, "P0: محرك المبادرة ينقر/يكتب مباشرة في الخلفية")
require("startActivity(" not in proactive, "P0: محرك المبادرة يفتح واجهات من الخلفية بدل استخدام بوابة النظام")
require("HakimResourceGovernor.Mode.PRESSURE" in proactive and "DEFERRED_RESOURCE_PRESSURE" in proactive,
        "P0: المبادرة لا تؤجل الصيانة غير الجوهرية عند ضغط الهاتف")
require("HakimResourceGovernor.Mode.CONSERVE" in proactive and "resource_adaptive_background" in proactive,
        "P0: المبادرة لا تملك صيانة خفيفة متكيفة مع الموارد")

# تأسيس القرآن المتحقق ذاتي، لكنه لا يستهلك شبكة محسوبة ولا يحول الرابط إلى مصدر ثقة.
require("HakimQuranBootstrap.syncIfNeeded" in proactive,
        "P0: تأسيس القرآن المتحقق غير داخل دورة المبادرة الذاتية")
require("HakimVerifiedQuranCorpus.isReady" in proactive,
        "P0: المبادرة قد تعيد تنزيل القرآن رغم وجود قاعدة متحققة")
require("HakimResourceGovernor.canUseRealtimeBackgroundNetwork" in proactive and
        "HakimResourceGovernor.Mode.CONSERVE" in proactive,
        "P0: جلب القرآن التلقائي لا يحترم قيود الشبكة/الموارد")
require('"verified_quran_bootstrap_integrated", true' in proactive and
        '"verified_quran_bootstrap_unmetered_and_resource_guarded", true' in proactive,
        "P0: حالة المبادرة لا تثبت دمج التأسيس القرآني المحروس")
require("official_hash_is_authority_not_url" in quran_bootstrap,
        "P0: التأسيس القرآني قد يثق بالرابط بدل بصمة المصدر الرسمي")
require("metered_background_download_forbidden" in quran_bootstrap and "MAX_ARCHIVE_BYTES" in quran_bootstrap,
        "P0: تنزيل القرآن التلقائي بلا حاجز شبكة محسوبة/حجم")
require("HakimVerifiedQuranCorpus.importOfficialArchive" in quran_bootstrap,
        "P0: الملف المنزل لا يمر عبر التحقق الرسمي الكامل")

# الاستئناف التلقائي في الواجهة يمر مجددًا بالمصفوفة ويستبعد الحالات المحمية.
require("foregroundOpportunity" in proactive and "HakimSovereignOneKernel.frame" in proactive, "P0: الاستئناف التلقائي لا يعاد تصنيفه عبر النواة الواحدة")
for phase in ["WAITING_APPROVAL", "WAITING_CREDENTIAL", "WAITING_TRUST", "CANCELLED", "BLOCKED"]:
    require(phase in proactive, f"P0: الاستئناف التلقائي لا يحمي حالة {phase}")
require("maybeResumeProactively" in chat and "HakimProactiveEngine.foregroundOpportunity" in chat, "P0: الواجهة لا تستأنف المهمة الآمنة تلقائيًا")
require("plan.needsApproval" in chat and "plan.sensitiveInputDetected" in chat, "P0: الاستئناف التلقائي لا يعيد تطبيق بوابات الأثر/الأسرار")

# بدء التطبيق: نحافظ على الفورية لكن بلا اندفاع يزاحم الواجهة على هاتف محدود الموارد.
require("HakimProactiveEngine.initialize(app)" in app, "P0: المبادرة لا تبدأ ضمن bootstrap التطبيق")
require("scheduleDeferredMaintenance" in app and "HakimResourceGovernor.shouldRunStartupMaintenance" in app,
        "P0: صيانة بدء التطبيق غير مؤجلة/غير محكومة بالموارد")
require('runSafeBackground(app, "app_start_deferred")' in app,
        "P0: لا توجد دورة مبادرة مؤجلة بعد بدء التطبيق")
require("AutoUpdater.schedule(app)" in app,
        "P0: مسار التحديث الدوري لا يثبت ضمن bootstrap التطبيق")
require("HakimResourceGovernor.canUseRealtimeBackgroundNetwork(app)" in app and "AutoUpdater.startRealtimeListener(app)" in app,
        "P0: التحديث الفوري لا يُستعاد عندما تسمح الموارد")
require("THREAD_PRIORITY_BACKGROUND" in app,
        "P0: صيانة البدء قد تنافس واجهة المستخدم بأولوية عادية")

# بعد الإقلاع: الجداول تُثبت دائمًا، والعمل الفوري يمر بحاكم الموارد.
require("AutoUpdater.schedule(context)" in boot and "HakimSelfCheck.schedule(context)" in boot,
        "P0: الاستمرارية الدورية لا تُستعاد بعد الإقلاع")
require("HakimResourceGovernor.snapshot(context)" in boot,
        "P0: الإقلاع لا يفحص ضغط موارد الهاتف")
require("AutoUpdater.startRealtimeListener(context)" in boot and "AutoUpdater.checkAsync(context)" in boot,
        "P0: مسار التحديث الفوري مفقود عند توفر الموارد")
require("HakimProactiveEngine.initialize(context)" in boot, "P0: المبادرة لا تستعاد بعد الإقلاع")
require("HakimProactiveEngine.runSafeBackground" in evolution, "P0: المبادرة ليست جزءًا من الدورة الدورية")
require('"realtime_update_reasserted", true' in proactive, "P0: لا توجد حالة مثبتة لإعادة ضمان التحديث الفوري")

# المستخدم يحتفظ بحق الإيقاف العام للمبادرة.
require("proactiveEnabled" in settings, "P0: لا يوجد مفتاح سيادي للمبادرة")
require("HakimProactiveEngine.setEnabled" in settings, "P0: إعداد المبادرة لا يغير حالتها الفعلية")

# المبادرة جزء من القلب والفحص والقرار، لا طبقة جانبية.
require('"proactive_engine"' in integration and "proactive_engine_integrated" in integration, "P0: المبادرة غير مدمجة في نسيج التكامل")
require("HakimProactiveEngine.status" in selfcheck and "المبادرة الذاتية مدمجة" in selfcheck, "P0: الفحص الذاتي لا يحرس المبادرة")
require("HakimProactiveEngine.promptContext" in sovereign and "ابحث ذاتيًا عن كل مكسب مفيد آمن" in sovereign, "P0: القرار السيادي لا يحمل مبدأ المبادرة")

print("HAKIM_PROACTIVE_AUTONOMY=PASS")
