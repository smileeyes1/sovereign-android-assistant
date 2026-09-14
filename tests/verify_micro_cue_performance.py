from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


micro = text("app/src/main/java/ps/hakim/phoneagent/HakimMicroCueEngine.kt")
intent = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentContext.kt")
authority = text("app/src/main/java/ps/hakim/phoneagent/HakimAuthorityEnvelope.kt")
workflow = text(".github/workflows/android.yml")

for kind in ["SILENT", "SYMBOL", "SINGLE_GLYPH", "SHORT_CUE", "EXPLICIT"]:
    require(kind in micro, f"P0: نوع إشارة مفقود: {kind}")
require("symbolOnly" in micro, "P0: الرمز لا يملك مصنفًا مستقلًا")
require("codePointCount" in micro, "P0: الحرف/الرمز Unicode لا يُقاس بصورة صحيحة")
require("contextRequired" in micro and "context_required_for_ambiguous_micro_cues" in micro,
        "P0: الإشارة الدقيقة قد تعمل بلا سياق موثوق")
require("high_impact_never_authorized_by_micro_cue" in micro,
        "P0: الإشارة الدقيقة قد تتحول إلى موافقة عالية الأثر")

require("HakimMicroCueEngine.classify(raw)" in intent,
        "P0: فهم المقصد لا يستهلك محرك الإشارة الدقيقة")
require("fastPathEligible" in intent and "mayFastContinue" in intent,
        "P0: لا يوجد مسار سريع للإشارة السياقية")
require("micro_cue_without_context" in intent and 'confidence = "LOW"' in intent,
        "P0: الرمز/الحرف بلا سياق قد يُرفع بثقة زائفة")
require("MICRO_CACHE_MS = 300L" in intent,
        "P0: لا توجد ذاكرة لحظية قصيرة لتقليل إعادة قراءة الشاشة")
require("cachedInference" in intent and "now - cachedAt in 0..MICRO_CACHE_MS" in intent,
        "P0: ذاكرة الاستنتاج اللحظية غير مستخدمة فعليًا")
require(intent.count("val screen = screenSummary()") == 1,
        "P0: الاستنتاج يقرأ الشاشة أكثر من مرة داخل الدورة")
require("screenContext = screen" in intent and "lastUrlContext = lastUrl.take(500)" in intent,
        "P0: سياق الاستنتاج لا يعاد استخدامه في العرض")

require("no_authority_expansion_from_generic_cues" in authority and "silence_is_not_consent" in authority,
        "P0: أقل إشارة قد توسع السلطة")
require("verify_micro_cue_performance.py" in workflow,
        "P0: لا توجد بوابة CI للإشارة الدقيقة والأداء")

print("HAKIM_MICRO_CUE_PERFORMANCE=PASS")
