from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
PAIR=(APP/"HakimPairingActivity.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(value, reason):
    if not value:
        raise SystemExit("DIRECT_CHANNEL_20306=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))>=20306,"version_floor")
req("control-channel-recovery-direct" in BUILD,"version_name_lineage")

for token in [
    'KEY_BRIDGE_BASE = "relay_bridge_base"',
    'DEFAULT_BRIDGE_BASE = "https://hakim-chatgpt-bridge-production.up.railway.app"',
    '"/device/v1/commands?topic="',
    '"/device/v1/results?topic="',
    '"Authorization", "Bearer " + relayKey',
    '"direct_connected"',
    '"legacy_fallback"',
    'HakimConnectionResilience.scheduleSoon(context, "secure_relay_failure")',
    'HakimHealthBeacon.sendAsync',
    '"https://ntfy.sh/"',
    'HakimSecretStore.put(context, SECRET_RELAY_KEY',
]:
    req(token in RELAY,"relay:"+token)

req('getQueryParameter("bridge_base")' in PAIR,"pair_bridge_origin_missing")
req("configurationMatches(this, topic, resultTopic, relayKey, bridgeBase)" in PAIR,"pair_idempotency_not_bridge_aware")
req("configure(this, topic, resultTopic, relayKey, bridgeBase)" in PAIR,"pair_config_not_bridge_aware")
req('setPositiveButton("اعتماد")' in PAIR and 'setNegativeButton("رفض")' in PAIR,"local_pairing_approval_weakened")
req("python3 tests/verify_20306_direct_channel.py" in WORKFLOW,"workflow_gate_missing")

print("DIRECT_CHANNEL_20306=PASS primary=direct_https fallback=encrypted_ntfy recovery=preserved")
