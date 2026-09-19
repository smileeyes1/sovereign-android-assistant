from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


rules = text("app/src/main/java/ps/hakim/phoneagent/HakimRuleLedger.kt")
adaptive = text("app/src/main/java/ps/hakim/phoneagent/HakimAdaptiveLearningPortability.kt")
portability = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignPortability.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
workflow = text(".github/workflows/android.yml")

require("fun portableRules" in rules and "fun replacePortableRules" in rules,
        "P0: سجل القواعد لا يملك تصديرًا/استعادة قابلة للنقل")
require("AtomicFile" in rules and "startWrite" in rules and "failWrite" in rules,
        "P0: استعادة القواعد ليست ذرية عند فشل الكتابة")
require("HakimGovernanceStore.exportSafeText" in rules,
        "P0: القواعد القابلة للنقل لا تمر بمنقح الأسرار")
require('setOf("rule", "correction", "preference")' in rules,
        "P0: سجل النقل قد يخرج نصوص مهام عادية")
require("AndroidKeyStore" in rules and "hakim_rule_ledger_v1" in rules,
        "P0: حماية سجل القواعد المحلي تغيرت دون قصد")

require("object HakimAdaptiveLearningPortability" in adaptive,
        "P0: دليل التعلم لا يملك طبقة نقل مستقلة")
require("anonymous_action_evidence" in adaptive and "recent_missions" in adaptive,
        "P0: النسخة لا تحفظ دليل نجاح التعلم الفعلي")
require("contains_pending_mission" in adaptive and 'put("contains_pending_mission", false)' in adaptive,
        "P0: نقل التعلم قد يحمل مهمة جارية")
require("pending_mission_id" in adaptive and '.remove("pending_mission_id")' in adaptive,
        "P0: الاستعادة قد تعيد مهمة قديمة بدل دليل التعلم فقط")
require("contains_credentials" in adaptive and 'put("contains_credentials", false)' in adaptive,
        "P0: عقد دليل التعلم لا يمنع الاعتمادات")
require("MAX_ACTIONS" in adaptive and "MAX_RECENT_MISSIONS" in adaptive,
        "P0: بيانات التعلم المحمولة بلا حدود حجم")

require("SCHEMA_VERSION = 2" in portability and "MIN_SUPPORTED_SCHEMA_VERSION = 1" in portability,
        "P0: مخطط الاستمرارية لا يميز النسخة الجديدة أو كسر النسخ القديمة")
require('put("effective_rules", effectiveRules)' in portability,
        "P0: النسخة السيادية لا تحتوي القواعد الفعالة")
require('put("adaptive_learning_evidence", adaptiveEvidence)' in portability,
        "P0: النسخة السيادية لا تحتوي دليل التعلم")
require('put("contains_pending_mission", false)' in portability,
        "P0: النسخة لا تعلن استبعاد المهمة الجارية")
require("HakimRuleLedger.replacePortableRules" in portability,
        "P0: استعادة القواعد غير موصولة بالنسخة السيادية")
require("HakimAdaptiveLearningPortability.replaceEvidence" in portability,
        "P0: استعادة دليل التعلم غير موصولة بالنسخة السيادية")
require("effectiveRules = HakimRuleLedger.portableRules(context)" in portability,
        "P0: لقطة التراجع لا تحفظ القواعد قبل الاستيراد")
require("adaptiveEvidence = HakimAdaptiveLearningPortability.exportEvidence(context)" in portability,
        "P0: لقطة التراجع لا تحفظ دليل التعلم قبل الاستيراد")
require("v1 يبقى مدعومًا" in portability and "portableRules: JSONArray?" in portability,
        "P0: استيراد النسخة القديمة قد يمسح حالة جديدة لم تكن تعرفها")
require("signing_private_key_excluded" in portability and "secrets_excluded" in portability,
        "P0: توسيع الاستمرارية أضعف استبعاد الأسرار أو مفتاح التوقيع")

require("تصدير نسخة سيادية" in settings and "استعادة نسخة سيادية" in settings,
        "P0: قابلية النقل غير متاحة للمستخدم من واجهة النظام")
require("verify_state_continuity.py" in workflow,
        "P0: لا توجد بوابة CI تمنع انحدار استمرارية الحالة")

print("HAKIM_STATE_CONTINUITY=PASS")
