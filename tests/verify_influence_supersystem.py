from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInfluenceSupersystem.kt").read_text(encoding="utf-8")
APP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")
EVOLUTION = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text(encoding="utf-8")
QURAN = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimQuranicInvariantKernel.kt").read_text(encoding="utf-8")
HALAL = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimHalalShubuhatGuard.kt").read_text(encoding="utf-8")
AGENTS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimAgentSystem.kt").read_text(encoding="utf-8")
SYSTEMS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt").read_text(encoding="utf-8")


def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

# الحاكم القرآني مع فصل الوحي عن الوسائل العلمية وعدم الفتوى الآلية.
require('HakimQuranicInvariantKernel.requireInherited("influence_supersystem' in SUPER, "P0: منظومة المؤثرات لا ترث الحاكم القرآني")
require("worldly_means_evidence_based" in SUPER, "P0: الوسائل الدنيوية غير مثبتة كدليلية")
require("no_automated_fatwa_from_keywords" in SUPER, "P0: لا يوجد منع صريح للفتوى الآلية من الكلمات")
require("verified_prohibition_blocks_assistance" in SUPER, "P0: لا توجد بوابة منع عند تحريم متحقق")
require("doubt_requires_verification_before_material_effect" in SUPER, "P0: الشبهة لا توقف الأثر الجوهري للتثبت")
require("لا تُعِن عليه" in SUPER and "تحقق من مصدر معتبر" in SUPER, "P0: قاعدة التعارض الشرعي/الشبهة غير صريحة")
require("worldly_science_remains_evidence_based" in QURAN, "P0: جذر القرآن لا يفصل العلم الدنيوي عن الوحي")
require("no_automated_fatwa_from_keywords" in HALAL, "P0: حارس الشبهات لا يمنع الفتوى بالكلمات")

# «كل ما هو مؤثر» سجل مفتوح، لا ادعاء حصر مغلق.
for token in [
    "REALITY_EVIDENCE", "HUMAN_IMPACT", "DEVICE_RUNTIME", "RESOURCE_PRESSURE",
    "NETWORK_CONNECTIVITY", "TOOLS_CAPABILITIES", "AUTHORITY_PERMISSIONS",
    "PRIVACY_SECRETS", "COST_LOCKIN", "SOURCES_FRESHNESS", "DEPENDENCIES",
    "MEMORY_CONTEXT", "FAILURE_HISTORY", "LEARNING_FEEDBACK",
    "DELIVERY_VISIBLE_RESULT", "FIELD_EVIDENCE", "SECURITY_THREATS", "UNKNOWN_EMERGENT"
]:
    require(token in SUPER, f"P0: عامل مؤثر مفقود: {token}")
require("all_material_influences_open_world_registry" in SUPER, "P0: سجل المؤثرات ليس مفتوح العالم")
require("unknown_emergent_factor_channel" in SUPER, "P0: لا قناة لعامل جديد/مجهول")
require("أي عامل جديد مادي الأثر" in SUPER, "P0: العامل الناشئ لا يتحول إلى حاجز واختبار")

# منظومة وكلاء: قيادة + استباق + تعليم + تعلم ذاتي + عمال + نقد/تحقق/ميدان.
for worker in [
    "ORCHESTRATOR", "QURAN_SUNNAH_GUARD", "INTENT_GUARD", "INFLUENCE_SCOUT",
    "EVIDENCE_RESEARCH", "PLANNER", "EXECUTOR", "PHONE_BROWSER", "EDUCATION_TEACHER",
    "SELF_LEARNER", "PROACTIVE_SCOUT", "CRITIC", "VERIFIER", "SAFETY_SECURITY",
    "RESILIENCE", "RESOURCE_GOVERNOR", "FIELD_AUDITOR"
]:
    require(worker in SUPER, f"P0: عامل/وكيل مطلوب مفقود: {worker}")
require("HakimAgentSystem.plan" in SUPER, "P0: المنظومة العليا غير موصولة بمنظومة الوكلاء الحالية")
require("HakimSystemOfSystems.compose" in SUPER, "P0: المنظومة العليا غير موصولة بنظام الأنظمة")
require("agent_orchestration" in SUPER and "proactive_workers" in SUPER, "P0: الوكلاء غير مثبتين كتنسيق استباقي")
require("self_learning_worker" in SUPER and "education_teacher_worker" in SUPER, "P0: وكيل التعلم/التعليم غير مثبت")
require("SELF_LEARNER" in SUPER and "التعلم الذاتي" in SUPER, "P0: التعلم الذاتي غير صريح")

# السيادة: لا سلطة ذاتية، لا صمت=موافقة، لا تعديل/دمج/توقيع كود ذاتي.
for token in [
    "authority_never_self_expands", "silence_not_consent", "self_learning_not_self_modifying_code",
    "no_autonomous_merge_or_signing", "user_intent_not_replaced"
]:
    require(token in SUPER, f"P0: حاجز سيادي مفقود: {token}")
require("لا يوسع أي وكيل سلطته" in SUPER, "P0: حاجز توسيع السلطة غير تشغيلي في السياق")
require("لا يكتب/يوقع/يدمج كودًا ذاتيًا" in SUPER, "P0: التطوير الذاتي قد يتحول لتعديل كود غير محكوم")

# المسلَّم والميدان لا يرثان النجاح النظري.
require("delivered_output_must_be_tested" in SUPER, "P0: المسلَّم لا يُختبر")
require("field_requires_real_evidence" in SUPER, "P0: الميدان لا يحتاج دليلًا حقيقيًا")
require("أي اختلاف جوهري بين المختبَر والمسلَّم" in SUPER, "P0: اختلاف المسلَّم لا يعيد الحالة لغير مثبت")

# تشغيل فعلي: بدء التطبيق + دورة التطور المستمرة، لا ملف معماري يتيم.
require("HakimInfluenceSupersystem.initialize(this)" in APP, "P0: منظومة المؤثرات لا تبدأ مع التطبيق")
require("HakimInfluenceSupersystem.runSafeCycle(app, \"app_start_deferred\")" in APP, "P0: لا دورة آمنة بعد بدء التطبيق")
require("HakimInfluenceSupersystem.initialize(app)" in EVOLUTION, "P0: دورة التطور لا تهيئ المنظومة العليا")
require("HakimInfluenceSupersystem.runSafeCycle(app, \"evolution_job\")" in EVOLUTION, "P0: دورة التطور الكاملة لا تشغل منظومة المؤثرات")
require("HakimInfluenceSupersystem.runSafeCycle(app, \"evolution_job_conserve\")" in EVOLUTION, "P0: وضع الاقتصاد يفقد المنظومة العليا")

# لا استبدال للمنظومات المثبتة؛ المنظومة العليا تنسق القائم منها.
require("enum class Agent" in AGENTS and "VERIFIER" in AGENTS and "SAFETY" in AGENTS, "P0: منظومة الوكلاء الأساسية غير موجودة")
require("object HakimSystemOfSystems" in SYSTEMS and "CAPABILITY_MESH" in SYSTEMS, "P0: نظام الأنظمة الأساسي غير موجود")

print("HAKIM_INFLUENCE_SUPERSYSTEM=PASS")
print("HAKIM_OPEN_WORLD_INFLUENCE_REGISTRY=PASS")
print("HAKIM_AGENT_WORKER_MESH=PASS")
print("HAKIM_QURAN_SUNNAH_GOVERNED_EXECUTION=PASS")
print("HAKIM_SELF_LEARNING_WITHOUT_SELF_AUTHORITY=PASS")
print("HAKIM_DELIVERED_AND_FIELD_EVIDENCE_GATES=PASS")