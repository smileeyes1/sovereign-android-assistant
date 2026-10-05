from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

router = text("app/src/main/java/ps/hakim/phoneagent/HakimToolRouter.kt")
orchestrator = text("app/src/main/java/ps/hakim/phoneagent/HakimOrchestrator.kt")
relay = text("app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt")
build = text("app/build.gradle")

require('canonical_live_state", "HakimTaskManager"' in orchestrator, "P0: سجل الحالة الحي ليس موحدًا")
require('transition_journal", "HakimExecutiveLoop"' in orchestrator, "P0: فصل دفتر الانتقالات عن سجل الحالة مفقود")
require('phone_connector", "SecureRelay"' in orchestrator, "P0: موصل الهاتف الآمن غير مثبت")
require('resume_after_disconnect", true' in orchestrator, "P0: الاستئناف بعد الانقطاع غير معلن")
require('idempotency", true' in orchestrator, "P0: عقد idempotency مفقود")
require("HakimTaskManager.consumeResumeRequest" in orchestrator, "P0: الاستئناف المحفوظ غير موصول")
require("HakimToolRouter.decide" in relay, "P0: عمليات القناة لا تمر عبر Tool Router")
require('approval_boundary_preserved", true' in router, "P0: بوابة الموافقة لم تُحفظ")
require('permissions_added", false' in router, "P0: الموجّه يدعي توسيع الصلاحيات")
require("HakimFaultContainment.canExecuteHighImpact" in router, "P0: الأثر العالي لا يحترم fault containment")
require("claimRemoteRequest" in relay and "duplicate_request" in relay, "P0: idempotency للقناة تراجع")
require("showApproval" in relay, "P0: موافقة الهاتف للأفعال المتغيرة تراجعت")
m=re.search(r"versionCode\s+(\d+)",build)
require(m is not None and int(m.group(1))>=20336, "P0: تراجع رقم الإصدار دون خط ٢٠٣٣٦")
require("vpn-dns-fail-open-r21" in build, "P0: فقد خط الأساس الشبكي الموروث")
require("termux-local-control-r27" in build, "P0: فقد خط ٢٠٣٣٦ المحلي أثناء النقل الأمامي")
print("HAKIM_UNIFIED_ORCHESTRATOR_CONTRACT=PASS")
