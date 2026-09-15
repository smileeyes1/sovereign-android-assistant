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
require(m and int(m.group(1)) >= 20037, "P0: مسار الكتابة الآمن يتطلب ٢٠٠٣٧ أو أحدث")
require('android:windowSoftInputMode="adjustResize"' in manifest,
        "P0: adjustResize مفقود من نشاط المحادثة")

require("HakimInputSafety.install(app)" in ime,
        "P0: حارس الإدخال غير مربوط بمسار بدء IME")
require("WindowInsetsCompat.Type.ime()" in ime,
        "P0: لا تتم مراقبة ظهور IME")
require("ime.bottom" not in ime,
        "P0: ارتفاع لوحة المفاتيح الكامل عاد كـ padding للجذر")
require("systemBars()" in ime and "displayCutout()" in ime,
        "P0: حواف النظام/القص غير محمية")
require("if (view.paddingLeft != left" in ime,
        "P0: لا يوجد منع لإعادة setPadding بلا تغير فعلي")

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
print("HAKIM_NO_FULL_IME_ROOT_PADDING=PASS")
