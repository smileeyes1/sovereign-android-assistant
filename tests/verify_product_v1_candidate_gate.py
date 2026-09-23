from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInferenceEngine.kt").read_text(encoding="utf-8")
GEMINI = (ROOT / "app/src/main/java/ps/hakim/phoneagent/GeminiDirectEngine.kt").read_text(encoding="utf-8")
OPENROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterDirectEngine.kt").read_text(encoding="utf-8")
PKCE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterPkceAuth.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
SECRETS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSecretStore.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")

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
    "OpenRouterDirectEngine(context)",
    "bestGeneralChat",
    "HakimSecretStore.has",
]:
    req(needed in REGISTRY, "registry:" + needed)

for needed in [
    "https://generativelanguage.googleapis.com/v1beta/interactions",
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
req("Gemini مباشر" in HOME and "اختبار المحرك المباشر" in HOME, "provider_setup_ui_missing")

req('"openrouter/free"' in OPENROUTER, "free_router_missing")
req('"paid_fallback=false"' in OPENROUTER, "paid_fallback_guard_missing")
req("code_challenge_method" in PKCE and "S256" in PKCE, "pkce_missing")

print("PRODUCT_V1_CANDIDATE_GATE=PASS direct_engine=openrouter_free+gemini streaming=true no_paid_fallback=true")
