from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


manifest = text("app/src/main/AndroidManifest.xml")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")
ui = text("app/src/main/java/ps/hakim/phoneagent/HakimChatUi.kt")
resource = text("app/src/main/java/ps/hakim/phoneagent/HakimResourceGovernor.kt")
build = text("app/build.gradle")
workflow = text(".github/workflows/android.yml")

require("versionCode 20020" in build, "P0: واجهة المحادثة الحديثة ليست إصدارًا أعلى مستقلًا")
require("2.0.20-chat-first-lightweight" in build, "P0: اسم إصدار واجهة حكيم الحديثة مفقود")
require(manifest.count('android.intent.category.LAUNCHER') == 1, "P0: يجب بقاء واجهة تشغيل واحدة")
launcher_block = manifest.split('android.intent.category.LAUNCHER')[0][-900:]
require('android:name=".HakimAgentsChatActivity"' in launcher_block, "P0: التطبيق لا يفتح مباشرة على المحادثة")

# سجل الرسائل يجب أن يكون معاد التدوير ومحدود الذاكرة، لا transcript متضخم.
require("class HakimChatMessageAdapter" in ui and "BaseAdapter" in ui, "P0: سجل الرسائل غير معاد التدوير")
require("hasStableIds" in ui, "P0: محول الرسائل لا يعلن IDs ثابتة")
require("maxMessages" in ui and "trimToBudget" in ui, "P0: سجل الرسائل قد ينمو بلا حد")
for mode in ["PRESSURE", "CONSERVE", "BALANCED", "PERFORMANCE"]:
    require(f"HakimResourceGovernor.Mode.{mode}" in ui, f"P0: ميزانية الرسائل لا تراعي وضع {mode}")
require("ScrollView" not in chat, "P0: عادت واجهة transcript/ScrollView المتضخمة")
require("transcript" not in chat, "P0: عاد TextView المتضخم للمحادثة")
require("ListView" in chat and "HakimChatMessageAdapter" in chat, "P0: واجهة المحادثة لا تستخدم السجل الخفيف")
require("TRANSCRIPT_MODE_ALWAYS_SCROLL" in chat, "P0: قائمة الرسائل لا تتبع آخر الرسائل بكفاءة")

# واجهة حديثة: محادثة أولًا، Composer ثابت، صوت، إرسال، أدوات مخفية بدل ازدحام الشاشة.
require('text = "حكيم"' in chat, "P0: رأس واجهة حكيم مفقود")
require('contentDescription = "نفّذ/أكمل"' in chat, "P0: زر الإرسال/الاستمرار غير واضح")
require("IME_ACTION_SEND" in chat, "P0: لوحة المفاتيح لا تملك إرسالًا مباشرًا")
require("تلقائي — حكيم يختار" in chat, "P0: اختيار الوكيل التلقائي ليس الافتراضي الظاهر")
require("PopupMenu" in chat and "showToolsMenu" in chat and "showAgentMenu" in chat,
        "P0: الأدوات/الوكلاء تزاحم المحادثة بدل القائمة المدمجة")
require("startVoiceInput" in chat and "RecognizerIntent" in chat, "P0: الإدخال الصوتي مفقود")
require("إيقاف المهمة فورًا" in chat and "cancelCurrentMission" in chat, "P0: إيقاف المهمة ليس متاحًا فورًا")
require("مركز حكيم والاتصال المحلي" in chat, "P0: مركز الاتصال المحلي غير قابل للوصول من الواجهة")

# TTS لا يبدأ بلا حاجة، لتخفيف الذاكرة/زمن البدء.
pos_build = chat.find("buildUi()")
pos_init = chat.find("initializeTtsIfNeeded()")
require(pos_build >= 0 and pos_init > pos_build, "P0: TTS قد يبدأ قبل بناء واجهة المحادثة")
require("if (voiceRepliesEnabled) initializeTtsIfNeeded()" in chat, "P0: TTS يبدأ حتى عندما يكون الرد الصوتي مغلقًا")
require("if (tts != null) return" in chat, "P0: يمكن إنشاء أكثر من محرك TTS")

# لا مكتبة UI ثقيلة جديدة؛ حاكم الموارد يبقى المرجع للأداء.
require("HakimResourceGovernor.snapshot" in ui, "P0: الواجهة لا تتكيف مع ضغط موارد الهاتف")
require("RecyclerView" not in build and "compose" not in build.lower(), "P0: أضيف إطار UI أثقل دون حاجة مادية")
require("quality_and_governance_never_downgraded" in resource, "P0: تحسين الواجهة قد يخفض جودة الحكم")
require("verify_chat_first_ui.py" in workflow, "P0: لا توجد بوابة CI تمنع انحدار واجهة المحادثة")

print("HAKIM_CHAT_FIRST_UI=PASS")
