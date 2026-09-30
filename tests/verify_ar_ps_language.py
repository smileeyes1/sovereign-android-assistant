from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimArabicPolicy.kt")
profile = text("app/src/main/java/ps/hakim/phoneagent/HakimPalestinianArabicProfile.kt")
gate = text("app/src/main/java/ps/hakim/phoneagent/HakimArabicOutputGate.kt")
intent = text("app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt")
self_check = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
manifest = text("app/src/main/AndroidManifest.xml")
locales = text("app/src/main/res/xml/locales_config.xml")
regional_strings = text("app/src/main/res/values-ar-rPS/strings.xml")

require("ARABIC-FIRST-RTL-AR-PS-2026-09-30-v2" in policy, "نسخة سياسة العربية الفلسطينية غير مثبتة")
require('LOCALE_TAG = "ar-PS"' in profile, "المحلية ar-PS مفقودة")
require('COUNTRY_CONTEXT = "PS"' in profile, "سياق فلسطين PS مفقود")
require("العربية الفصحى الطبيعية" in profile, "الأساس اللغوي العربي الطبيعي مفقود")
require("no_unverified_local_claims" in profile, "حارس عدم اختلاق التفاصيل المحلية مفقود")
require("HakimPalestinianArabicProfile.promptContract(raw)" in intent, "الهوية الفلسطينية لا تصل إلى كل موجّه محكوم")
require("AR-PS-OUTPUT-GATE-2026-09-30-v1" in gate, "بوابة قبول العربية مفقودة")
require("NO_COMMON_ENGLISH_UI_LEAK" in gate, "فحص تسرب الإنجليزية مفقود")
require("EASTERN_DIGITS_ONLY" in gate, "فحص أرقام الصفوف الأولى مفقود")
require("NO_EQUALS_AT_LINE_START" in gate, "فحص بداية المعادلة بعلامة = مفقود")
require("HTML_LANG_AR_PS" in gate and "HTML_DIR_RTL" in gate, "فحص HTML العربي الفلسطيني ناقص")
require('android:localeConfig="@xml/locales_config"' in manifest, "إعداد لغات التطبيق غير مربوط")
require('android:name="ar-PS"' in locales, "ar-PS غير معلنة كلغة تطبيق")
require("hakim_accessibility_description" in regional_strings, "موارد ar-PS الإقليمية مفقودة")
require("palestinian_context_default" in policy, "السياق الفلسطيني ليس افتراضيًا")
require("output_gate_required" in policy, "بوابة القبول ليست شرطًا في السياسة")
require('<html lang=\\\"ar-PS\\\" dir=\\\"rtl\\\">' in policy, "جذر HTML ليس ar-PS/RTL")
require('arabic.optString("locale") == HakimPalestinianArabicProfile.LOCALE_TAG' in self_check, "الفحص الذاتي لا يتحقق من ar-PS")

print("AR_PS_LANGUAGE_GOVERNANCE=PASS")
