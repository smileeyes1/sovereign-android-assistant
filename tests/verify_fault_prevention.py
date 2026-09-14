from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def req(cond, msg):
    if not cond:
        raise SystemExit(msg)

ledger = text("app/src/main/java/ps/hakim/phoneagent/HakimFaultLedger.kt")
evolution = text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
field = text("app/src/main/java/ps/hakim/phoneagent/HakimFieldValidation.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
workflow = text(".github/workflows/android.yml")

req("object HakimFaultLedger" in ledger, "P0: سجل الأعطال السببي مفقود")
req("silent_failure_forbidden" in ledger, "P0: لا توجد قاعدة تمنع الفشل الصامت")
req("root_cause_required_on_repeat" in ledger, "P0: تكرار الخطأ لا يفرض معالجة السبب الجذري")
req("secret_redaction" in ledger and "[سر محجوب]" in ledger, "P0: سجل الأعطال قد يسرب سرًا")
req("repeat_count" in ledger and ">= 3" in ledger, "P0: لا يوجد كشف للأعطال المادية المتكررة")
req("HakimFaultLedger.record(applicationContext, \"evolution_job\"" in evolution, "P0: دورة التطور لا تسجل فشلها")
req("FAILED_RECORDED" in evolution, "P0: فشل دورة التطور قد يختفي بلا حالة صريحة")
req("HakimFaultLedger.status(app)" in field, "P0: الاختبار الميداني لا يقرأ سجل الأعطال")
req("منع الفشل الصامت" in settings, "P0: المستخدم لا يملك بوابة ظاهرة لاختبار منع الفشل الصامت")
req("verify_fault_prevention.py" in workflow, "P0: بوابة منع الفشل الصامت غير موصولة بـCI")
print("HAKIM_FAULT_PREVENTION=PASS")
