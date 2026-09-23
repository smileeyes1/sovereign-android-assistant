from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
BASELINE = (ROOT / "governance/LAST_VERIFIED_BASELINE.md").read_text(encoding="utf-8")
APP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PLAYPROTECT_20092_GATE=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20092, "candidate_version")
req("versionName '2.0.92-playprotect-safe'" in BUILD, "candidate_name")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for forbidden in [
    'android:name=".HakimAccessibilityService"',
    'android.permission.BIND_ACCESSIBILITY_SERVICE',
    'android:name=".HakimNotificationListener"',
    'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
    'android.permission.REQUEST_INSTALL_PACKAGES',
    'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION',
]:
    req(forbidden not in MANIFEST, "high_risk_surface:" + forbidden)

for needed in [
    "android.permission.INTERNET",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.intent.action.SEND_MULTIPLE",
]:
    req(needed in MANIFEST, "core_capability_missing:" + needed)

req("c84359e422dad0aa205f59a153a1f29d7f4c4e81" in BASELINE, "cloud_baseline")
req("20091" in BASELINE and "Play Protect" in BASELINE, "blocked_parent_not_recorded")
req("20092" in BASELINE, "candidate_not_recorded")
for forbidden_call in [
    "AutoUpdater.schedule(this)",
    "AutoUpdater.startRealtimeListener(this)",
    "AutoUpdater.checkAsync(this)",
]:
    req(forbidden_call not in APP, "self_install_loop:" + forbidden_call)

# Known failure: sensitive accessibility declaration must be detected.
probe = MANIFEST + '\n<service android:name=".HakimAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />'
try:
    req('android.permission.BIND_ACCESSIBILITY_SERVICE' not in probe, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("PLAYPROTECT_20092_GATE=FAIL reason=sentinel_not_detected")

print("PLAYPROTECT_20092_GATE=PASS package=ps.hakim.stable candidate=20092")
