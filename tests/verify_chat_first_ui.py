from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


manifest = text("app/src/main/AndroidManifest.xml")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
ui = text("app/src/main/java/ps/hakim/phoneagent/HakimChatUi.kt")
ime = text("app/src/main/java/ps/hakim/phoneagent/HakimImeResilience.kt")
input_safety = text("app/src/main/java/ps/hakim/phoneagent/HakimInputSafety.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
crash = text("app/src/main/java/ps/hakim/phoneagent/HakimCrashShield.kt")
polish = text("app/src/main/java/ps/hakim/phoneagent/HakimUiPolish.kt")
agents = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt")
resource = text("app/src/main/java/ps/hakim/phoneagent/HakimResourceGovernor.kt")
build = text("app/build.gradle")
workflow = text(".github/workflows/android.yml")

version = re.search(r"versionCode\s+(\d+)", build)
require(version is not None and int(version.group(1)) >= 20038,
        "P0: إصلاح تثبيت مربع الكتابة ليس ضمن إصدار ٢٠٠٣٨ أو أحدث")
require("versionName '" in build, "P0: اسم إصدار حكيم مفقود")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب بقاء واجهة تشغيل واحدة")
launcher_block = manifest.split('android.intent.category.LAUNCHER')[0][-1200:]
require('android:name=".HakimAgentsChatActivity"' in launcher_block, "P0: التطبيق لا يفتح مباشرة على المحادثة")
require('android:windowSoftInputMode="adjustResize"' in launcher_block,
        "P0: مسار التوافق الرسمي للوحة المفاتيح adjustResize مفقود")
require("HakimImeResilience.install(this)" in app,
        "P0: حارس IME غير مفعّل عند بدء التطبيق")
require("HakimInputSafety.install(app)" in ime,
        "P0: حارس الكتابة غير مربوط بمسار IME الفعلي")
require("ViewCompat.setOnApplyWindowInsetsListener" in ime and "WindowInsetsCompat.Type.ime()" in ime,
        "P0: لا تتم مراقبة Insets لوحة المفاتيح")
require("WindowInsetsAnimationCompat.Callback" in ime,
        "P0: composer غير متزامن مع حركة لوحة المفاتيح")
require("Build.VERSION_CODES.VANILLA_ICE_CREAM" in ime and "systemBars()" in ime,
        "P0: حارس IME لا يميز فرض edge-to-edge في Android 15+ أو لا يحمي حواف النظام")
require("displayCutout()" in ime, "P0: حارس IME لا يحمي القص/النوتش")
require("calculateImeOverlap" in ime and "currentWindowMetrics.bounds.bottom" in ime,
        "P0: لا يوجد قياس هندسي فعلي لتداخل composer مع IME")
require("composer.translationY" in ime,
        "P0: لا يوجد رفع منخفض الكلفة لمربع الكتابة عند التداخل")
require("val bottom = state.baseContentPadding[3] + bars.bottom" in ime,
        "P0: Padding الجذر يجب أن يتبع حواف النظام فقط")
require("baseContentPadding[3] + ime.bottom" not in ime,
        "P0: عاد ارتفاع IME الكامل إلى Padding الجذر")
require("composer_must_remain_visible_with_keyboard" in ime,
        "P0: عقد بقاء مربع الكتابة ظاهرًا مع لوحة المفاتيح مفقود")
require("double_lift_prevented_when_adjust_resize_already_works" in ime,
        "P0: لا يوجد عقد يمنع الرفع المزدوج")

for token in [
    "foreground_input_grace",
    "typing_active",
    "TYPING_SUPPRESS_MS",
    "setOnFocusChangeListener",
    "reads_or_stores_user_text\" to false",
]:
    require(token in input_safety, f"P0: حارس الكتابة يفتقد {token}")
require("suppressProactiveResumeFor" in crash,
        "P0: لا توجد بوابة لكبح الاستئناف التلقائي أثناء الكتابة")

require("HakimCrashShield.install(this)" in app,
        "P0: حارس التعطل لا يسبق تهيئة المكونات")
require(app.find("HakimCrashShield.install(this)") < app.find("HakimLearning.initialize(this)"),
        "P0: حارس التعطل يثبت بعد مكونات قد تنهار")
for token in [
    "getHistoricalProcessExitReasons",
    "ApplicationExitInfo.REASON_CRASH",
    "ApplicationExitInfo.REASON_ANR",
    "safe_recovery_until",
    "last_auto_resume_mission_id",
    "records_user_text_or_credentials\" to false",
]:
    require(token in crash, f"P0: حارس التعطل يفتقد {token}")
require("guardNonCritical" in app and "restore_mission_state" in app,
        "P0: أعطال البدء غير الحاكمة ما زالت قادرة على إسقاط واجهة حكيم")

require("class HakimChatMessageAdapter" in ui and "BaseAdapter" in ui, "P0: سجل الرسائل غير معاد التدوير")
require("hasStableIds" in ui, "P0: محول الرسائل لا يعلن IDs ثابتة")
require("maxMessages" in ui and "trimToBudget" in ui, "P0: سجل الرسائل قد ينمو بلا حد")
for mode in ["PRESSURE", "CONSERVE", "BALANCED", "PERFORMANCE"]:
    require(f"HakimResourceGovernor.Mode.{mode}" in ui, f"P0: ميزانية الرسائل لا تراعي وضع {mode}")
require("humanFacing" in ui and "أعمل على أفضل مسار للمهمة" in ui,
        "P0: ضجيج المزود/التنفيذ لم يُفصل عن تجربة الإنسان")
require("ماذا تريد أن تنجز؟" in polish and "خصوصيتك أولًا • توقف في أي وقت" in polish,
        "P0: الواجهة الأساسية لم تُبسّط إلى لغة إنسانية مباشرة")
require("technical_detail_hidden_by_default" in polish,
        "P0: تفاصيل النظام التقنية ليست مخفية افتراضيًا")

summary = agents.split("fun summary", 1)[1].split("fun status", 1)[0]
for forbidden in ["الثقة:", "الوكلاء:", "decisionScore", "inferenceConfidence"]:
    require(forbidden not in summary, f"P0: الملخص البشري يعرض تفاصيل داخلية: {forbidden}")
require("human_facing_summary_hides_internal_telemetry" in agents,
        "P0: لا يوجد عقد صريح لفصل telemetry عن ملخص المستخدم")

require("ScrollView" not in chat, "P0: عادت واجهة ScrollView المتضخمة")
require("private lateinit var transcript: TextView" not in chat, "P0: عاد TextView transcript القديم للمحادثة")
require("transcript.append" not in chat, "P0: عاد تراكم النص الكامل في TextView واحد")
require("ListView" in chat and "HakimChatMessageAdapter" in chat, "P0: واجهة المحادثة لا تستخدم السجل الخفيف")
require("TRANSCRIPT_MODE_ALWAYS_SCROLL" in chat, "P0: قائمة الرسائل لا تتبع آخر الرسائل بكفاءة")

require('text = "حكيم"' in chat, "P0: رأس واجهة حكيم مفقود")
require('"نفّذ/أكمل"' in chat and "contentDescription = description" in chat,
        "P0: زر الإرسال/الاستمرار غير واضح أو غير موسوم لسهولة الوصول")
require("IME_ACTION_SEND" in chat, "P0: لوحة المفاتيح لا تملك إرسالًا مباشرًا")
require("تلقائي — حكيم يختار" in chat, "P0: اختيار الوكيل التلقائي ليس الافتراضي الظاهر")
require("PopupMenu" in chat and "showToolsMenu" in chat and "showAgentMenu" in chat,
        "P0: الأدوات/الوكلاء تزاحم المحادثة بدل القائمة المدمجة")
require("startVoiceInput" in chat and "RecognizerIntent" in chat, "P0: الإدخال الصوتي مفقود")
require("إيقاف المهمة فورًا" in chat and "cancelCurrentMission" in chat, "P0: إيقاف المهمة ليس متاحًا فورًا")
require("مركز حكيم والاتصال المحلي" in chat, "P0: مركز الاتصال المحلي غير قابل للوصول من الواجهة")

pos_build = chat.find("buildUi()")
pos_init = chat.find("initializeTtsIfNeeded()")
require(pos_build >= 0 and pos_init > pos_build, "P0: TTS قد يبدأ قبل بناء واجهة المحادثة")
require("if (voiceRepliesEnabled) initializeTtsIfNeeded()" in chat, "P0: TTS يبدأ حتى عندما يكون الرد الصوتي مغلقًا")
require("if (tts != null) return" in chat, "P0: يمكن إنشاء أكثر من محرك TTS")

require("HakimResourceGovernor.snapshot" in ui, "P0: الواجهة لا تتكيف مع ضغط موارد الهاتف")
require("RecyclerView" not in build and "compose" not in build.lower(), "P0: أضيف إطار UI أثقل دون حاجة مادية")
require("quality_and_governance_never_downgraded" in resource, "P0: تحسين الواجهة قد يخفض جودة الحكم")
require("verify_chat_first_ui.py" in workflow and "verify_safe_input_path.py" in workflow,
        "P0: لا توجد بوابات CI تمنع انحدار واجهة المحادثة ومسار الكتابة")

print("HAKIM_CHAT_FIRST_UI=PASS")
print("HAKIM_IME_DOCKED_COMPOSER_GUARD=PASS")
print("HAKIM_INPUT_STABILITY_GUARD=PASS")
print("HAKIM_CRASH_LOOP_GUARD=PASS")
print("HAKIM_SIMPLE_PROFESSIONAL_UI=PASS")
