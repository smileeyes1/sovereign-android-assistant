from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimResourceSovereigntyPolicy.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignIndependence.kt")
workflow = text(".github/workflows/android.yml")

require("RESOURCE-SOVEREIGNTY" in policy, "P0: سياسة سيادة الموارد مفقودة")
for token in [
    "lawful_free_or_owned_first_when_equivalent",
    "zero_cost_is_preference_not_rights_override",
    "no_free_energy_from_nothing_claim",
    "no_meter_payment_or_lock_bypass",
    "energy_demand_reduction_first",
    "renewable_and_storage_when_owned_or_authorized",
    "qualified_electrical_work_for_hazardous_installations",
    "water_conservation_leak_detection_storage",
    "rainwater_harvesting_only_when_lawful_and_safe",
    "potable_water_requires_treatment_and_quality_verification",
    "nonpotable_and_potable_separation_required",
    "energy_aware_scheduling",
    "authorized_known_networks_only",
    "no_wifi_auth_or_paywall_bypass",
    "offline_first_when_capable",
    "cache_queue_resume",
    "metered_network_cost_aware",
    "replaceable_service_providers",
    "critical_services_require_fallback_when_practical",
    "local_reasoning_when_sufficient",
    "external_reasoning_on_proven_gain",
    "portable_state_and_open_formats_preferred",
    "near_realtime_when_available_no_zero_latency_claim",
    "adaptive_flexible_mobile_continuous",
]:
    require(token in policy, f"P0: مبدأ سيادة موارد مفقود: {token}")

require("ممنوع سرقة الكهرباء" in policy, "P0: منع سرقة/تجاوز الكهرباء غير صريح")
require("الطاقة الشمسية/المتجددة" in policy and "التخزين" in policy,
        "P0: بدائل الطاقة المحلية المشروعة غير صريحة")
require("مياه الأمطار" in policy and "فحوص جودة" in policy,
        "P0: استقلال الماء مع تحقق الجودة غير صريح")
require("لا تخلط مياهًا غير صالحة للشرب" in policy,
        "P0: فصل المياه الصالحة وغير الصالحة للشرب غير صريح")
require("لا تخترق Wi-Fi" in policy, "P0: منع اختراق الشبكة غير صريح")
require("لا تحايل على اشتراك" in policy, "P0: منع التحايل على الخدمات غير صريح")
require("لا تعد بزمن صفري" in policy, "P0: الآنية قد تُفهم كضمان غير واقعي")
require("HakimResourceSovereigntyPolicy.promptContext" in sovereign,
        "P0: سياسة الموارد غير موصولة بالاستقلال السيادي")
require('put("resource_sovereignty"' in sovereign,
        "P0: حالة سيادة الموارد غير مكشوفة في الاستقلال السيادي")
require("verify_resource_sovereignty_policy.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة لسيادة الموارد")

print("HAKIM_RESOURCE_SOVEREIGNTY_POLICY=PASS")
