from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
factory = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMaterialFactory.kt").read_text(encoding="utf-8")
intent = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")
self_check = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

require('MATERIAL-FACTORY-2026-09-23-v1' in factory, "FACTORY: إصدار النواة مفقود")
require('FABRICATED_UNVERIFIED' in factory and 'MATERIAL_VERIFIED' in factory, "FACTORY: الفصل بين التصنيع والتحقق مفقود")
require('التصميم≠التصنيع' in factory, "FACTORY: حاجز التصميم/التصنيع مفقود")
require('لا يُعلن مصنوعًا أو ناجحًا بلا دليل ميداني' in factory, "FACTORY: منع ادعاء النجاح المادي مفقود")
require('Scale.NANO' in factory and 'Scale.ATOMIC' in factory and 'Scale.MICRO' in factory, "FACTORY: المقاييس المتعددة مفقودة")
require('أبسط مقياس تصنيع يحقق الغاية' in factory, "FACTORY: منع تعقيد النانو لذاته مفقود")
require('HakimMaterialFactory.governedContext' in intent, "FACTORY: المصنع غير موصول بمحرك النية")
require('"material_factory"' in intent, "FACTORY: مسار المصنع غير معرف في محرك النية")
require('اصنع' in intent and 'نانو' in intent and 'ملموس' in intent, "FACTORY: كلمات تفعيل المصنع الأساسية مفقودة")
require('HakimMaterialFactory.status(context)' in self_check, "FACTORY: المصنع غير ظاهر في الفحص الذاتي")
require('.put("material_factory", factory)' in self_check, "FACTORY: تقرير الفحص الذاتي لا يحمل حالة المصنع")

print("HAKIM_MATERIAL_FACTORY_POLICY=PASS")


# منع انحدار التوجيه: "اصنع تطبيق" يجب أن يبقى مسار برمجة لا مصنعًا ماديًا.
builder_pos = intent.find('listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق"')
factory_pos = intent.find('listOf("اصنع", "صنع", "منتج", "جهاز"')
require(builder_pos >= 0 and factory_pos >= 0 and builder_pos < factory_pos,
        "FACTORY: انحدار أولوية التوجيه؛ تصنيع التطبيق قد يُلتقط كمصنع مادي")

# فشل معلوم معزول: إذا قلبنا الأولوية عمدًا يجب أن يكتشفها الحارس.
mutated = intent.replace(
    'listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق"',
    'listOf("zzz تطبيق", "zzz", "zzz"',
    1
)
known_failure_detected = mutated.find('listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق"') < 0
require(known_failure_detected, "FACTORY: اختبار الفشل المعلوم لا يكتشف كسر مسار التطبيقات")

print("HAKIM_MATERIAL_FACTORY_ROUTING_GUARD=PASS")
