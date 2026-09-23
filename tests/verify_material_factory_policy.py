from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
factory = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMaterialFactory.kt").read_text(encoding="utf-8")
intent = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")

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

print("HAKIM_MATERIAL_FACTORY_POLICY=PASS")
