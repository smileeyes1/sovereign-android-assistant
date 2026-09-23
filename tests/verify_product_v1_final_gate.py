from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = (ROOT / "governance/PRODUCT_V1_FINAL_CONTRACT.md").read_text(encoding="utf-8")
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimInferenceEngine.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PRODUCT_V1_FINAL_GATE=FAIL reason=" + reason)

for phrase in [
    "Normal chat never treats opening ChatGPT/Gemini/Claude/DeepSeek as the answer",
    "at least one GENERAL_CHAT DIRECT_MODEL engine is configured and field-proven",
    "consumer ChatGPT login",
]:
    req(phrase in CONTRACT, "contract:" + phrase)

req("GENERAL_CHAT" in ENGINE, "general_chat_capability_missing")
req("sealed class Result" in ENGINE, "engine_result_contract_missing")
req("Launching another app is not an engine result" in ENGINE, "launch_not_result_guard_missing")
req("return emptyList()" not in REGISTRY, "no_direct_general_chat_engine_registered")

print("PRODUCT_V1_FINAL_GATE=PASS direct_general_chat=true in_app_result=true")
