from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInferenceEngine.kt").read_text(encoding="utf-8")
GEMINI = (ROOT / "app/src/main/java/ps/hakim/phoneagent/GeminiDirectEngine.kt").read_text(encoding="utf-8")
OPENROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterFreeEngine.kt").read_text(encoding="utf-8")
MATRIX = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimWisdomMatrix.kt").read_text(encoding="utf-8")
FREE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimFreePolicy.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
SECRETS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSecretStore.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PRODUCT_V1_CANDIDATE_GATE=FAIL reason=" + reason)

for needed in ["GENERAL_CHAT", "fun complete(", "fun cancel()"]:
    req(needed in ENGINE, "engine:" + needed)

for needed in ["OpenRouterFreeEngine(context)", "GeminiDirectEngine(context)", "HakimWisdomMatrix", "attachmentsSupported"]:
    req(needed in REGISTRY, "registry:" + needed)

for needed in ["openrouter/free", "https://openrouter.ai/api/v1/chat/completions", '"stream", true', "token_price=0"]:
    req(needed in OPENROUTER, "openrouter:" + needed)

for needed in ["https://generativelanguage.googleapis.com/v1beta/interactions", '"stream", true', "previous_interaction_id"]:
    req(needed in GEMINI, "gemini:" + needed)

for needed in ["zeroCostCertainty", "reliability", "privacy", "learnedSpeed", "sortedByDescending"]:
    req(needed in MATRIX, "matrix:" + needed)

req("getBoolean(KEY_FREE_ONLY, true)" in FREE, "free_only_not_default")
req("OpenRouterFreeEngine.ID -> true" in FREE, "zero_price_engine_not_allowed")
req("DIRECT_MODEL" in ROUTER and "HakimEngineRegistry.bestGeneralChat" in ROUTER, "router_direct_missing")
req("retryDirectOrBlock" in CENTER and "HakimEngineTelemetry.record" in CENTER, "automatic_failover_missing")
req("AndroidKeyStore" in SECRETS, "keystore_missing")
req("OpenRouter المجاني" in HOME and "وضع الذكاء: مجاني فقط" in HOME, "free_provider_ui_missing")

print("PRODUCT_V1_CANDIDATE_GATE=PASS free_first=true direct_engines=2 failover=true streaming=true")
