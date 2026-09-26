from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
fabric = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimExecutionFabric.kt").read_text(encoding="utf-8")
relay = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
pair = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimPairingActivity.kt").read_text(encoding="utf-8")
resilience = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConnectionResilience.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimService.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")
boot = (ROOT / "app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("EXECUTION_FABRIC_GATE=FAIL reason=" + reason)

import re
m = re.search(r"versionCode\s+(\d+)", gradle)
req(m is not None and int(m.group(1)) >= 20110, "candidate_version")
req("EXECUTION-FABRIC-2026-09-24-v1" in fabric, "fabric_version")
for token in [
    '"secure_relay"', '"legacy_websocket"', '"local_adb"',
    '.put("online_requires_live_path", true)',
    '.put("single_path_failure_does_not_close_goal", true)',
]:
    req(token in fabric, "fabric:" + token)

# Result transport remains end-to-end encrypted. 20306 makes the Hakim HTTPS bridge
# primary while retaining encrypted ntfy as an explicit recovery path.
for token in [
    'KEY_RESULT_TOPIC = "relay_result_topic"',
    'RESULT_PREFIX = "HR1."',
    'RESULT_AAD = "HAKIM-RESULT-v1"',
    '"/device/v1/results?topic="',
    '"Authorization", "Bearer " + relayKey',
    '"https://ntfy.sh/" + resultTopic',
    '"legacy_fallback"',
    'fun isConnected(): Boolean = connected',
]:
    req(token in relay, "relay_contract:" + token)
req("KEY_RESULT_URL" not in relay and "result_url" not in relay, "stale_result_url_contract")
req('getQueryParameter("relay_result_topic")' in pair, "pairing_contract")

req("HakimExecutionFabric.recover(app, reason)" in resilience, "resilience_not_delegated")
req("HakimUnifiedRelay.start(applicationContext)" in service, "secure_relay_not_hosted_by_service")
req("HakimLocalPairing.reconnectAsync(applicationContext)" in service, "adb_not_recovered_by_service")
req('HakimExecutionFabric.recover(applicationContext, "task_removed")' in service, "task_removed_recovery")
req('HakimExecutionFabric.recover(this, "app_start")' in app, "app_start_recovery")
req('HakimExecutionFabric.recover(context, "boot_or_replace")' in boot, "boot_recovery")

# Known failure: restoring the stale result-url contract must be detected.
mutant = relay.replace('KEY_RESULT_TOPIC = "relay_result_topic"', 'KEY_RESULT_URL = "relay_result_url"', 1)
req('KEY_RESULT_TOPIC = "relay_result_topic"' not in mutant, "known_failure_not_detected")

print("EXECUTION_FABRIC_GATE=PASS")
