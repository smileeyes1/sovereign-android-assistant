from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
DIAG=(APP/"HakimNetworkDiagnostics.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("NETWORK_DIAGNOSTICS_20307=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20307,"version")
req("network-diagnostics" in BUILD,"version_name")

for token in [
    "WifiInfo",
    "WifiManager",
    "rssi_dbm",
    "frequency_mhz",
    "link_speed_mbps",
    "rx_link_speed_mbps",
    "tx_link_speed_mbps",
    'tcpProbe("1.1.1.1"',
    "ssdp:all",
    "safeLocalUrl",
    "hashIdentity",
    "MAX_UPNP",
]:
    req(token in DIAG,"diag:"+token)

for permission in [
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
]:
    req(permission in MANIFEST,"permission:"+permission)

for forbidden in ["setWifiEnabled(", "wifiManager.disconnect(", "wifiManager.reassociate(", "removeNetwork(", "enableNetwork("]:
    req(forbidden not in DIAG,"read_only_violation:"+forbidden)

req('.put("network_diagnostics", HakimNetworkDiagnostics.inspect(context))' in RELAY,"status_not_exposed")
req("python3 tests/verify_20307_network_diagnostics.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20307"' in WORKFLOW,"workflow_version")

print("NETWORK_DIAGNOSTICS_20307=PASS read_only=true privacy_hashed=true")
