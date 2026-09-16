from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENTS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt").read_text(encoding="utf-8")
SUPER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInfluenceSupersystem.kt").read_text(encoding="utf-8")


def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

require("val influenceStatus = HakimInfluenceSupersystem.status(context)" in AGENTS,
        "P0: منظومة كل ما هو مؤثر غير مربوطة بكل agentPrompt")
require("[منظومة كل ما هو مؤثر — الحاكم التشغيلي الأعلى للمهمة]" in AGENTS,
        "P0: الحاكم الأعلى للمؤثرات لا يدخل سياق الوكلاء")
for phrase in [
    "سجل المؤثرات مفتوح لا مغلق",
    "لا تدعِ معرفة كل المؤثرات",
    "التحريم المتحقق يمنع الإعانة",
    "التعلم الذاتي والاستباقي يحسن الترتيب",
    "SOURCE/CI/SIGNED≠FIELD",
    "أي اختلاف جوهري بين المختبَر والمسلَّم"
]:
    require(phrase in AGENTS, f"P0: حاكم مؤثر مفقود من سياق كل مهمة: {phrase}")
require('.put("influence_supersystem", HakimInfluenceSupersystem.status(context))' in AGENTS,
        "P0: حالة الوكلاء لا تعرض المنظومة العليا")
require("fun status(context: Context): JSONObject" in SUPER,
        "P0: المنظومة العليا لا توفر حالة غير تكرارية")
# منع حلقة recursion: agentPrompt يستخدم status فقط؛ promptContext الكامل في supersystem يمكنه التخطيط داخليًا ولا يستدعى من AgentSystem.
require("HakimInfluenceSupersystem.promptContext" not in AGENTS,
        "P0: ربط دائري محتمل بين AgentSystem وInfluenceSupersystem")

print("HAKIM_INFLUENCE_PER_TASK_GOVERNANCE=PASS")
print("HAKIM_INFLUENCE_NO_AGENT_RECURSION=PASS")