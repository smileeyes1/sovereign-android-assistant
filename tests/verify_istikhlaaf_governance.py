from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

ist = text("app/src/main/java/ps/hakim/phoneagent/HakimIstikhlaafFramework.kt")
prophets = text("app/src/main/java/ps/hakim/phoneagent/HakimProphetsGuidancePolicy.kt")
framework = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicFramework.kt")
method = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranSunnahMethod.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")

for token in [
    "العبودية والاستعانة بالله", "طلب الهداية المستمرة", "الاستخلاف ابتلاء",
    "الحكم بالحق ومقاومة الهوى", "عمارة الأرض والإصلاح", "منع الفساد",
    "أداء الأمانات والحكم بالعدل", "الحكمة خير كثير", "البصيرة", "التدبر", "طلب الرشد",
]:
    require(token in ist, f"P0: مبدأ استخلاف قرآني مفقود: {token}")

require('put("stewardship_is_responsibility_and_test", true)' in ist, "P0: الاستخلاف لا يُحكم كمسؤولية وابتلاء")
require('put("intelligence_is_tool_not_supreme_value", true)' in ist, "P0: الذكاء قد يصبح قيمة مطلقة")
require('put("no_divine_mandate_for_personal_rule", true)' in ist, "P0: غاب منع ادعاء التفويض الإلهي الشخصي")
require('put("no_coercion_or_rights_bypass_from_stewardship_claim", true)' in ist, "P0: غاب منع تجاوز الحقوق باسم الاستخلاف")
require("لا تستعمل مفهوم الاستخلاف لتبرير إكراه الناس" in ist, "P0: حاجز الإكراه والحقوق مفقود")
require("HakimIstikhlaafFramework.promptContext(raw)" in framework, "P0: إطار الاستخلاف لا يصل للإطار القرآني")
require('put("istikhlaaf_framework", HakimIstikhlaafFramework.status())' in framework, "P0: حالة الاستخلاف غير ظاهرة")

require('put("all_prophets_and_messengers_revered", true)' in prophets, "P0: شمول الإيمان والتوقير للرسل غير مثبت")
require('put("specific_prophetic_claim_requires_verification", true)' in prophets, "P0: التثبت من النسبة للأنبياء مفقود")
require('put("israiliyyat_not_treated_as_revelation", true)' in prophets, "P0: الفصل عن الإسرائيليات مفقود")
require("محمد ﷺ خاتم النبيين" in prophets, "P0: ختم النبوة غير مثبت في سياسة الأنبياء")
require("HakimProphetsGuidancePolicy.promptContext(raw)" in method, "P0: هدي الأنبياء لا يصل للمنهج")
require('put("all_prophets_guidance_policy", HakimProphetsGuidancePolicy.status())' in method, "P0: حالة هدي الأنبياء غير ظاهرة")

require("الاستخلاف مسؤولية وابتلاء وإصلاح وعمارة" in constitution, "P0: الاستخلاف غير مدمج في الدستور التنفيذي")
require("الذكاء والقوة أدوات" in constitution, "P0: الحكمة لا تقيد الذكاء والقوة في الثوابت")
require('put("istikhlaaf_framework", HakimIstikhlaafFramework.status())' in constitution, "P0: حالة الاستخلاف غير مدمجة في الدستور")
require('put("prophets_guidance", HakimProphetsGuidancePolicy.status())' in constitution, "P0: هدي الأنبياء غير مدمج في الدستور")

print("ISTIKHLAAF_GOVERNANCE=PASS")
