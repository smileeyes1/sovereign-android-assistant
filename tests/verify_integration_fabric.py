from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
sovereign = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEngine.kt")
capabilities = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityRegistry.kt")
recovery = text("app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
workflow = text(".github/workflows/android.yml")

require("SOVEREIGN-INTEGRATION-FABRIC" in fabric, "P0: نسيج التكامل السيادي مفقود")
require("structural_integrity" in fabric and "no_isolated_critical_layer" in fabric,
        "P0: سلامة التكامل أو منع الجزر الحرجة غير ممثلة")
require("runtime_readiness_is_not_structural_integrity" in fabric,
        "P0: جاهزية الاتصال اللحظية مختلطة مع سلامة القلب البنيوي")
require("missing_external_link_changes_route_not_governance" in fabric,
        "P0: فقد الوصلة الخارجية قد يهدم الحاكمية بدل تغيير المسار")
require("HakimQuranicInvariantKernel.requireInherited" in fabric,
        "P0: نسيج التكامل لا يرث جذر القرآن")
require("HakimQuranicCorpusPolicy.status()" in fabric and "quranic_corpus_114_integrated" in fabric,
        "P0: نسيج التكامل لا يحرس شمول سور القرآن الـ١١٤")
require("ps.hakim.stable" in fabric and "fail_closed_core_changes" in fabric,
        "P0: نسيج التكامل لا يحرس هوية التطبيق/القلب")

for node in [
    "quranic_kernel", "quranic_corpus_114", "constitution", "intent_context", "decision_matrix", "sovereign_engine",
    "self_leadership", "agent_system", "mission_ledger", "authority_envelope", "capability_registry",
    "autonomous_executor", "reasoning_executor", "verification", "learning",
    "connection_resilience", "unified_relay", "self_check"
]:
    require(f'"{node}"' in fabric, f"P0: طبقة حرجة غير ممثلة في نسيج التكامل: {node}")

for edge in [
    "quranic_kernel→quranic_corpus_114", "quranic_corpus_114→constitution", "quranic_kernel→constitution",
    "decision_matrix→sovereign_engine", "sovereign_engine→agent_system",
    "mission_ledger↔sovereign_engine", "execution→verification→learning",
    "connection_resilience↔unified_relay", "recovery→mission_ledger→replan"
]:
    require(edge in fabric, f"P0: وصلة حرجة مفقودة من نسيج التكامل: {edge}")

# ترتيب بدء التطبيق: تثبيت الجذر/الدستور/التعلم ثم نسيج التكامل قبل القنوات والتعافي.
for token in ["HakimQuranicInvariantKernel.requireInherited", "HakimConstitution.install", "HakimLearning.initialize",
              "HakimIntegrationFabric.install", "HakimUnifiedRelay.start", "HakimConnectionResilience.install"]:
    require(token in app, f"P0: مرحلة بدء مفقودة: {token}")
require(app.index("HakimIntegrationFabric.install") < app.index("HakimUnifiedRelay.start"),
        "P0: القناة الموحدة تبدأ قبل تثبيت نسيج التكامل")
require(app.index("HakimIntegrationFabric.install") < app.index("HakimConnectionResilience.install"),
        "P0: التعافي يبدأ قبل تثبيت نسيج التكامل")

# المحرك السيادي لا يخطط أو ينفذ أو يتحقق خارج النسيج.
for scope in ["sovereign_assess", "sovereign_prompt", "sovereign_execute", "sovereign_verify", "sovereign_complete"]:
    require(f'HakimIntegrationFabric.requireCore(context, "{scope}")' in sovereign,
            f"P0: المحرك السيادي منفصل عن نسيج التكامل في {scope}")
require("HakimIntegrationFabric.promptContext" in sovereign and "HakimIntegrationFabric.status" in sovereign,
        "P0: نسيج التكامل غير ظاهر في سياق/حالة المحرك")

# القدرات والتعافي والفحص الذاتي كلها تستهلك نفس حقيقة التكامل.
require('Capability("integration_fabric"' in capabilities and 'Capability("secure_relay"' in capabilities,
        "P0: سجل القدرات لا يمثل التكامل والقناة الآمنة")
require("HakimIntegrationFabric.structuralStatus" in capabilities,
        "P0: سجل القدرات لا يقيس سلامة التكامل")
require('HakimIntegrationFabric.requireCore(app, "connection_resilience_install")' in recovery,
        "P0: تثبيت التعافي منفصل عن نسيج التكامل")
require('HakimIntegrationFabric.requireCore(app, "connection_resilience_recover")' in recovery,
        "P0: استعادة الاتصال قد تعمل خارج نسيج التكامل")
require("HakimIntegrationFabric.status(context)" in selfcheck and "نسيج التكامل البنيوي سليم" in selfcheck,
        "P0: الفحص الذاتي لا يحرس التكامل")
require("الجاهزية الخارجية مفصولة عن سلامة القلب" in selfcheck,
        "P0: الفحص الذاتي يخلط الاتصال الخارجي بصحة القلب")

# CI يجب أن يرفض أي انحدار في النسيج.
require("verify_integration_fabric.py" in workflow,
        "P0: لا توجد بوابة CI للتكامل والوصل")

print("HAKIM_INTEGRATION_FABRIC=PASS")
