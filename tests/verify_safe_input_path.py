from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)


build = text("app/build.gradle")
manifest = text("app/src/main/AndroidManifest.xml")
ime = text("app/src/main/java/ps/hakim/phoneagent/HakimImeResilience.kt")
input_safety = text("app/src/main/java/ps/hakim/phoneagent/HakimInputSafety.kt")
crash = text("app/src/main/java/ps/hakim/phoneagent/HakimCrashShield.kt")
proactive = text("app/src/main/java/ps/hakim/phoneagent/HakimProactiveEngine.kt")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20038, "P0: تثبيت مربع الكتابة يتطلب ٢٠٠٣٨ أو أحدث")
require('android:windowSoftInputMode="adjustResize"' in manifest,
        "P0: مسار التوافق adjustResize مفقود من نشاط المحادثة")

require("HakimInputSafety.install(app)" in ime,
        "P0: حارس الإدخال غير مربوط بمسار بدء IME")
require("WindowInsetsCompat.Type.ime()" in ime,
        "P0: لا تتم مراقبة ظهور IME")
require("WindowInsetsAnimationCompat.Callback" in ime,
        "P0: حركة مربع الكتابة غير متزامنة مع حركة لوحة المفاتيح")
require("calculateImeOverlap" in ime and "currentWindowMetrics.bounds.bottom" in ime,
        "P0: لا يوجد قياس فعلي لتداخل composer مع IME")
require("composer.translationY" in ime,
        "P0: composer لا يملك مسار رفع منخفض الكلفة فوق IME")
require("updateMessageListPadding" in ime,
        "P0: سجل الرسائل لا يحجز مساحة التداخل الفعلية")
require("systemBars()" in ime and "displayCutout()" in ime,
        "P0: حواف النظام/القص غير محمية")
require("val bottom = state.baseContentPadding[3] + bars.bottom" in ime,
        "P0: Padding الجذر يجب أن يتبع حواف النظام فقط")
require("baseContentPadding[3] + ime.bottom" not in ime,
        "P0: عاد ارتفاع لوحة المفاتيح الكامل إلى Padding الجذر")
require("double_lift_prevented_when_adjust_resize_already_works" in ime,
        "P0: لا يوجد عقد لمنع الرفع المزدوج عند نجاح adjustResize")

for token in [
    "RESUME_GRACE_MS",
    "TYPING_SUPPRESS_MS",
    "setOnFocusChangeListener",
    "typing_active",
    "reads_or_stores_user_text\" to false",
]:
    require(token in input_safety, f"P0: حارس الإدخال يفتقد {token}")

require("suppressProactiveResumeFor" in crash,
        "P0: حارس التعطل لا يدعم كبح الاستئناف أثناء التفاعل")
require("maxOf(currentUntil, requestedUntil)" in crash,
        "P0: مهلة الأمان الجديدة قد تقصر مهلة تعافٍ أطول")
require("if (HakimCrashShield.shouldSuppressProactiveResume(context)) return null" in proactive,
        "P0: المحرك الاستباقي لا يحترم حارس الكتابة/التعطل")

print("HAKIM_SAFE_INPUT_PATH=PASS")
print("HAKIM_TYPING_PREEMPTS_PROACTIVE_RESUME=PASS")
print("HAKIM_IME_DOCKED_COMPOSER=PASS")
print("HAKIM_NO_FULL_IME_ROOT_PADDING=PASS")
print("HAKIM_NO_DOUBLE_IME_LIFT=PASS")
