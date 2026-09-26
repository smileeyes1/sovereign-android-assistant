from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
RES=(APP/"HakimConnectionResilience.kt").read_text(encoding="utf-8")
DOC=(APP/"HakimConstraintDoctor.kt").read_text(encoding="utf-8")
SVC=(APP/"HakimService.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v,reason):
    if not v:
        raise SystemExit("CONTROL_CHANNEL_20305=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))>=20305,"version_floor")
req("control-channel-recovery" in BUILD,"version_name_lineage")

for token in [
    "URGENT_JOB_ID = 771210",
    "setMinimumLatency(URGENT_DELAY_MS)",
    "setOverrideDeadline(URGENT_DEADLINE_MS)",
    "scheduleSoon(app, \"offline_\"",
]:
    req(token in RES,"resilience:"+token)

req("val securePaired = HakimUnifiedRelay.isConfigured(app)" in DOC,"secure_pairing_not_diagnosed")
req("val paired = legacyPaired || securePaired" in DOC,"secure_pairing_not_first_class")
req('"control_channel_online"' in DOC,"control_channel_status_missing")
req("secureConnected || legacyConnected" in DOC,"online_status_not_multi_path")

req('scheduleSoon(applicationContext, "service_destroyed")' in SVC,"service_destroyed_urgent_recovery_missing")
req('scheduleSoon(applicationContext, "task_removed")' in SVC,"task_removed_urgent_recovery_missing")
req('if (isPaired()) {\n            createBrowser()\n            connectRemote()' in SVC,"secure_only_service_must_not_create_legacy_webview")
req('قناة حكيم المشفّرة تعمل — وضع خفيف بلا متصفح' in SVC,"secure_only_lightweight_state_missing")
req('if (!::webView.isInitialized) createBrowser()' in SVC,"browser_must_be_lazy_when_needed")

for token in [
    'putString("connection_recovery_state", "healthy")',
    'putLong("last_recovery_ok_at", connectedAt)',
    'HakimHealthBeacon.sendAsync(context, "secure_relay_connected")',
    'scheduleSoon(context, "secure_relay_failure")',
]:
    req(token in RELAY,"relay:"+token)

req("python3 tests/verify_20305_control_channel_recovery.py" in WORKFLOW,"workflow_gate_missing")
print("CONTROL_CHANNEL_20305=PASS primary=secure_relay urgent_recovery=true periodic_watchdog=true secure_pairing=true")
