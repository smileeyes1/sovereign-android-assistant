from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


human = text("app/src/main/java/ps/hakim/phoneagent/HakimHumanFirstPolicy.kt")
authority = text("app/src/main/java/ps/hakim/phoneagent/HakimAuthorityEnvelope.kt")
intent = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentContext.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
workflow = text(".github/workflows/android.yml")

require("HUMAN-FIRST" in human, "P0: سياسة الإنسان أولًا مفقودة")
for token in [
    "dignity_is_hard_constraint",
    "zero_technical_burden_default",
    "charitable_intent_default",
    "kindness_must_not_be_exploited",
    "silence_is_not_consent",
    "generic_cue_is_not_high_impact_consent",
    "human_error_and_fatigue_tolerant",
    "mercy_with_justice",
    "preserve_user_agency",
    "adaptive_explanation_depth",
    "heart_reform_supported_without_claiming_inner_state",
    "vulnerable_and_weak_receive_extra_protection",
    "protect_lawful_good_without_claiming_hidden_righteousness",
    "support_muslim_good_with_universal_justice",
    "quiet_internal_support_auto_when_safe",
    "external_support_never_expands_authority",
    "fallback_required_after_tool_failure",
    "extraordinary_good_without_magic_claims",
    "observable_success_required",
]:
    require(token in human, f"P0: مبدأ إنساني حاكم مفقود: {token}")

require("لا تشترط عليه خبرة تقنية" in human, "P0: صفر العبء التقني غير صريح")
require("طيبة المستخدم ورحمته" in human and "لا تُستغل" in human,
        "P0: حماية الطيبة والرحمة من الاستغلال غير صريحة")
require("السكوت" in human and "ليس موافقة" in human,
        "P0: السكوت قد يفسر موافقة")
require("إصلاح القلب" in human and "لا تدّع معرفة باطن الإنسان" in human,
        "P0: إصلاح القلب غير منضبط أو يدعي معرفة الباطن")
require("المستضعف والضعيف" in human and "دون تمييز ظالم" in human,
        "P0: حماية المستضعفين غير متوازنة بالعدل")
require("المؤمنين والمسلمين" in human and "كل إنسان بريء" in human,
        "P0: دعم المسلمين غير مقيد بالعدل الشامل")
require("الدعم الصامت" in human and "لا إخفاء أفعال مؤثرة" in human,
        "P0: الدعم الصامت قد يتحول إلى إخفاء أو مراقبة غير مصرح بها")
require("انتقل إلى بديل مشروع وآمن ومتاح" in human,
        "P0: فشل الوسيلة قد يوقف الغاية بلا بديل")
require("لا تسمِّ نجاحًا تقنيًا «معجزة»" in human,
        "P0: احتمال ادعاء المعجزة كآلية تقنية")
require("لا تتوقف قبل النجاح القابل للإثبات" in human and "لا تدّع اكتمالها آليًا" in human,
        "P0: النجاح غير مربوط بالإثبات وحدود المقاصد الباطنة")
require("لا تفترض العجز" in human and "لا تتحدث بتعالٍ" in human,
        "P0: السياسة قد تنقلب إلى وصم/تعالٍ")

require("HakimHumanFirstPolicy.promptContext" in authority,
        "P0: غلاف السلطة لا يستهلك سياسة الإنسان أولًا")
require("silence_is_not_consent" in authority and "kindness_is_not_consent" in authority,
        "P0: السلطة لا تحرس السكوت/الطيبة من التحول إلى موافقة")
require("HakimHumanFirstPolicy.promptContext" in intent,
        "P0: فهم المقصد لا يستهلك سياسة الإنسان أولًا")
require("لا تتطلب من المستخدم معرفة تقنية" in intent,
        "P0: محرك المقصد قد يعيد العبء التقني للمستخدم")
require("HakimHumanFirstPolicy.promptContext" in sovereign and 'put("human_first"' in sovereign,
        "P0: المحرك السيادي لا يحمل سياسة الإنسان أولًا")
require("human_first_policy" in fabric and "human_first_integrated" in fabric,
        "P0: الإنسان أولًا غير موصول بنسيج التكامل")
require("الإنسان أولًا مدمج في نسيج التكامل" in selfcheck,
        "P0: الفحص الذاتي لا يحرس الإنسان أولًا")

require("verify_human_first_policy.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة لسياسة الإنسان أولًا")

print("HAKIM_HUMAN_FIRST_POLICY=PASS")
