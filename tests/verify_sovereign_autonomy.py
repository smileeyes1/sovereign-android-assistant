from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


cap = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityRegistry.kt")
auth = text("app/src/main/java/ps/hakim/phoneagent/HakimAuthorityEnvelope.kt")
lead = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfLeadershipController.kt")
mission = text("app/src/main/java/ps/hakim/phoneagent/HakimMissionLedger.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
auto = text("app/src/main/java/ps/hakim/phoneagent/HakimAutonomousExecutor.kt")
reason = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningPlanExecutor.kt")
chat = text("app/src/main/java/ps/hakim/phoneagent/HakimAgentsChatActivity.kt")

# معرفة ذاتية بالقدرات وعدم اختلاق الجاهزية.
for capability in ["secure_store", "mission_ledger", "browser", "accessibility_actions", "chatgpt_official", "profile_vault", "field_update"]:
    require(capability in cap, f"P0: القدرة {capability} غير ممثلة في سجل القدرات")
require("ready_now" in cap and "لا تدّع قدرة غير جاهزة" in cap, "P0: لا يوجد فصل بين وجود القدرة وجاهزيتها الفعلية")
require("field_update" in cap and "false" in cap, "P0: تحديث الميدان قد يدعى جاهزًا بلا توقيع")

# غلاف السلطة.
for gate in ["AUTO", "AUTO_VERIFY", "APPROVAL", "CREDENTIAL", "TRUST", "SYSTEM_PERMISSION", "BLOCK"]:
    require(gate in auth, f"P0: بوابة السلطة {gate} مفقودة")
require("لا توسع الصلاحية" in auth and "كل شيء" in auth, "P0: العبارة العامة قد توسع السلطة")
require("systemPermissionRegex" in auth, "P0: صلاحيات النظام لا تمر ببوابة مستقلة")
require("HakimActionPolicy.classify" in auth, "P0: غلاف السلطة منفصل عن سياسة الأفعال المثبتة")

# القيادة الذاتية: المستخدم يملك WHAT/WHY وحكيم HOW.
for mode in ["LEAD_AUTONOMOUSLY", "LEAD_AND_VERIFY", "RESEARCH_AND_REPLAN", "RECOVER_CAPABILITY", "WAIT_APPROVAL", "WAIT_CREDENTIAL", "WAIT_TRUST", "CANCELLED", "BLOCKED"]:
    require(mode in lead, f"P0: وضع القيادة الذاتية {mode} مفقود")
require("WHAT/WHY" in lead and "HOW" in lead, "P0: فصل السيادة بين المستخدم وحكيم غير مثبت")
require("HakimCapabilityRegistry" in lead and "HakimAuthorityEnvelope" in lead, "P0: القيادة لا تفحص القدرات/السلطة")
require("PREVENT→PLAN→EXECUTE→VERIFY→RECOVER→LEARN→FREEZE" in lead, "P0: دورة القيادة الذاتية المغلقة مفقودة")
require("لا توسع السلطة ذاتيًا" in lead, "P0: منع التصعيد الذاتي للصلاحيات مفقود")

# حق الإلغاء السيادي داخل الحالة والتنفيذ الفعلي.
require("CANCELLED" in mission and "fun cancel" in mission and "isCancelled" in mission, "P0: الإلغاء السيادي غير ممثل في سجل المهمة")
require("user_cancel_is_sovereign" in mission, "P0: حالة الإلغاء السيادي غير مكشوفة")
require("HakimMissionLedger.isCancelled" in auto, "P0: الحلقة المحلية لا تفحص إلغاء المستخدم")
require("HakimMissionLedger.isCancelled" in reason, "P0: منفذ خطة الاستدلال لا يفحص إلغاء المستخدم")
require("إيقاف المهمة فورًا" in chat and "isCancelCue" in chat and "cancelCurrentMission" in chat, "P0: المستخدم لا يملك إيقافًا فوريًا من المحادثة")

# التنفيذ الحقيقي يمر بغلاف السلطة، لا بالموجه فقط.
require("HakimAuthorityEnvelope.classifyUiAction" in auto, "P0: الحلقة المحلية لا تمرر الأفعال عبر غلاف السلطة")
require("HakimAuthorityEnvelope.classifyUiAction" in reason, "P0: خطة الاستدلال لا تمرر الأفعال عبر غلاف السلطة")
require("SYSTEM_PERMISSION" in auto and "SYSTEM_PERMISSION" in reason, "P0: صلاحيات النظام قد تنفذ كفعل عادي")

# القلب السيادي يرث كل الطبقات ويعامل الإلغاء كحاجز.
require("HakimSelfLeadershipController.promptContext" in sovereign, "P0: المحرك السيادي لا يرث القيادة الذاتية")
require("HakimSelfLeadershipController.status" in sovereign, "P0: حالة القيادة الذاتية غير مرئية")
require("HakimCapabilityRegistry.status" in sovereign and "HakimAuthorityEnvelope.status" in sovereign, "P0: القدرات/السلطة غير ظاهرة في حالة المحرك")
require("Phase.CANCELLED" in sovereign and '"cancelled"' in sovereign, "P0: المحرك السيادي لا يمنع استئناف المهمة الملغاة")

print("HAKIM_SOVEREIGN_AUTONOMY=PASS")
