from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
APP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
BOOT = (ROOT / "app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text(encoding="utf-8")
EVOLUTION = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEvolutionJobService.kt").read_text(encoding="utf-8")
DOCTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConstraintDoctor.kt").read_text(encoding="utf-8")
SELF = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("NO_INSTALL_PROMPT_20093=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20093, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for forbidden in [
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    'android:name=".UpdateJobService"',
    'android:name=".UpdateInstallReceiver"',
    'android:name=".HakimAccessibilityService"',
    'android:name=".HakimNotificationListener"',
]:
    req(forbidden not in MANIFEST, "manifest:" + forbidden)

runtime = "\n".join([APP, CENTER, BOOT, EVOLUTION, DOCTOR, SELF])
for forbidden in [
    "AutoUpdater.schedule(",
    "AutoUpdater.startRealtimeListener(",
    "AutoUpdater.checkAsync(",
    "AutoUpdater.checkNow(",
    "AutoUpdater.openInstallPermissionSettings(",
    "AutoUpdater.canInstallPackages(",
    "INSTALL_SOURCE_PERMISSION",
    "ACTION_MANAGE_UNKNOWN_APP_SOURCES",
    "السماح لحكيم بتثبيت تحديثاته",
    "إذن التثبيت",
]:
    req(forbidden not in runtime, "runtime:" + forbidden)

req('"update_install_mode", "external_user_managed"' in DOCTOR, "external_update_mode_missing")

probe = CENTER + "\nAutoUpdater.openInstallPermissionSettings(this)"
try:
    req("AutoUpdater.openInstallPermissionSettings(" not in probe, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("NO_INSTALL_PROMPT_20093=FAIL reason=sentinel_not_detected")

print("NO_INSTALL_PROMPT_GATE=PASS candidate>=20093")
