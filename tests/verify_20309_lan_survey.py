from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
SURVEY=(APP/"HakimLanSurvey.kt").read_text(encoding="utf-8")
DIAG=(APP/"HakimNetworkDiagnostics.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("LAN_SURVEY_20309=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20309,"version")
req("lan-survey" in BUILD and "network-diagnostics" in BUILD,"version_name")
req('HakimLanSurvey.inspect(app)' in DIAG,"survey_not_exposed")

for token in [
    "Executors.newFixedThreadPool",
    "CACHE_MS",
    "PORTS = intArrayOf(80, 443, 8080, 8443, 53, 22)",
    "subnetTargets",
    "tcpOpen",
    "httpFingerprint",
    "roleHint",
    "management_candidates",
    "repeater_or_ap_candidates",
    "scanned_hosts",
]:
    req(token in SURVEY,"survey:"+token)

for forbidden in [
    "setWifiEnabled(",
    "wifiManager.disconnect(",
    "wifiManager.reassociate(",
    "removeNetwork(",
    "enableNetwork(",
    "POST",
    "PUT",
    "DELETE",
    "Authorization",
    "Basic ",
]:
    req(forbidden not in SURVEY,"read_only_violation:"+forbidden)

req("python3 tests/verify_20309_lan_survey.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20309"' in WORKFLOW,"workflow_version")

print("LAN_SURVEY_20309=PASS bounded=true read_only=true credentials=false")
