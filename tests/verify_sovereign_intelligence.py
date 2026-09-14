from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


build = text("app/build.gradle")
manifest = text("app/src/main/AndroidManifest.xml")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
decision = text("app/src/main/java/ps/hakim/phoneagent/HakimDecisionMatrix.kt")
religious = text("app/src/main/java/ps/hakim/phoneagent/HakimReligiousIntegrity.kt")
mission = text("app/src/main/java/ps/hakim/phoneagent/HakimMissionLedger.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
autonomous = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
protocol = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProtocol.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
secure = text("app/src/main/java/ps/hakim/phoneagent/HakimSecureStore.kt")

require("applicationId 'ps.hakim.stable'" in build, "P0: الذكاء السيادي خرج من تطبيق حكيم الواحد")
require("org.hakim.omega.companion" not in "\n".join([manifest, agents, sovereign, app]), "P0: عاد اعتماد التطبيق الموازي")

for mode in ["AUTO", "AUTO_VERIFY", "RESEARCH_FIRST", "APPROVAL_GATE", "BLOCK"]:
    require(mode in decision, f"P0: نمط مصفوفة القرار {mode} مفقود")
for dim in ["benefit", "evidence", "reversibility", "authority", "privacy", "safety", "clarity", "costFit", "burdenReduction", "freshness"]:
    require(dim in decision, f"P0: بُعد القرار {dim} مفقود")
require("القيود الحاكمة بوابات لا أوزان تعويضية" in decision, "P0: المصفوفة قد تعوض خطرًا حاكمًا بنقاط منفعة")
require("HakimDecisionMatrix.evaluate" in sovereign, "P0: المحرك السيادي لا يقيّم مصفوفة القرار")
require("HakimDecisionMatrix.promptContext" in sovereign, "P0: المحرك السيادي لا يمرر تفسير المصفوفة")
require("HakimSovereignEngine.promptContext" in agents, "P0: الوكيل القائد لا يرث قرار المحرك السيادي")

require("القرآن الكريم والسنة الصحيحة" in religious, "P0: مرجعية النزاهة الشرعية غير مثبتة")
require("ميّز صراحة بين" in religious and "التفسير" in religious and "الاجتهاد" in religious, "P0: فصل النص الشرعي عن التفسير/الاجتهاد مفقود")
require("الخلاف المعتبر" in religious, "P0: احترام الخلاف الفقهي المعتبر مفقود")
require("لا تنقل آية أو حديثًا" in religious, "P0: بوابة التثبت من النص الشرعي مفقودة")
require("الحروف المقطعة" in religious and "قوى تقنية" in religious, "P0: حاجز عدم تحويل القرآن إلى خوارزميات/قوى تقنية مفقود")
require("RELIGIOUS" in agents, "P0: وكيل النزاهة الشرعية غير موجود في منظومة القائد")
require("HakimReligiousIntegrity.assess" in sovereign and "HakimReligiousIntegrity.promptContext" in sovereign, "P0: المحرك السيادي لا يستدعي النزاهة الشرعية")
require("HakimSovereignEngine.assess" in agents, "P0: القائد لا يرث تقييم النزاهة الشرعية من المحرك السيادي")

# حاكم الهدي النبوي: توقير + تحقق + اتباع + بركة مشروعة بلا ادعاء تقني.
require("[حاكم الهدي النبوي]" in religious, "P0: حاكم الهدي النبوي مفقود")
require("سيدنا محمد" in religious and "ﷺ" in religious, "P0: توقير سيدنا محمد ﷺ غير مثبت")
require("السنة والسيرة والشمائل والخصائص والهدي" in religious, "P0: مجالات الهدي النبوي غير مغطاة")
require("الصحيح والحسن والضعيف والموضوع" in religious, "P0: تمييز درجات الرواية النبوية مفقود")
require("آل البيت والصحابة وأمهات المؤمنين" in religious, "P0: أدب آل البيت والصحابة وأمهات المؤمنين غير مثبت")
require("الإيمان به، محبته، اتباع سنته، الصلاة والسلام عليه" in religious, "P0: معنى البركة النبوية المشروع غير مثبت")
require("لا تُحوّل إلى ادعاء قوة خفية أو ضمان نتيجة دنيوية أو تأثير تقني" in religious, "P0: حاجز الغلو التقني في مفهوم البركة مفقود")
require("بسم الله الرحمن الرحيم" in religious, "P0: البسملة في السياق الديني المناسب غير مثبتة")
require("propheticRegex" in religious and "propheticExactRegex" in religious, "P0: اكتشاف المهام النبوية أو بوابة التحقق الدقيق مفقود")

for phase in ["UNDERSTAND", "PLAN", "EXECUTE", "VERIFY", "RECOVER", "WAITING_APPROVAL", "WAITING_CREDENTIAL", "WAITING_TRUST", "COMPLETE", "BLOCKED"]:
    require(phase in mission, f"P0: مرحلة المهمة {phase} مفقودة")
require("wip_limit" in mission and "1" in mission, "P0: WIP=1 غير مثبت")
require("HakimSecureStore" in mission, "P0: غاية المهمة ليست محفوظة في المخزن المشفر")
require("sanitizeGoal" in mission and "سري محذوف" in mission, "P0: سجل المهمة قد يخزن أسرارًا")
require("restoreActiveMissionState" in app and "Phase.RECOVER" in app, "P0: المهمة لا تستعاد بعد إعادة تشغيل التطبيق")

require("MAX_CONSECUTIVE_FAILURES = 3" in sovereign, "P0: لا توجد عتبة لإعادة التخطيط بعد الفشل")
require("HARD_FAILURE_LIMIT = 5" in sovereign, "P0: لا يوجد حد يمنع الدوران بعد الفشل المتكرر")
require("WIP=1" in sovereign and "HakimMissionLedger" in sovereign, "P0: المحرك السيادي غير مربوط بسجل المهمة")
require("RELIGIOUS" in agents and "RESILIENCE" in agents, "P0: وكلاء النزاهة/الاستمرارية غير موجودين")
require("HakimSovereignEngine.assess" in agents and "HakimSovereignEngine.promptContext" in agents, "P0: المحرك السيادي غير داخل خطة/موجه القائد")

require("MAX_STEPS" in autonomous and "fingerprint" in autonomous, "P0: التنفيذ الذاتي بلا حد أو منع دوران")
require("HakimMissionLedger" in autonomous and "HakimSovereignEngine" in autonomous, "P0: الحلقة المحلية لا تسجل التقدم في المهمة السيادية")
require("cycle < 2" in chat, "P0: دورات الاستدلال بلا حد مانع للدوران")
require("arr.length() > 8" in protocol, "P0: خطة الاستدلال بلا حد أفعال")
for forbidden in ["shell", "exec", "javascript", "tap_xy", "adb"]:
    require(forbidden not in protocol.lower(), f"P0: بروتوكول الاستدلال يسمح بتنفيذ واسع: {forbidden}")
require("containsSecret" in protocol, "P0: بروتوكول الاستدلال لا يحجب الأسرار")
require("AndroidKeyStore" in secure and "AES/GCM/NoPadding" in secure, "P0: التخزين المحلي المحمي مفقود")

print("HAKIM_SOVEREIGN_INTELLIGENCE=PASS")
