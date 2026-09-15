from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

cycle = text("app/src/main/java/ps/hakim/phoneagent/HakimTaqwaCultivationCycle.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
readiness = text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalReadiness.kt")
standard = text("governance/HAKIM_ELITE_PROFESSIONAL_STANDARD.md")

for stage in [
    "TAQWA", "INTENT", "SEED", "CULTIVATE", "PRODUCE", "TEST",
    "QUALIFY", "PUBLISH", "STEWARD", "ACCOUNT", "GRATITUDE", "LEARN"
]:
    require(stage in cycle, f"P0: مرحلة دورة التقوى مفقودة: {stage}")

for token in [
    "wealth_is_means_not_ultimate_value", "family_is_trust_not_property",
    "publish_after_test_and_qualification", "human_authority_bounded",
    "no_lost_scripture_fabrication", "religious_claim_requires_source",
    "worldly_means_require_causes_and_testing"
]:
    require(token in cycle, f"P0: قيد دورة التقوى مفقود: {token}")

for phrase in [
    "المال والأهل والبنون نعم وأمانات ووسائل",
    "الزرع والرعاية والإنتاج والاختبار والتأهيل والنشر والمحاسبة دورة عمل",
    "التقوى→المقصد→الزرع→الرعاية بالحكمة→الإنتاج→الاختبار→التأهيل→النشر المسؤول"
]:
    require(phrase in constitution, f"P0: الدستور لا يثبت: {phrase}")

for phrase in [
    "صحف إبراهيم أو موسى",
    "لا تخترع نصًا مفقودًا",
    "إذا قيل «أمر الله» أو «مراد القرآن»"
]:
    require(phrase in religious, f"P0: النزاهة الشرعية لا تثبت: {phrase}")

require("HakimTaqwaCultivationCycle.promptContext(goal)" in sovereign,
        "P0: المحرك السيادي لا يرث دورة التقوى والزرع")
require('"taqwa_cultivation_cycle"' in sovereign,
        "P0: حالة المحرك السيادي لا تعرض دورة التقوى")
require('"taqwa_cultivation_cycle"' in readiness,
        "P0: الجاهزية المهنية لا تعتبر دورة التقوى بوابة")
require("TAQWA" in constitution and "v9" in constitution,
        "P0: الدستور لم يُرق إلى نسخة التقوى الحاكمة")


for phrase in [
    "## دورة التقوى والزرع والحصاد",
    "الحكم المطلق لله وحده",
    "لا تُنسب إرادة بشرية إلى «أمر الله» أو «مراد القرآن»",
    "صحف إبراهيم وموسى"
]:
    require(phrase in standard, f"P0: المعيار المهني لا يثبت: {phrase}")

print("TAQWA_CULTIVATION=PASS")
