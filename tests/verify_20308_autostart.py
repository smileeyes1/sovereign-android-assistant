from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
APPKT=(APP/"HakimApp.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimExecutionFabric.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("AUTOSTART_20308=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20308,"version")
req("control-channel-recovery-direct" in BUILD and "autostart" in BUILD and "network-diagnostics" in BUILD,"version_name")

req("startHakimIfPaired(prefs)" in APPKT,"application_does_not_autostart")
req("startForegroundService(intent)" in APPKT,"application_foreground_start_missing")
req('HakimExecutionFabric.recover(this, "command_center_open")' in CENTER,"launcher_execution_recovery_missing")
req('HakimConnectionResilience.recover(this, "command_center_open")' in CENTER,"launcher_connection_recovery_missing")
req("startForegroundService(intent)" in FABRIC,"fabric_service_start_missing")
req("HakimUnifiedRelay.start(app)" in FABRIC,"secure_relay_start_missing")
req("python3 tests/verify_20308_autostart.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20308"' in WORKFLOW,"workflow_version")

print("AUTOSTART_20308=PASS application=true launcher=true secure_relay=true")
