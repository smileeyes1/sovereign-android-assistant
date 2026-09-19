from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)


mesh = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityMesh.kt")
registry = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityRegistry.kt")
leadership = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfLeadershipController.kt")
systems = text("app/src/main/java/ps/hakim/phoneagent/HakimSystemOfSystems.kt")
workflow = text(".github/workflows/android.yml")

require("object HakimCapabilityMesh" in mesh, "P0: شبكة القدرات مفقودة")
for family in ["REASONING", "WEB", "DEVICE", "FILES", "VOICE", "COMMUNICATION", "DATA", "NETWORK", "SECURITY", "LEARNING", "UPDATE"]:
    require(family in mesh, f"P0: عائلة قدرة مفقودة: {family}")
for node in [
    "in_app_reasoning", "hakim_browser", "web_services_gateway", "android_https_intent",
    "android_share", "document_picker", "document_creator", "speech_input", "tts_output",
    "camera_capture", "local_adb", "secure_relay", "chatgpt_official", "secure_store",
    "profile_vault", "local_learning", "resource_governor", "trusted_updater", "validated_network",
]:
    require(f'Node("{node}"' in mesh, f"P0: عقدة قدرة مفقودة: {node}")

require("readyNow" in mesh and "activatable" in mesh, "P0: لا يوجد فصل بين الجاهز والقابل للتفعيل")
require("NET_CAPABILITY_VALIDATED" in mesh, "P0: الشبكة لا تتحقق من اتصال موثق")
require("HakimResourceGovernor.snapshot" in mesh, "P0: ترتيب الأدوات لا يراعي موارد الهاتف")
require("resourceCost" in mesh and "Mode.PRESSURE" in mesh and "Mode.CONSERVE" in mesh,
        "P0: لا يوجد خفض للأدوات الأثقل تحت ضغط الموارد")
require("rank(context" in mesh and "best(context" in mesh, "P0: شبكة القدرات لا ترتب/تختار الأدوات")
require("no_paid_auto_signup" in mesh and "no_permission_escalation" in mesh,
        "P0: شبكة القدرات قد توسع الكلفة أو الصلاحيات تلقائيًا")
require("external_services_are_tools_not_governors" in mesh,
        "P0: الخدمات الخارجية قد تتحول إلى حاكم")
require("عند فشل أداة انتقل" in mesh, "P0: لا توجد قاعدة تبديل تلقائي عند فشل أداة")

require('Capability("capability_mesh"' in registry, "P0: سجل القدرات لا يعرف شبكة التفوق")
require("HakimCapabilityMesh.status(context)" in registry, "P0: سجل القدرات لا يعرض حالة الشبكة")
require("HakimCapabilityMesh.rank(context, goal" in leadership, "P0: القيادة الذاتية لا تستخدم ترتيب الشبكة")
require("automatic_safe_failover" in leadership, "P0: القيادة الذاتية لا تثبت الفشل الآمن التلقائي")
require("HakimCapabilityMesh.promptContext(context, goal)" in leadership,
        "P0: قرار القيادة لا يحمل سياق الأدوات المرتبة")
require("CAPABILITY_MESH" in systems, "P0: نظام الأنظمة لا يحتوي شبكة القدرات")
require("HakimCapabilityMesh.rank(context, goal" in systems, "P0: النظام المنبثق لا يختار قدراته")
require("derived_systems_inherit_capability_mesh" in systems,
        "P0: الأنظمة المنبثقة لا ترث شبكة القدرات")
require("verify_capability_mesh.py" in workflow, "P0: لا توجد بوابة CI لشبكة القدرات")

print("HAKIM_CAPABILITY_MESH=PASS")
