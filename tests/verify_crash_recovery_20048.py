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
req("DEFERRED_CRASH_RECOVERY" in evo, "Evolution يجب أن يتوقف أثناء نافذة crash recovery")

m=re.search(r"versionCode\s+(\d+)",build)
req(m and int(m.group(1))==20048,"versionCode يجب أن يكون ٢٠٠٤٨")
req(policy["current_candidate_version"]==20048,"سياسة التوقيع يجب أن تسجل ٢٠٠٤٨ كمرشح")
req(policy["current_field_version"]==20040,"لا يجوز تغيير خط الميدان المباشر دون دليل جديد")

print("HAKIM_20048_SECURE_ONLY_NO_LEGACY_WEBVIEW=PASS")
print("HAKIM_20048_CRASH_RECOVERY_DEFERRAL=PASS")
print("HAKIM_20048_CRASH_OBSERVABILITY=PASS")
print("HAKIM_20048_FALSE_PAIRING_GATE_FIXED=PASS")
