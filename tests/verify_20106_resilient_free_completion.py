from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
POLICY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimResiliencePolicy.kt").read_text(encoding="utf-8")
OPENROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterFreeEngine.kt").read_text(encoding="utf-8")
GEMINI = (ROOT / "app/src/main/java/ps/hakim/phoneagent/GeminiDirectEngine.kt").read_text(encoding="utf-8")
MATRIX = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimWisdomMatrix.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("RESILIENT_FREE_20106=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20106, "version")
req("versionName '2.1.06-resilient-free-completion'" in BUILD, "version_name")

for needed in [
    "FIRST_VISIBLE_OUTPUT_MS = 25_000L",
    "NO_VISIBLE_PROGRESS_MS = 45_000L",
    "CALL_TIMEOUT_SECONDS = 120L",
    "FAILURE_THRESHOLD = 2",
    "COOLDOWN_MS = 10L * 60L * 1000L",
    "recordSuccess",
    "recordFailure",
]:
    req(needed in POLICY, "policy:" + needed)

for engine_name, src in [("openrouter", OPENROUTER), ("gemini", GEMINI)]:
    req("HakimResiliencePolicy.CONNECT_TIMEOUT_SECONDS" in src, engine_name + ":connect_timeout")
    req("HakimResiliencePolicy.READ_STALL_TIMEOUT_SECONDS" in src, engine_name + ":read_timeout")
    req("HakimResiliencePolicy.CALL_TIMEOUT_SECONDS" in src, engine_name + ":call_timeout")
    req("FIRST_VISIBLE_OUTPUT_MS" in src, engine_name + ":first_visible_watchdog")
    req("NO_VISIBLE_PROGRESS_MS" in src, engine_name + ":no_progress_watchdog")
    req("سيحوّل حكيم تلقائيًا إلى محرك مجاني آخر" in src, engine_name + ":fallback_message")

req("HakimResiliencePolicy.isAvailable(context, it.id)" in MATRIX, "cooldown_not_in_matrix")
req("HakimResiliencePolicy.recordSuccess(this, engine.id)" in CENTER, "success_health_not_recorded")
req("HakimResiliencePolicy.recordFailure(this, engine.id" in CENTER, "failure_health_not_recorded")
req("المحرك بطيء/متعثر؛ يحوّل إلى " in CENTER, "visible_failover_missing")

# No engine may retain the old multi-minute total timeout.
req(".callTimeout(210, TimeUnit.SECONDS)" not in OPENROUTER + GEMINI, "legacy_210s_timeout")
req(".readTimeout(180, TimeUnit.SECONDS)" not in OPENROUTER + GEMINI, "legacy_180s_stall")

print("RESILIENT_FREE_20106=PASS first_visible=25s no_progress=45s total=120s circuit_breaker=10m")
