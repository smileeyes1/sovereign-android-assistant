from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
APPKT=(APP/"HakimApp.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
RES=(APP/"HakimConnectionResilience.kt").read_text(encoding="utf-8")
ALARM=(APP/"HakimResilienceAlarmReceiver.kt").read_text(encoding="utf-8")
WATCH=(APP/"HakimRelayWatchdog.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
BOOT=(APP/"BootReceiver.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("RESILIENCE_20310=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20310,"version")
req("control-channel-recovery" in BUILD and "alarm-watchdog" in BUILD,"version_name")

for token in [
    "setAndAllowWhileIdle",
    "ELAPSED_REALTIME_WAKEUP",
    "HakimUnifiedRelay.ensureAlive",
    "HakimConnectionResilience.recover",
]:
    req(token in ALARM,"alarm:"+token)

for token in [
    "scheduleWithFixedDelay",
    "HakimUnifiedRelay.ensureAlive",
    "HakimConnectionResilience.scheduleSoon",
]:
    req(token in WATCH,"watchdog:"+token)

req('HakimUnifiedRelay.ensureAlive(this, "command_center_resume")' in CENTER,"resume_relay")
req('HakimConnectionResilience.recover(this, "command_center_resume")' in CENTER,"resume_recovery")
req("HakimResilienceAlarmReceiver.schedule(this)" in CENTER,"resume_alarm")

req("HakimResilienceAlarmReceiver.schedule(this)" in APPKT,"app_alarm")
req("HakimRelayWatchdog.install(this)" in APPKT,"app_watchdog")
req("override fun onTrimMemory" in APPKT,"trim_memory_recovery")

req("HakimResilienceAlarmReceiver.schedule(app)" in RES,"resilience_alarm_composed")
req("HakimRelayWatchdog.install(app)" in RES,"resilience_watchdog_composed")

req("loopGeneration" in RELAY,"relay_generation")
req("lastLoopProgressAt" in RELAY,"relay_progress")
req("fun restart(context: Context, reason: String)" in RELAY,"relay_restart")
req("fun ensureAlive(context: Context, reason: String" in RELAY,"relay_ensure_alive")
req("generation == loopGeneration" in RELAY,"relay_generation_guard")

req("HakimResilienceAlarmReceiver.schedule(context, 90_000L)" in BOOT,"boot_alarm")
req("HakimRelayWatchdog.install(context)" in BOOT,"boot_watchdog")

req('android:name=".HakimResilienceAlarmReceiver"' in MANIFEST,"alarm_receiver")
req("ps.hakim.stable.RESILIENCE_ALARM" in MANIFEST,"alarm_action")
req("android.intent.action.USER_PRESENT" in MANIFEST,"user_present")
req("android.intent.action.USER_UNLOCKED" in MANIFEST,"user_unlocked")

# لا نطلب استثناء بطارية قسريًا ولا exact-alarm خاصًا.
req("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" not in MANIFEST,"battery_whitelist_forbidden")
req("SCHEDULE_EXACT_ALARM" not in MANIFEST,"exact_alarm_permission_forbidden")

req("python3 tests/verify_20310_resilience.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20310"' in WORKFLOW,"workflow_version")

print("RESILIENCE_20310=PASS job=true alarm=true watchdog=true resume=true boot=true relay_restart=true")
