from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def req(cond, msg):
    if not cond:
        raise SystemExit(msg)

field = text("app/src/main/java/ps/hakim/phoneagent/HakimFieldValidation.kt")
boundary = text("app/src/main/java/ps/hakim/phoneagent/HakimHumanCapabilityBoundary.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
workflow = text(".github/workflows/android.yml")

req("object HakimFieldValidation" in field, "P0: منظومة الاختبار الميداني مفقودة")
for level in ["PASS", "PARTIAL", "BLOCKED"]:
    req(level in field, f"P0: مستوى الاختبار الميداني مفقود: {level}")
for check in ["هوية التطبيق", "هوية التوقيع الميداني", "الفحص الذاتي الحاكم", "نسيج التكامل", "سياسة القرآن كله", "نص القرآن المحلي المتحقق", "التخزين المشفر", "نظام الأنظمة", "حاكم موارد الهاتف", "التعلم التكيفي", "منع الفشل الصامت"]:
    req(check in field, f"P0: الاختبار الميداني لا يفحص: {check}")
req('put("field_verified", false)' in field, "P0: الفحص الذاتي قد يدعي FIELD_VERIFIED بلا سيناريوهات فعلية")
req("field_verified_requires_real_scenarios" in field, "P0: متطلب السيناريوهات الفعلية غير مثبت")
req("تشغيل الاختبار الميداني الشامل" in settings and "HakimFieldValidation.run" in settings, "P0: الاختبار الميداني غير متاح للمستخدم")

req("object HakimHumanCapabilityBoundary" in boundary, "P0: حد القدرة الإنسانية مفقود")
req('put("human_equivalence_claimed", false)' in boundary, "P0: حكيم قد يدعي التكافؤ الكامل مع الإنسان")
req("maximal_digital_assistance_goal" in boundary, "P0: هدف أقصى مساعدة رقمية غير مثبت")
req("physical_world_dependency_acknowledged" in boundary, "P0: الاعتماد على العالم المادي مخفي")
req("system_permissions_cannot_be_bypassed" in boundary, "P0: قد يدعي حكيم تجاوز صلاحيات أندرويد")
req("missing_secrets_cannot_be_invented" in boundary, "P0: قد يدعي حكيم اختلاق الأسرار المفقودة")
req("prepare_to_last_gate" in boundary, "P0: حكيم لا يحضّر تلقائيًا حتى آخر بوابة")
req("verify_field_validation_and_human_boundary.py" in workflow, "P0: بوابة الميدان/الحد الإنساني غير موصولة بـCI")
print("HAKIM_FIELD_VALIDATION_AND_HUMAN_BOUNDARY=PASS")
