from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
GRADLE = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
APPKT = (APP / "HakimApp.kt").read_text(encoding="utf-8")
BOOT = (APP / "BootReceiver.kt").read_text(encoding="utf-8")
DOCTOR = (APP / "HakimConstraintDoctor.kt").read_text(encoding="utf-8")
JOB = (APP / "UpdateJobService.kt").read_text(encoding="utf-8")
EVOLUTION = (APP / "HakimEvolutionJobService.kt").read_text(encoding="utf-8")
AUTO = (APP / "AutoUpdater.kt").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))

def req(ok, reason):
    if not ok:
        raise SystemExit("AUTOUPDATE_WAKEUP=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)\b", GRADLE)
req(m is not None and int(m.group(1)) >= 20316, "version")
CURRENT_VERSION = int(m.group(1))
req("autoupdate-wakeup-v1" in GRADLE, "version_name")
req('android:name=".UpdateJobService"' in MANIFEST, "job_service_not_registered")
req('android.permission.BIND_JOB_SERVICE' in MANIFEST, "job_service_permission")

for token in [
    'AutoUpdater.schedule(this)',
    'AutoUpdater.startRealtimeListener(this)',
    'AutoUpdater.checkAsync(this)',
]:
    req(token in APPKT, "app_start_missing:" + token)

for token in [
    'AutoUpdater.schedule(context)',
    'AutoUpdater.startRealtimeListener(context)',
    'AutoUpdater.checkAsync(context)',
]:
    req(token in BOOT, "boot_missing:" + token)

for token in [
    'AutoUpdater.schedule(app)',
    'AutoUpdater.startRealtimeListener(app)',
    'AutoUpdater.checkAsync(app)',
]:
    req(token in DOCTOR, "doctor_missing:" + token)

req('HakimFaultContainment.guard(app, "auto_update_job", "periodic_check")' in JOB, "job_not_contained")
req('AutoUpdater.checkNow(app)' in JOB, "job_check_missing")
req('catch (_: Exception)' not in JOB, "job_silent_failure")
req('AutoUpdater.checkNow(applicationContext)' in EVOLUTION, "periodic_fallback_missing")

# Preserve the current safety contract: updater discovers/verifies/exports;
# it does not gain a silent package-install privilege in this root-fix release.
req('installer_capability", false' in AUTO, "installer_contract_changed")
req("REQUEST_INSTALL_PACKAGES" not in MANIFEST, "install_permission_expansion")
req("UPDATE_PACKAGES_WITHOUT_USER_ACTION" not in MANIFEST, "silent_install_permission")

req(STATE["android"]["candidate"]["version_code"] == CURRENT_VERSION, "state_candidate")
req(STATE["android"]["candidate"]["field_verified"] is False, "field_claim")
req(STATE["productization"].get("auto_update_wakeup_source_integrated") is True, "state_source")
req(STATE["productization"].get("auto_update_wakeup_field_verified") is False, "state_field")

print("AUTOUPDATE_WAKEUP=PASS app_start=true boot=true recovery=true periodic=true silent_install=false")
