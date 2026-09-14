from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


sos = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
workflow = text(".github/workflows/android.yml")

require("object HakimSystemOfSystems" in sos, "P0: نظام الأنظمة مفقود")
require("DerivedSystem" in sos and "compose(context" in sos, "P0: توليد النظام المنبثق غير منفذ")
for unit in ["GOVERNANCE", "QURAN_SUNNAH", "HUMAN_FIRST", "INTENT", "RESOURCE", "AUTHORITY", "VERIFICATION", "RECOVERY", "LEARNING"]:
    require(unit in sos, f"P0: نظام حاكم أساسي مفقود من نظام الأنظمة: {unit}")
require("ephemeral_derived_system" in sos, "P0: الأنظمة المنبثقة قد تتحول إلى خدمات دائمة")
require("cannot_expand_authority" in sos, "P0: النظام المنبثق قد يوسع السلطة")
require("cannot_mutate_code" in sos, "P0: النظام المنبثق قد يعدل الكود ذاتيًا")
require("HakimResourceGovernor.snapshot" in sos, "P0: نظام الأنظمة غير واعٍ بموارد الهاتف")
require("SEQUENTIAL_WIP1_MINIMAL_BACKGROUND" in sos, "P0: ضغط الموارد لا يفرض مسارًا خفيفًا متسلسلًا")

for phrase in [
    "و؟ الواقع",
    "و؟ المقصد",
    "و؟ القيود",
    "لِمَ؟",
    "و؟ البدائل",
    "و؟ الدليل",
    "اعتمد",
    "أصلح",
    "أكمل",
    "هَيّا",
]:
    require(phrase in sos, f"P0: بروتوكول حكيم ناقص في نظام الأنظمة: {phrase}")

require("HakimSystemOfSystems.compose(context, goal)" in sovereign, "P0: المحرك السيادي لا يكوّن نظام المهمة")
require("HakimSystemOfSystems.promptContext(context, goal)" in sovereign, "P0: نظام الأنظمة لا يدخل سياق القرار")
require('put("system_of_systems", HakimSystemOfSystems.status(context))' in sovereign, "P0: حالة نظام الأنظمة غير ظاهرة")
require("و؟→و؟→و؟→لِمَ؟→و؟→و؟→اعتمد→أصلح→أكمل→هَيّا" in sovereign,
        "P0: سلسلة السيادة لا تستخدم بروتوكول و؟ الحاكم")

require('"system_of_systems"' in fabric and "system_of_systems_integrated" in fabric,
        "P0: نظام الأنظمة غير مدمج في نسيج التكامل")
for edge in [
    "quran_sunnah_method→system_of_systems",
    "human_first_policy→system_of_systems",
    "resource_governor→system_of_systems",
    "intent_context→system_of_systems",
    "authority_envelope→system_of_systems",
    "system_of_systems→sovereign_engine",
    "system_of_systems→agent_system",
]:
    require(edge in fabric, f"P0: وصلة نظام الأنظمة مفقودة: {edge}")

require("اعمل بنظام الأنظمة افتراضيًا" in governance, "P0: نواة المستخدم لا تفعل نظام الأنظمة افتراضيًا")
require("مسار حكيم الحاكم المستمر لكل نظام وكل نظام منبثق" in governance,
        "P0: بروتوكول و؟ غير مطبق على الأنظمة المنبثقة")
require("verify_system_of_systems.py" in workflow, "P0: لا توجد بوابة CI لنظام الأنظمة")

print("HAKIM_SYSTEM_OF_SYSTEMS=PASS")
