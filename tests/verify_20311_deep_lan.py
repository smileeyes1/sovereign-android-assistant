from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
DEEP=(APP/"HakimDeepLanDiscovery.kt").read_text(encoding="utf-8")
DIAG=(APP/"HakimNetworkDiagnostics.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("DEEP_LAN_20311=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20311,"version")
req("deep-lan-discovery" in BUILD and "network-diagnostics" in BUILD,"version_name")
req('HakimDeepLanDiscovery.inspect(app)' in DIAG,"deep_discovery_not_exposed")

for token in [
    "neighborSnapshot",
    "wifiNeighbors",
    "sshBanner",
    "tlsFingerprint",
    "httpFingerprint",
    "192.168.13.11",
    "192.168.13.100",
    "mac_oui",
    "mac_hash",
    "bssid_oui",
    "bssid_hash",
    "reachable_hint",
]:
    req(token in DEEP,"missing:"+token)

for forbidden in [
    "setWifiEnabled(",
    "disconnect(",
    "reassociate(",
    "removeNetwork(",
    "enableNetwork(",
    "requestNetwork(",
    "Authorization",
    "Basic ",
    "Digest ",
    "POST",
    "PUT",
    "DELETE",
    "password",
    "passwd",
    "login",
]:
    req(forbidden not in DEEP,"read_only_violation:"+forbidden)

req("isPrivateIpv4" in DEEP,"private_network_gate")
req("HISTORIC_TARGETS" in DEEP,"historic_targets_bounded")
req("take(32)" in DEEP,"wifi_scan_bound")
req("take(96)" in DEEP,"neighbor_bound")
req("python3 tests/verify_20311_deep_lan.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20311"' in WORKFLOW,"workflow_version")

print("DEEP_LAN_20311=PASS bounded=true read_only=true no_credentials=true")
