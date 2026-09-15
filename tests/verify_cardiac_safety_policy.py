from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimCardiacSafetyPolicy.kt")
systems = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt")
workflow = text(".github/workflows/android.yml")

require("CARDIAC-SAFETY" in policy, "P0: حارس القلب الجسدي مفقود")
for token in [
    "cardiac_physical_vs_human_heart_separated",
    "wireless_health_read_only_by_default",
    "trusted_paired_sources_only",
    "signal_quality_and_timestamp_required",
    "single_reading_is_not_diagnosis",
    "absence_of_alert_is_not_clearance",
    "no_implant_or_life_support_control",
    "no_remote_shock_stimulation_ablation",
    "no_autonomous_medication_change",
    "no_auth_bypass_or_signal_spoofing",
    "data_minimization_local_first",
    "revocable_consent_required",
    "adaptive_analysis_but_no_treatment_actuation",
    "urgent_escalation_over_long_automation",
]:
    require(token in policy, f"P0: ضابط قلبي مفقود: {token}")

require("القراءة/المزامنة المأذونة والتنبيه فقط" in policy,
        "P0: الاتصال اللاسلكي ليس مقيدًا بالقراءة والتنبيه")
require("ممنوع على حكيم" in policy and "منظم قلب" in policy and "مزيل رجفان" in policy,
        "P0: منع التحكم بالأجهزة القلبية المزروعة غير صريح")
require("لا تشخّص مرضًا قلبيًا من قراءة واحدة" in policy,
        "P0: خطر التشخيص من قراءة مفردة غير محروس")
require("لا اقتران صامت" in policy and "لا تجاوز مصادقة" in policy,
        "P0: حدود الاقتران والمصادقة اللاسلكية غير صريحة")
require("CARDIAC_SAFETY" in systems and "HakimCardiacSafetyPolicy.promptContext" in systems,
        "P0: حارس القلب غير موصول بنظام الأنظمة")
require("derived_systems_inherit_cardiac_safety_when_relevant" in systems,
        "P0: الأنظمة المنبثقة لا ترث حارس القلب عند الصلة")
for cue in ["قلب", "نبض", "ecg", "spo2", "منظم قلب", "مزيل رجفان"]:
    require(cue in systems.lower(), f"P0: محفز قلبي مفقود: {cue}")
require("verify_cardiac_safety_policy.py" in workflow,
        "P0: لا توجد بوابة CI مستقلة لحارس القلب الجسدي")

print("HAKIM_CARDIAC_SAFETY_POLICY=PASS")
