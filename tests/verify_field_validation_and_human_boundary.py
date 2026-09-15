from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding="utf-8")

def req(cond, msg):
    if not cond:
        raise SystemExit(msg)

field = text("app/src/main/java/ps/hakim/phoneagent/HakimFieldValidation.kt")
boundary = text("app/src/main/java/ps/hakim/phoneagent/HakimHumanCapabilityBoundary.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
preflight = text("scripts/hakim-field-preflight.sh")
workflow = text(".github/workflows/android.yml")

req("object HakimFieldValidation" in field, "P0: منظومة الاختبار الميداني مفقودة")
for level in ["PASS", "PARTIAL", "BLOCKED"]:
    req(level in field, f"P0: مستوى الاختبار الميداني مفقود: {level}")
for check in ["هوية التطبيق", "هوية التوقيع الميداني", "الفحص الذاتي الحاكم", "نسيج التكامل", "سياسة القرآن كله", "نص القرآن المحلي المتحقق", "التخزين المشفر", "نظام الأنظمة", "حاكم موارد الهاتف", "التعلم التكيفي", "منع الفشل الصامت"]:
    req(check in field, f"P0: الاختبار الميداني لا يفحص: {check}")
req('put("field_verified", false)' in field, "P0: الفحص الذاتي قد يدعي FIELD_VERIFIED بلا سيناريوهات فعلية")
req("field_verified_requires_real_scenarios" in field, "P0: متطلب السيناريوهات الفعلية غير مثبت")
req("تشغيل الاختبار الميداني الشامل" in settings and "HakimFieldValidation.run" in settings, "P0: الاختبار الميداني غير متاح للمستخدم")

# الفحص التمهيدي الخارجي يجب أن يبقى قراءة فقط قبل أي قرار تحديث/هجرة.
for required in [
    'PKG="ps.hakim.stable"',
    'F4="F42D71B0308A543E253099C02301BFDBFEFA45F8755B12AB22A3C903305E442E"',
    'D1="D13E7AA8271CB6D32AEC2157CC5BA4FAFD226957EB0C731E9CEBA827BF78B0D3"',
    'get-state', 'shell pm path', 'shell dumpsys package', ' pull ',
    'verify --verbose --print-certs', 'F4_MATCH', 'D1_ALTERNATE_NOT_INPLACE_F4',
    'BLOCKED_SIGNER_UNREADABLE', 'BLOCKED_UNKNOWN_SIGNER',
]:
    req(required in preflight, f"P0: الفحص التمهيدي لا يثبت المكوّن المطلوب: {required}")

# اسمح فقط بسحب APK المثبت إلى مجلد مؤقت محلي وتنظيف ذلك المجلد؛ امنع أي كتابة للجهاز.
active = "\n".join(
    line for line in preflight.splitlines()
    if line.strip() and not line.lstrip().startswith("#")
)
for pattern in [
    r'\badb\b[^\n]*\binstall\b',
    r'\badb\b[^\n]*\buninstall\b',
    r'\badb\b[^\n]*\bpush\b',
    r'\bshell\s+pm\s+clear\b',
    r'\bshell\s+pm\s+(?:grant|revoke)\b',
    r'\bshell\s+settings\s+put\b',
    r'\bshell\s+appops\s+set\b',
    r'\bshell\s+rm\b',
    r'\bshell\s+reboot\b',
    r'\bdisable-user\b',
    r'\benable\s+[^\n]*ps\.hakim\.stable',
]:
    req(re.search(pattern, active, re.I) is None,
        f"P0: الفحص التمهيدي خرج من وضع القراءة فقط: {pattern}")
req('rm -rf "$TMP"' in preflight, "P0: الفحص التمهيدي لا ينظف ملفه المحلي المؤقت")
req('pull "$BASE_APK" "$LOCAL_APK"' in preflight, "P0: الفحص لا يسحب APK المثبت للتحقق المحلي")

req("object HakimHumanCapabilityBoundary" in boundary, "P0: حد القدرة الإنسانية مفقود")
req('put("human_equivalence_claimed", false)' in boundary, "P0: حكيم قد يدعي التكافؤ الكامل مع الإنسان")
req("maximal_digital_assistance_goal" in boundary, "P0: هدف أقصى مساعدة رقمية غير مثبت")
req("physical_world_dependency_acknowledged" in boundary, "P0: الاعتماد على العالم المادي مخفي")
req("system_permissions_cannot_be_bypassed" in boundary, "P0: قد يدعي حكيم تجاوز صلاحيات أندرويد")
req("missing_secrets_cannot_be_invented" in boundary, "P0: قد يدعي حكيم اختلاق الأسرار المفقودة")
req("prepare_to_last_gate" in boundary, "P0: حكيم لا يحضّر تلقائيًا حتى آخر بوابة")
req("verify_field_validation_and_human_boundary.py" in workflow, "P0: بوابة الميدان/الحد الإنساني غير موصولة بـCI")
req("bash -n scripts/hakim-field-preflight.sh" in workflow, "P0: سكربت الفحص التمهيدي غير مفحوص نحويًا في CI")
print("HAKIM_FIELD_VALIDATION_AND_HUMAN_BOUNDARY=PASS")
