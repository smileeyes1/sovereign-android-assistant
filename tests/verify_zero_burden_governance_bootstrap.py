from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


gov = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
home = text("app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt")
pairing = text("app/src/main/java/ps/hakim/phoneagent/HakimLocalPairing.kt")

# لا يجوز أن يبدأ حكيم بلا نظام حاكم.
require("DEFAULT_GLOBAL_INSTRUCTIONS" in gov, "P0: لا توجد نواة حاكمة افتراضية")
require("ifBlank { DEFAULT_GLOBAL_INSTRUCTIONS }" in gov, "P0: التثبيت النظيف قد يعيد تعليمات فارغة")
require("[نظام المستخدم الحاكم المحلي]" in gov, "P0: النواة الافتراضية غير موصولة بسياق الاستدلال")
require("و؟ → و؟ → و؟ → لِمَ؟ → و؟ → و؟ → اعتمد → أصلح → أكمل → هَيّا" in gov,
        "P0: مسار حكيم الحاكم المطلوب مفقود")
for phrase in [
    "افترض صفر خبرة تقنية",
    "السكوت ليس موافقة",
    "فشل أداة لا يعني فشل الغاية",
    "PREVENT → PLAN → EXECUTE → VERIFY → RECOVER → LEARN → FREEZE",
    "المختبَر يجب أن يساوي المسلَّم",
]:
    require(phrase in gov, f"P0: النواة الافتراضية فقدت قاعدة حاكمة: {phrase}")

# واجهة النظام يجب أن تعرض النواة وتسمح باستعادتها بدل خانة فارغة.
require("HakimGovernanceStore.global(this)" in settings, "P0: واجهة النظام لا تحمل النواة الفعلية")
require("استعادة نواة حكيم الأعلى" in settings, "P0: زر استعادة النواة الافتراضية مفقود")
require("HakimGovernanceStore.DEFAULT_GLOBAL_INSTRUCTIONS" in settings, "P0: الاستعادة لا تستخدم المصدر الحاكم نفسه")
require("مفعّل افتراضيًا" in settings, "P0: الواجهة لا توضح أن النظام الحاكم موجود افتراضيًا")

# اقتران ADB: الرمز بوابة أندرويد لمرة واحدة، لا سؤال مبهم ولا قفز تلقائي للإعدادات.
require("تأسيس الاتصال المحلي — مرة واحدة" in home, "P0: زر الاتصال لا يشرح أنه تأسيس لمرة واحدة")
require("أندرويد يفرض رمز اقتران" in home, "P0: لا يوجد شرح صريح لمصدر الرمز ولماذا يلزم")
require("showLocalAdbSetupGuide" in home, "P0: مسار التأسيس الموجّه مفقود")
require("افتح شاشة الاقتران" in home, "P0: المستخدم لا يملك خطوة واضحة للوصول إلى رمز أندرويد")
require("حكيم مقترن أصلًا؛ أعيد الاتصال بدل طلب رمز جديد" in home,
        "P0: حكيم قد يطلب رمزًا جديدًا رغم وجود اقتران سابق")
require("adbStatus.postDelayed({ showLocalAdbSetupGuide(firstRun = true) }" in home,
        "P0: التشغيل الأول لا يعرض دليلاً قبل التأسيس")
require("ensureNotificationPermissionThenSetup()" not in home.split("private fun maybeBootstrapLocalAdb()", 1)[1].split("private fun startGuidedLocalAdbSetup()", 1)[0],
        "P0: التشغيل الأول ما زال يقفز تلقائيًا إلى إعدادات الاقتران")

require("رمز أندرويد لمرة واحدة" in pairing, "P0: إشعار الاقتران لا يشرح أن الرمز من أندرويد ولمرة واحدة")
require("سيعيد حكيم الاتصال تلقائيًا دون طلب الرمز عادةً" in pairing,
        "P0: واجهة الاقتران لا تشرح الاستمرارية بعد النجاح")
require("local_adb_paired" in pairing and "reconnectAsync" in pairing,
        "P0: الاقتران لا يحفظ الحالة أو لا يملك إعادة اتصال تلقائية")

print("ZERO_BURDEN_GOVERNANCE_BOOTSTRAP=PASS")
