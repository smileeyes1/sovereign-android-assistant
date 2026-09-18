from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit("P0: " + msg)

app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
activity = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

oncreate_start = app.index("override fun onCreate")
bootstrap_start = app.index("private fun startNonCriticalBootstrap")
oncreate = app[oncreate_start:bootstrap_start]
bootstrap_end = app.index("private fun scheduleDeferredMaintenance")
bootstrap = app[bootstrap_start:bootstrap_end]

for token in [
    "HakimCrashShield.install(this)",
    "HakimQuranicInvariantKernel.requireInherited(\"app_start\")",
    "HakimConstitution.install(this)",
    "HakimImeResilience.install(this)",
    "HakimUiPolish.install(this)",
    "HakimWorkSurface.install(this)",
    "startNonCriticalBootstrap(safeRecovery, prefs)",
]:
    require(token in oncreate, f"عنصر البدء السريع مفقود: {token}")

for token in [
    "HakimLearning.initialize",
    "HakimProactiveEngine.initialize",
    "HakimIntegrationFabric.install",
    "restoreActiveMissionState",
    "HakimUnifiedRelay.start",
    "startLegacyBrowserIfPaired",
    "HakimHealthBeacon.sendAsync",
    "HakimConnectionResilience.install",
    "AutoUpdater.schedule",
    "HakimSelfCheck.schedule",
    "scheduleDeferredMaintenance",
]:
    require(token not in oncreate, f"عمل غير حرج ما زال يحجب Application.onCreate: {token}")

require("Thread {" in bootstrap, "bootstrap غير الحرج لا يعمل على خيط مستقل")
require("THREAD_PRIORITY_BACKGROUND" in bootstrap, "bootstrap الخلفي لا يخفض أولوية الخيط")
for token in [
    "HakimLearning.initialize(app)",
    "HakimProactiveEngine.initialize(app)",
    "HakimIntegrationFabric.install(app)",
    "restoreActiveMissionState()",
    "HakimConnectionResilience.install(app)",
    "AutoUpdater.schedule(app)",
    "HakimSelfCheck.schedule(app)",
    "scheduleDeferredMaintenance()",
]:
    require(token in bootstrap, f"تهيئة غير حرجة ضاعت بدل تأجيلها: {token}")

require("HakimLearning.initialize(this)" not in activity,
        "Activity تعيد تهيئة التعلم على main thread")
require("HakimProactiveEngine.initialize(this)" not in activity,
        "Activity تعيد تهيئة المبادرة على main thread")
require("HakimConstitution.install(this)" not in activity,
        "Activity تعيد تثبيت الدستور بعد أن ثبته Application")

m = re.search(r"versionCode\\s+(\\d+)", build)
require(m and int(m.group(1)) >= 20051, "إصلاح البدء غير الحاجب يجب ألا يرجع قبل ٢٠٠٥١")
candidate = int(m.group(1))
require(policy["current_field_version"] >= 20051,
        "خط الميدان يجب أن يتضمن ٢٠٠٥١ المثبت فعليًا أو أحدث")
require(policy["current_candidate_version"] == candidate,
        "سياسة التوقيع لا تطابق المرشح الحالي")
require(policy["field_evidence"]["version_code"] == policy["current_field_version"],
        "دليل الميدان لا يطابق current_field_version")
failures = policy.get("historical_field_failures", [])
f20050 = next((x for x in failures if x.get("version_code") == 20050), None)
require(f20050 is not None and f20050.get("stability") == "FAILED",
        "فشل استقرار ٢٠٠٥٠ غير محفوظ تاريخيًا")
require(f20050.get("evidence", {}).get("cold_start_wait_ms", 0) >= 10000,
        "دليل مهلة ٢٠٠٥٠ غير محفوظ")

print("HAKIM_20051_NONBLOCKING_APP_START=PASS")
print("HAKIM_20051_ACTIVITY_DUPLICATE_INIT_REMOVED=PASS")
print("HAKIM_20050_FIELD_IDENTITY_STABILITY_SEPARATION=PASS")
