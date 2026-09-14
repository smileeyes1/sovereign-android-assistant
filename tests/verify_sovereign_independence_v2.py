from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


independence = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignIndependence.kt")
portability = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignPortability.kt")
providers = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProviderRegistry.kt")
settings = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemSettingsActivity.kt")
governance = text("app/src/main/java/ps/hakim/phoneagent/HakimGovernanceStore.kt")
site_trust = text("app/src/main/java/ps/hakim/phoneagent/HakimSiteTrust.kt")
leadership = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfLeadershipController.kt")
registry = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityRegistry.kt")
workflow = text(".github/workflows/android.yml")

require("object HakimSovereignIndependence" in independence, "P0: طبقة الاستقلال السيادي الشامل مفقودة")
for domain in ["IDENTITY", "GOVERNANCE", "DATA", "REASONING", "EXECUTION", "NETWORK", "UPDATE", "RECOVERY", "RESOURCES", "PORTABILITY"]:
    require(domain in independence, f"P0: مجال الاستقلال مفقود: {domain}")
require("single_external_point_of_failure_forbidden" in independence, "P0: لا يوجد منع صريح لنقطة فشل خارجية واحدة")
require("offline_graceful_degradation" in independence, "P0: لا توجد سياسة تدهور آمن دون شبكة")
require("android_platform_dependency_acknowledged" in independence, "P0: الاستقلال يدعي ضمنيًا التخلص من اعتماد Android")
require("advanced_model_equivalence_offline_not_claimed" in independence, "P0: قد يدعي حكيم تكافؤ نموذج متقدم بلا مزود")
require("HakimReasoningProviderRegistry.promptContext" in independence, "P0: استقلال المزود غير داخل عقد الاستقلال")
require('readyNow = provider.optBoolean("advanced_reasoning_ready_now")' in independence,
        "P0: جاهزية الاستدلال المتقدم لا تتبع حالته الفعلية")
require('advanced_reasoning_ready_now") || true' not in independence,
        "P0: الاستدلال المتقدم يُعلن جاهزًا دائمًا دون دليل")
require("توقيع ميداني مثبت" not in independence,
        "P0: طبقة الاستقلال تدعي إثباتًا ميدانيًا لا يثبته الكود")
require('readyNow = resource.optBoolean("resource_governor")' in independence,
        "P0: جاهزية حاكم الموارد لا تتبع حالته الفعلية")
require('readyNow = portability.optBoolean("sovereign_portability")' in independence,
        "P0: جاهزية قابلية النقل لا تتبع حالتها الفعلية")

require("object HakimReasoningProviderRegistry" in providers, "P0: سجل مزودات الاستدلال مفقود")
require("LOCAL_DETERMINISTIC" in providers and "EXTERNAL_ADVANCED" in providers, "P0: لا يوجد فصل بين المحلي والاستدلال المتقدم")
require("local_deterministic" in providers, "P0: المسار المحلي ليس مزودًا من الدرجة الأولى")
require("advanced_model_equivalence_offline_not_claimed" in providers, "P0: حدود الاستدلال المحلي غير مصرح بها")
require("single_external_provider_is_not_governor" in providers, "P0: مزود خارجي قد يتحول إلى حاكم")
require("user_selected_web" in providers, "P0: لا توجد فتحة مزود يختاره المستخدم")

require("object HakimSovereignPortability" in portability, "P0: قابلية النقل السيادي مفقودة")
require("contains_secrets" in portability and "contains_signing_private_key" in portability, "P0: النسخة السيادية لا تثبت استبعاد الأسرار/مفتاح التوقيع")
require("confirmed: Boolean" in portability and "تأكيدًا صريحًا" in portability, "P0: الاستعادة لا تتطلب تأكيدًا صريحًا")
require("HakimPersonalVault.all" in portability and "HakimGovernanceStore.allSites" in portability, "P0: النسخة لا تغطي الحالة المحلية المفيدة")
require("HakimSiteTrust.replaceTrustedHosts" in portability, "P0: ثقة المواقع غير قابلة للاستعادة")
require("replaceSites" in governance and "allSites" in governance, "P0: تعليمات المواقع غير قابلة للنقل")
require("trustedHosts" in site_trust and "replaceTrustedHosts" in site_trust, "P0: ثقة المواقع غير قابلة للنقل")

require("تصدير نسخة سيادية" in settings and "استعادة نسخة سيادية" in settings, "P0: المستخدم لا يملك واجهة نقل بياناته")
require("ACTION_CREATE_DOCUMENT" in settings and "ACTION_OPEN_DOCUMENT" in settings, "P0: النقل لا يستخدم منتقي Android الذي يختاره المستخدم")
require("HakimSovereignPortability.exportJson" in settings and "HakimSovereignPortability.importJson" in settings, "P0: واجهة الاستقلال غير موصولة فعليًا")

require("حقق الاستقلال السيادي بأعلى قدر واقعي" in governance, "P0: النواة الافتراضية لا تحمل مبدأ الاستقلال")
require("لا تدّع تكافؤ ذكاء متقدم" in governance, "P0: النواة قد تبالغ في الاستقلال عن المزود")
require("HakimSovereignIndependence.promptContext" in leadership, "P0: القيادة الذاتية لا تمر عبر الاستقلال")
require("coreSovereign" in leadership, "P0: القيادة لا تتوقف عند فشل الاستقلال البنيوي")
require("sovereign_independence" in registry and "sovereign_portability" in registry and "reasoning_provider_registry" in registry,
        "P0: سجل القدرات لا يعرف طبقات الاستقلال الجديدة")
require("verify_sovereign_independence_v2.py" in workflow, "P0: لا توجد بوابة CI للاستقلال السيادي الشامل")

print("HAKIM_SOVEREIGN_INDEPENDENCE_V2=PASS")
