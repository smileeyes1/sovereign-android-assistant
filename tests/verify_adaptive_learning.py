from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


adaptive = text("app/src/main/java/ps/hakim/phoneagent/HakimAdaptiveLearning.kt")
learning = text("app/src/main/java/ps/hakim/phoneagent/HakimLearning.kt")
evolution = text("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
workflow = text(".github/workflows/android.yml")

# التكيف يجب أن يكون محليًا ومحكومًا ولا يوسع السلطة أو يعدل الكود تلقائيًا.
require("object HakimAdaptiveLearning" in adaptive, "P0: محرك التعلم التكيفي مفقود")
require("local_only" in adaptive and "changes_code_automatically" in adaptive and "can_expand_authority" in adaptive,
        "P0: حدود التعلم التكيفي غير معلنة")
require('put("changes_code_automatically", false)' in adaptive, "P0: التكيف قد يدعي تعديل الكود تلقائيًا")
require('put("can_expand_authority", false)' in adaptive, "P0: التكيف قد يوسع السلطة")
require("rankSafeCandidates" in adaptive and "baseline" in adaptive,
        "P0: التكيف لا يقتصر على إعادة ترتيب بدائل خط الأساس")
require("baseline_fallback" in adaptive and "ROLLED_BACK_TO_BASELINE" in adaptive,
        "P0: الرجوع إلى خط الأساس عند الانحدار مفقود")
require("REGRESSION_MARGIN" in adaptive and "COOLDOWN_MS" in adaptive,
        "P0: لا توجد بوابة انحدار/فترة تهدئة للتكيف")
require("Beta(1,1)" in adaptive and "MIN_ACTION_TRIALS" in adaptive,
        "P0: التكيف يقفز من عينة واحدة إلى ثقة زائفة")
require("HakimQuranicInvariantKernel.requireInherited" in adaptive,
        "P0: التعلم التكيفي خارج الجذر الحاكم")

# التنفيذ يتعلم من أثر الشاشة الفعلي، لا من نجاح استدعاء click فقط.
require("pendingAdaptiveFingerprint" in autonomous and "currentFingerprint != before" in autonomous,
        "P0: نتيجة الخطوة لا تقاس بتغير الحالة الفعلية")
require("HakimAdaptiveLearning.recordActionOutcome" in autonomous,
        "P0: التنفيذ المحلي لا يغذي التعلم بنتائج الخطوات")
require("HakimAdaptiveLearning.rankSafeCandidates" in autonomous,
        "P0: الخبرة لا تؤثر في ترتيب الخطوات الآمنة اللاحقة")
require("safeContinuationCandidates" in autonomous,
        "P0: مجموعة المرشحات الآمنة ليست منفصلة عن إعادة ترتيب التعلم")
require("HakimAuthorityEnvelope.classifyUiAction" in autonomous and "HakimDecisionMatrix.evaluate" in autonomous,
        "P0: التعلم تجاوز غلاف السلطة أو مصفوفة القرار")

# نتيجة المهمة والمسار الفعلي يغذيان التعلم مرة واحدة، والفشل العابر لا يُعامل كهزيمة نهائية.
require("HakimAdaptiveLearning.noteMissionRoute" in sovereign,
        "P0: المسار الفعلي للمهمة لا يدخل سجل التكيف")
require("failed.failures >= HARD_FAILURE_LIMIT" in sovereign and
        "HakimAdaptiveLearning.recordMissionOutcome(context, failed.id, false)" in sovereign,
        "P0: فشل عابر قد يخفض السياسة كأنه فشل نهائي")
require("HakimAdaptiveLearning.recordMissionOutcome(context, missionId, true)" in sovereign,
        "P0: إكمال المهمة لا يثبت النجاح في التعلم")
require("last_outcome_mission_id" in adaptive,
        "P0: نتيجة المهمة قد تُحسب أكثر من مرة")

# دورة التطور تثبت أو تتراجع وفق فحص الصحة.
require("consolidateAdaptation" in learning,
        "P0: طبقة التعلم لا تعرض تثبيت التكيف")
require("HakimLearning.consolidateAdaptation" in evolution and "report.optString(\"status\"" in evolution,
        "P0: دورة التطور لا تربط التكيف بصحة النظام")
require("DISABLED_BY_HEALTH" in adaptive,
        "P0: فشل الصحة لا يعطل أثر التكيف")

# الواجهة الذكية: كلام اختياري + رد صوتي اختياري + حالة مرئية، دون تسجيل ذاتي خفي.
require("RecognizerIntent.ACTION_RECOGNIZE_SPEECH" in chat and "startVoiceInput" in chat,
        "P0: التحدث مع حكيم غير منفذ")
require("EXTRA_PREFER_OFFLINE" in chat,
        "P0: الإدخال الصوتي لا يفضل المعالجة دون اتصال")
require("TextToSpeech" in chat and "voiceRepliesEnabled" in chat,
        "P0: الرد الصوتي الاختياري غير منفذ")
require('getBoolean("voice_replies", false)' in chat,
        "P0: الرد الصوتي ليس متوقفًا افتراضيًا")
require("setStatus(" in chat and "أتعلم من الأثر" in chat,
        "P0: الواجهة لا تظهر حالة الفهم/التنفيذ/التعلم")
require("startActivityForResult(intent, speechRequestCode)" in chat,
        "P0: الميكروفون قد يبدأ دون فعل صريح من المستخدم")

# التكيف جزء من الصحة والتكامل لا طبقة جانبية.
require("adaptive_learning_integrated" in fabric and '"adaptive_learning"' in fabric,
        "P0: التعلم التكيفي غير مدمج في نسيج التكامل")
require("التعلم التكيفي مدمج في نسيج التكامل" in selfcheck,
        "P0: الفحص الذاتي لا يحرس التكامل التكيفي")
require("التكيف لا يوسع السلطة" in selfcheck and "الرجوع إلى خط الأساس متاح" in selfcheck,
        "P0: الفحص الذاتي لا يحرس حدود التكيف أو rollback")

require("verify_adaptive_learning.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة للتعلم والتكيف والواجهة الصوتية")

print("HAKIM_ADAPTIVE_LEARNING_AND_VOICE=PASS")
