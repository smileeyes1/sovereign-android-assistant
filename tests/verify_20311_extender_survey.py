from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WIFI=(APP/"HakimWifiEnvironmentSurvey.kt").read_text(encoding="utf-8")
LAN=(APP/"HakimLanSurvey.kt").read_text(encoding="utf-8")
DIAG=(APP/"HakimNetworkDiagnostics.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("EXTENDER_SURVEY_20311=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20311,"version")
req("extender-survey" in BUILD and "lan-survey" in BUILD and "network-diagnostics" in BUILD,"version_name")

for token in [
    "wm.scanResults",
    "active_scan_requested",
    "ssid_hash",
    "bssid_hash",
    "channel_counts_24",
    "channel_pressure_24",
    "suggested_24_channel",
    "suggested_24_width_mhz",
    "possible_roaming_set",
]:
    req(token in WIFI,"wifi:"+token)

for forbidden in [
    "startScan(",
    "setWifiEnabled(",
    "disconnect(",
    "reassociate(",
    "removeNetwork(",
    "enableNetwork(",
]:
    req(forbidden not in WIFI,"wifi_read_only:"+forbidden)

req("HakimWifiEnvironmentSurvey.inspect(app)" in DIAG,"wifi_environment_not_exposed")
req('result.put("wifi_environment"' in DIAG,"wifi_environment_key")

for token in [
    "HttpsURLConnection",
    "vendor_hint",
    "model_hint",
    "isPrivateIpv4(ip)",
    'requestMethod = "GET"',
]:
    req(token in LAN,"lan_fingerprint:"+token)

for forbidden in [
    'requestMethod = "POST"',
    'requestMethod = "PUT"',
    'requestMethod = "DELETE"',
    'setRequestProperty("Authorization"',
    "Basic ",
]:
    req(forbidden not in LAN,"lan_write_or_auth:"+forbidden)

# Existing permissions only: do not expand privilege surface for 20311.
for forbidden_perm in [
    "android.permission.NEARBY_WIFI_DEVICES",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
]:
    req(forbidden_perm not in MANIFEST,"new_permission:"+forbidden_perm)

req("python3 tests/verify_20311_extender_survey.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20311"' in WORKFLOW,"workflow_version")

print("EXTENDER_SURVEY_20311=PASS wifi_read_only=true local_https_fingerprint=true no_new_permissions=true")
