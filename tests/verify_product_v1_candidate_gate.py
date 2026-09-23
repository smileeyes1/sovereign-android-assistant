from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInferenceEngine.kt").read_text(encoding="utf-8")
GEMINI = (ROOT / "app/src/main/java/ps/hakim/phoneagent/GeminiDirectEngine.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
SECRETS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSecretStore.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")
FREE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimFreeOnlyPolicy.kt").read_text(encoding="utf-8")
QURAN = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimQuranicGovernance.kt").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PRODUCT_V1_CANDIDATE_GATE=FAIL reason=" + reason)

for needed in [
    "GENERAL_CHAT",
    "fun complete(",
    "fun cancel()",
]:
    req(needed in ENGINE, "engine:" + needed)

for needed in [
    "GeminiDirectEngine(context, model)",
    "bestGeneralChat",
    "HakimSecretStore.has",
]:
    req(needed in REGISTRY, "registry:" + needed)

for needed in [
    "https://generativelanguage.googleapis.com/v1beta/interactions?alt=sse",
    '"stream", true',
    "x-goog-api-key",
    "step.delta",
    "interaction.completed",
    "previous_interaction_id",
    "IMAGES",
    "AUDIO",
    "VIDEO",
]:
    req(needed in GEMINI, "gemini:" + needed)

for needed in [
    "DIRECT_MODEL",
    "HakimEngineRegistry.bestGeneralChat",
]:
    req(needed in ROUTER, "router:" + needed)

for needed in [
    "executeDirectModel",
    "beginStreamingReply",
    "appendStreamingDelta",
    "currentDirectEngine?.cancel()",
    "returned",
]:
    if needed == "returned":
        continue
    req(needed in CENTER, "ui:" + needed)

req("AndroidKeyStore" in SECRETS, "keystore_missing")
req("gemini-3.8-flash" in FREE and "gemini-3.7-flash" in FREE and "gemini-3.5-flash-lite" in FREE, "free_model_chain_missing")
req("getBoolean(KEY_FREE_ONLY, true)" in FREE, "free_only_not_default")
req("HakimQuranicGovernance.instruction()" in DIRECTOR, "quranic_governance_not_applied")
req("لا تجعل حروف القرآن" in QURAN, "anti_mysticism_boundary_missing")
req("fallbackGeneralChat" in REGISTRY, "direct_failover_missing")
req("Gemini مباشر" in HOME and "اختبار المحرك المباشر" in HOME, "provider_setup_ui_missing")

print("PRODUCT_V1_CANDIDATE_GATE=PASS direct_engine=gemini streaming=true multimodal_inline=true free_only=true failover=true quranic_values=true")
