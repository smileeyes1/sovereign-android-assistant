from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

personal = text("app/src/main/java/ps/hakim/phoneagent/HakimPersonalSovereignty.kt")
secure = text("app/src/main/java/ps/hakim/phoneagent/HakimSecureStore.kt")
constitution = text("app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
command = text("app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt")
readiness = text("app/src/main/java/ps/hakim/phoneagent/HakimProfessionalReadiness.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
relay = text("app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
standard = text("governance/HAKIM_ELITE_PROFESSIONAL_STANDARD.md")
require("HakimSecureStore.put" in personal and "HakimSecureStore.get" in personal,
        "P0: عقد المستخدم لا يستخدم المخزن المشفر")
require("AndroidKeyStore" in secure and "AES/GCM/NoPadding" in secure,
        "P0: المخزن الحساس لا يعتمد AndroidKeyStore/AES-GCM")
require('external_export_default", false' in personal,
        "P0: ذاكرة المستخدم الحساسة قد تُصدّر خارجيًا افتراضيًا")
require("sensitive_traits_not_exported_by_default" in personal,
        "P0: حاجز عدم تصدير السمات الحساسة مفقود")
require("soul_or_brain_literal_copy_claim" in personal and "false" in personal,
        "P0: النظام قد يدعي نسخ الروح/الدماغ حرفيًا")
require("EXPLICIT_CURRENT" in personal and "CORRECTION" in personal and "INFERENCE" in personal,
        "P0: مصادر اليقين في المقصد غير مصنفة")
require("highImpact && certainty != Certainty.CERTAIN" in personal,
        "P0: أثر مرتفع قد يعتمد على استنتاج غير يقيني")
require("captureExplicitDeclaration" in personal,
        "P0: لا يوجد التقاط محلي للتصريح الشخصي الصريح")
require("captureExplicitDeclaration" in chat and "captureExplicitDeclaration" in command,
        "P0: واجهات حكيم لا تحفظ التصريح الشخصي الصريح")
require("intent" not in personal.split("val publicKeys = listOf(", 1)[1].split(")", 1)[0],
        "P0: النص الشخصي الكامل قد يخرج إلى المزود الخارجي")
require("faith_commitments" not in personal.split("val publicKeys = listOf(", 1)[1].split(")", 1)[0],
        "P0: الالتزام الديني الشخصي قد يخرج افتراضيًا إلى المزود الخارجي")
require("التصريح الحالي/التصحيح > القاعدة الصريحة المحفوظة > السياق > الاستنتاج" in agents,
        "P0: هرم فهم المستخدم غير مثبت في الوكيل القائد")
for token in ["personal_sovereignty_default", "explicit_intent_over_memory", "explicit_intent_over_inference", "personal_sensitive_memory_local_encrypted"]:
    require(token in constitution, f"P0: الدستور لا يثبت {token}")
for target, source in [("readiness", readiness), ("sovereign", sovereign), ("relay", relay)]:
    require("personal_sovereignty" in source, f"P0: {target} لا يكشف حالة السيادة الشخصية")
require("«الرحمن» اسم لله سبحانه" in religious,
        "P0: حارس اسم الرحمن غير مثبت")
require("رحيم/لطيف بالإنسان" in religious,
        "P0: الرحمة السلوكية غير مفصولة عن الاسم الإلهي")
for phrase in [
    "## السيادة الشخصية والذاكرة",
    "قصد المستخدم الصريح الحالي وتصحيحه يعلوان على الذاكرة السابقة",
    "لا تُصدّر إلى مزود خارجي افتراضيًا",
    "لا يدعي حفظ روح الإنسان أو دماغه حرفيًا",
    "معيار الرحمة في حكيم سلوكي"
]:
    require(phrase in standard, f"P0: معيار السيادة الشخصية ناقص: {phrase}")

print("PERSONAL_SOVEREIGNTY=PASS")
