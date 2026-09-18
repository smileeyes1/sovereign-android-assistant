from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
def t(p): return (ROOT/p).read_text(encoding="utf-8")
def req(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

app=t("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
doctor=t("app/src/main/java/ps/hakim/phoneagent/HakimConstraintDoctor.kt")
crash=t("app/src/main/java/ps/hakim/phoneagent/HakimCrashShield.kt")
health=t("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
watch=t("app/src/main/java/ps/hakim/phoneagent/HakimConnectionRecoveryJobService.kt")
resilience=t("app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt")
resources=t("app/src/main/java/ps/hakim/phoneagent/HakimResourceGovernor.kt")
evo=t("app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt")
build=t("app/build.gradle")
policy=json.loads(t("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

req("startLegacyBrowserIfPaired" in app, "يجب فصل تشغيل خدمة المتصفح القديمة عن القناة الآمنة")
req("if (disabled || !legacyPaired) return" in app, "الخدمة القديمة يجب ألا تبدأ بلا اقتران legacy كامل")
req("if (!safeRecovery) startLegacyBrowserIfPaired(prefs)" in app, "بعد crash يجب منع تشغيل WebView الثقيلة تلقائيًا")
req("if (HakimCrashShield.shouldSuppressProactiveResume(this)) return" in app, "الصيانة المؤجلة يجب أن تتوقف أثناء التعافي")

req("val securePaired = HakimUnifiedRelay.isConfigured(app)" in doctor, "ConstraintDoctor يجب أن يعترف بالاقتران الآمن")
req("val paired = legacyPaired || securePaired" in doctor, "الاقتران الآمن يجب أن يمنع إنذار الاقتران الكاذب")
req('if (legacyPaired && network && !HakimService.running)' in doctor, "حالة الخدمة القديمة يجب أن تعتمد على legacy فقط")
req('SECURE_RELAY_RECOVERING' in doctor, "يجب تمثيل تعافي القناة الآمنة مستقلًا")

for token in ["last_seen_exit_timestamp","last_crash_or_anr_at","last_recorded_failure_label","last_recorded_failure_type"]:
    req(token in crash, f"تشخيص crash مفقود: {token}")

for token in ['"crash_shield"','"resources"','"local_reasoning"']:
    req(token in health, f"نبضة الصحة لا تحمل {token}")

req("HakimCrashShield.shouldSuppressProactiveResume(app)" in watch, "watchdog يجب أن يخفف العمل بعد crash")
req("HakimResourceGovernor.canRunNonEssentialBackground(app)" in watch, "watchdog يجب أن يحترم ضغط الموارد")
req("periodic_watchdog_safe_recovery" in watch, "نبضة watchdog يجب أن تميز وضع التعافي الآمن")
req('p.getString("auth_key", "").orEmpty().isNotBlank()' in resilience, "تعريف legacy يجب أن يتطلب مفتاح المصادقة لا حقولًا قديمة فقط")
req("!legacyPaired || HakimCrashShield.shouldSuppressProactiveResume(app)" in resilience, "التعافي الآمن يجب أن يمنع تشغيل خدمة WebView القديمة")
req("if (securePaired) HakimUnifiedRelay.start(app)" in resilience, "HC1 يجب أن يبقى قابلًا للتعافي دون WebView")
req("val safeRecovery = HakimCrashShield.shouldSuppressProactiveResume(app)" in resources, "حاكم الموارد يجب أن يرى نافذة crash")
req("safeRecovery || mem.lowMemory" in resources, "نافذة crash يجب أن تفرض PRESSURE")
req('"safe_recovery_active"' in resources, "حالة الموارد يجب أن تكشف وضع التعافي")
req("DEFERRED_CRASH_RECOVERY" in evo, "Evolution يجب أن يتوقف أثناء نافذة crash recovery")
req(resilience.count('p.getString("auth_key", "").orEmpty().isNotBlank()') >= 2,
    "تعريف legacy في recover/status يجب أن يتطلب auth_key")
req("if (!legacyPaired || HakimCrashShield.shouldSuppressProactiveResume(app))" in resilience,
    "التعافي يجب ألا يعيد تشغيل legacy WebView أثناء safe-recovery")
req("safeRecovery || mem.lowMemory" in resources,
    "نافذة crash recovery يجب أن تُعامل كضغط موارد")
req('.put("safe_recovery_active", HakimCrashShield.shouldSuppressProactiveResume(context))' in resources,
    "حاكم الموارد يجب أن يكشف safe-recovery للتشخيص")
req('periodic_watchdog_safe_recovery' in watch,
    "نبضة watchdog يجب أن تميز safe-recovery صراحة")

m=re.search(r"versionCode\s+(\d+)",build)
req(m and int(m.group(1))==20048,"versionCode يجب أن يكون ٢٠٠٤٨")
req(policy["current_candidate_version"]==20048,"سياسة التوقيع يجب أن تسجل ٢٠٠٤٨ كمرشح")
req(policy["current_field_version"]==20040,"لا يجوز تغيير خط الميدان المباشر دون دليل جديد")

print("HAKIM_20048_SECURE_ONLY_NO_LEGACY_WEBVIEW=PASS")
print("HAKIM_20048_CRASH_RECOVERY_DEFERRAL=PASS")
print("HAKIM_20048_CRASH_OBSERVABILITY=PASS")
print("HAKIM_20048_FALSE_PAIRING_GATE_FIXED=PASS")
print("HAKIM_20048_RESILIENCE_SAFE_RECOVERY=PASS")
print("HAKIM_20048_RESOURCE_PRESSURE_GUARD=PASS")
