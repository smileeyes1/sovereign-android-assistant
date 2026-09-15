from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

guard = text("app/src/main/java/ps/hakim/phoneagent/HakimHalalShubuhatGuard.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
deliberation = text("app/src/main/java/ps/hakim/phoneagent/HakimDeliberationQuality.kt")
readiness = text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalReadiness.kt")
relay = text("app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt")
standard = text("governance/HAKIM_ELITE_PROFESSIONAL_STANDARD.md")
for token in [
    "CLEAR_PERMITTED", "CLEAR_PROHIBITED", "DOUBTFUL", "UNKNOWN",
    "VERIFY_FIRST", "ABSTAIN", "BLOCK_AND_ALTERNATIVE",
    "no_automated_fatwa_from_keywords", "protective_margin_around_prohibitions"
]:
    require(token in guard, f"P0: حارس الشبهات ناقص: {token}")

require("sourceVerified" in guard and "!sourceVerified" in guard,
        "P0: الحلال/الحرام البيّن قد يمر بلا مصدر متحقق")
require("لا يجوز تحويل حكم غير متحقق إلى حلال/حرام بيّن" in guard,
        "P0: لا يوجد منع صريح لتحويل الظن إلى فتوى")
require("highImpact) Gate.ABSTAIN" in guard,
        "P0: الشبهة عالية الأثر لا تتوقف")
require("قدّم البديل المشروع" in guard,
        "P0: التحريم المتحقق لا يولد بديلًا مشروعًا")
require("حديث النعمان بن بشير" in religious and "رواه البخاري ومسلم" in religious,
        "P0: أصل الحديث المتفق عليه غير مثبت في النزاهة الشرعية")
require("لا ذريعة لوسوسة أو تحريم بلا دليل" in religious,
        "P0: الحديث قد يُستخدم للتشدد بلا دليل")
require("لا يدعي حكيم معرفة القلوب" in religious,
        "P0: صلاح القلب قد يتحول إلى ادعاء قراءة نيات")

for token in [
    "halal_shubuhat_guard_default",
    "no_automated_fatwa_from_keywords",
    "high_impact_shubuhat_abstain_until_verified"
]:
    require(token in constitution, f"P0: الدستور لا يثبت {token}")
require("HakimHalalShubuhatGuard.assessTask" in sovereign,
        "P0: المسار السيادي لا يفحص الشبهات")
require("research_then_replan" in sovereign and "Gate.ABSTAIN" in sovereign,
        "P0: الشبهة لا تفرض بحثًا/إعادة تخطيط")
require("مسألة حلال/حرام أو شبهة انتقلت إلى التنفيذ بلا دليل شرعي مسجل" in deliberation,
        "P0: المداولة قد تسمح بتنفيذ شبهة بلا دليل")
require("HakimHalalShubuhatGuard.promptContext" in deliberation,
        "P0: مزود الاستدلال لا يرث حارس الشبهات")
require("halal_shubuhat_guard" in readiness and "halal_shubuhat_guard" in relay,
        "P0: حالة حارس الشبهات غير ظاهرة في الجاهزية/HC1")

for phrase in [
    "## الحلال والحرام والشبهات وصلاح القلب",
    "لا يولّد حكيم حكمًا شرعيًا من الكلمات",
    "المشتبه أو غير المحسوم ينتقل إلى research/verify",
    "صلاح القلب معيار للمقصد والصدق والتواضع"
]:
    require(phrase in standard, f"P0: معيار الحديث ناقص: {phrase}")

print("HALAL_SHUBUHAT_GUARD=PASS")
