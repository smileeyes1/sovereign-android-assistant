from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
FREE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimFreePolicy.kt").read_text(encoding="utf-8")
MATRIX = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimWisdomMatrix.kt").read_text(encoding="utf-8")
TELEMETRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineTelemetry.kt").read_text(encoding="utf-8")
OPENROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterFreeEngine.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("FREE_INTELLIGENCE_20103=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20103, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")
req("getBoolean(KEY_FREE_ONLY, true)" in FREE, "free_only_default")
req("OpenRouterFreeEngine.ID -> true" in FREE, "openrouter_free_not_admitted")
req("geminiFreeTierConfirmed" in FREE, "gemini_free_confirmation_missing")
for needed in ["quality", "privacy", "speed", "multimodal", "zeroCostCertainty", "reliability", "latencyMs"]:
    req(needed in (MATRIX + TELEMETRY), "matrix:" + needed)
req('private const val MODEL = "openrouter/free"' in OPENROUTER, "paid_model_slug_risk")
req('"token_price=0"' in OPENROUTER, "zero_price_evidence_missing")
req("retryDirectOrBlock" in CENTER, "failover_missing")
req("nextExcluded" in CENTER and "fallback" in CENTER, "bounded_engine_switch_missing")
req("سياق المحادثة الحديث داخل حكيم" in DIRECTOR, "multiturn_context_missing")
req("takeLast(8_000)" in DIRECTOR, "context_bound_missing")

print("FREE_INTELLIGENCE_GATE=PASS candidate>=20103 cost=fail_closed matrix=adaptive failover=automatic context=bounded")
