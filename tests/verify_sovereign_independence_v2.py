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
require('"contains_personal_data"' in portability and "export_may_contain_personal_data" in portability,
        "P0: النسخة لا تصرّح بوجود البيانات الشخصية الممكنة منفصلًا عن الأسرار")
require("قد تحتوي بيانات شخصية" in portability,
        "P0: وصف النسخة يوحي بأنها خالية من البيانات الشخصية")
require("confirmed: Boolean" in portability and "تأكيدًا صريحًا" in portability, "P0: الاستعادة لا تتطلب تأكيدًا صريحًا")
require("HakimPersonalVault.all" in portability and "HakimGovernanceStore.allSites" in portability, "P0: النسخة لا تغطي الحالة المحلية المفيدة")
require("HakimSiteTrust.replaceTrustedHosts" in portability, "P0: ثقة المواقع غير قابلة للاستعادة")
require("replaceSites" in governance and "allSites" in governance, "P0: تعليمات المواقع غير قابلة للنقل")
require("trustedHosts" in site_trust and "replaceTrustedHosts" in site_trust, "P0: ثقة المواقع غير قابلة للنقل")
require("fun exportSafeText" in governance, "P0: لا يوجد مسار تنقيح آمن للنص الخارج من النظام الحاكم")
require("HakimGovernanceStore.exportSafeText(instructions)" in portability,
        "P0: تعليمات المواقع تُصدّر دون تنقيح الأسرار المضمّنة")
require("HakimGovernanceStore.exportSafeText(HakimGovernanceStore.global(context))" in portability,
        "P0: النظام الحاكم العام يُصدّر دون تنقيح الأسرار المضمّنة")
require('sites.put(host, instructions.take' not in portability,
        "P0: بقي مسار خام لتصدير تعليمات المواقع")
require('.put("governance_global", HakimGovernanceStore.global(context)' not in portability,
        "P0: بقي مسار خام لتصدير النظام الحاكم العام")
require('root.optString("governance_global", "")\n        ).take' not in portability,
        "P0: بقي مسار خام لاستيراد النظام الحاكم العام دون تنقيح")
require('sites[host] = sitesObj.optString(host, "").take' not in portability,
        "P0: بقي مسار خام لاستيراد تعليمات المواقع دون تنقيح")
require(portability.count("HakimGovernanceStore.exportSafeText(") >= 4,
        "P0: التنقيح يجب أن يغطي التصدير والاستيراد للنظام العام وتعليمات المواقع")

# الاستعادة يجب أن تكون قابلة للتراجع وألا تترك نصف حالة جديدة.
require("data class LocalSnapshot" in portability and "val before = snapshot(context)" in portability,
        "P0: الاستعادة لا تلتقط الحالة الموثوقة قبل أول كتابة")
require("restoreSnapshot(context, before)" in portability,
        "P0: فشل الاستعادة لا يعيد الحالة السابقة")
require("restore_rollback_guard" in portability,
        "P0: حالة قابلية النقل لا تعلن حاجز التراجع")
require("replaceSitesSafely" in portability and "restoreSitesBestEffort" in portability,
        "P0: تعليمات المواقع ما زالت عرضة لمسح جزئي عند فشل الكتابة")
require("replaceProfileSafely" in portability and "restoreProfileBestEffort" in portability,
        "P0: خزنة البيانات ما زالت عرضة لاستعادة جزئية")
require("profile_restore_replaces_not_merges" in portability,
        "P0: استعادة الخزنة قد تترك حقولًا قديمة غير موجودة في النسخة")
require("HakimGovernanceStore.replaceSites(context, sites)" not in portability,
        "P0: الاستيراد عاد إلى مسار يمسح المواقع القديمة قبل إثبات كتابة الجديدة")
require("if (!HakimPersonalVault.save(context, id, value))" in portability,
        "P0: فشل كتابة حقل مشفر في الخزنة لا يوقف الاستعادة ويستدعي التراجع")
require("أُعيدت الحالة السابقة كاملة" in portability and "تعذر إثبات استعادة الحالة السابقة كاملة" in portability,
        "P0: نتيجة الفشل لا تميز بين تراجع مثبت وتراجع غير مثبت")

require("تصدير نسخة سيادية" in settings and "استعادة نسخة سيادية" in settings, "P0: المستخدم لا يملك واجهة نقل بياناته")
require("ACTION_CREATE_DOCUMENT" in settings and "ACTION_OPEN_DOCUMENT" in settings, "P0: النقل لا يستخدم منتقي Android الذي يختاره المستخدم")
require("HakimSovereignPortability.exportJson" in settings and "HakimSovereignPortability.importJson" in settings, "P0: واجهة الاستقلال غير موصولة فعليًا")
require('openOutputStream(uri, "w")' in settings,
        "P0: تصدير النسخة السيادية لا يستخدم وضع الكتابة المدعوم من ContentResolver")
require('openOutputStream(uri, "wt")' not in settings,
        "P0: عاد وضع wt غير المدعوم وقد يفشل حفظ النسخة السيادية")
require("قد تتضمن بياناتك الشخصية المحفوظة" in settings,
        "P0: واجهة التصدير لا تنبّه المستخدم أن النسخة قد تتضمن بيانات شخصية")
require("بيانات التعبئة الشخصية المحفوظة" in settings,
        "P0: حوار الاستعادة لا يصف البيانات الشخصية بوضوح")
require("بيانات شخصية متكررة للتعبئة المحلية" in settings,
        "P0: قسم الخزنة لا يعرّف البيانات كبيانات شخصية")
require("السماح لمحرك الاستدلال برؤية بيانات التعبئة الشخصية عند الحاجة" in settings,
        "P0: خيار مشاركة البيانات مع الاستدلال يقلل من وصف حساسيتها")
require("هذه البيانات غير الحساسة" not in settings and "بيانات التعبئة غير الحساسة" not in settings,
        "P0: واجهة حكيم ما زالت تصف الاسم/الهاتف/العنوان بأنها غير حساسة")

require("حقق الاستقلال السيادي بأعلى قدر واقعي" in governance, "P0: النواة الافتراضية لا تحمل مبدأ الاستقلال")
require("لا تدّع تكافؤ ذكاء متقدم" in governance, "P0: النواة قد تبالغ في الاستقلال عن المزود")
require("HakimSovereignIndependence.promptContext" in leadership, "P0: القيادة الذاتية لا تمر عبر الاستقلال")
require("coreSovereign" in leadership, "P0: القيادة لا تتوقف عند فشل الاستقلال البنيوي")
require("sovereign_independence" in registry and "sovereign_portability" in registry and "reasoning_provider_registry" in registry,
        "P0: سجل القدرات لا يعرف طبقات الاستقلال الجديدة")
require("verify_sovereign_independence_v2.py" in workflow, "P0: لا توجد بوابة CI للاستقلال السيادي الشامل")

print("HAKIM_SOVEREIGN_INDEPENDENCE_V2=PASS")
