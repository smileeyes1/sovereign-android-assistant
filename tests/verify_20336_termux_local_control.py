#!/usr/bin/env python3
from pathlib import Path
import json
import re

root=Path(__file__).resolve().parents[1]
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
termux=(root/"app/src/main/java/ps/hakim/phoneagent/HakimTermuxControl.kt").read_text(encoding="utf-8")
fabric=(root/"app/src/main/java/ps/hakim/phoneagent/HakimExecutionFabric.kt").read_text(encoding="utf-8")
selfcheck=(root/"app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
bootstrap=(root/"scripts/hakim-termux-local-control-bootstrap.sh").read_text(encoding="utf-8")
active=json.loads((root/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
promotion=json.loads((root/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))

def require(ok,msg):
    if not ok:
        raise SystemExit("TERMUX_LOCAL_CONTROL_20336=FAIL "+msg)

require(re.search(r"versionCode\s+20336\b",gradle) is not None,"version_code")
require("termux-local-control-r27" in gradle,"version_name")

for token in [
    '<uses-permission android:name="com.termux.permission.RUN_COMMAND" />',
    '<package android:name="com.termux" />',
    'android:name=".HakimTermuxResultReceiver"',
]:
    require(token in manifest,"manifest:"+token)

for token in [
    'object HakimTermuxControl',
    'com.termux.app.RunCommandService',
    'com.termux.RUN_COMMAND',
    'com.termux.RUN_COMMAND_PATH',
    'com.termux.RUN_COMMAND_ARGUMENTS',
    'com.termux.RUN_COMMAND_PENDING_INTENT',
    '/data/data/com.termux/files/home/.hakim/bin/hakim-control',
    '"status"',
    '"adb_status"',
    '"adb_connect"',
    '"adb_selftest"',
    '"resilience_status"',
    '.put("fixed_profiles_only", true)',
    '.put("arbitrary_shell_exposed", false)',
    '.put("external_remote_quota_required", false)',
    '.put("paid_provider_required", false)',
    '.put("high_impact_requires_separate_gate", true)',
    '.put("unlimited_claim", false)',
    '.put("resource_limits_apply", true)',
    'class HakimTermuxResultReceiver',
    'getBundleExtra("result")',
    'PendingIntent.getBroadcast(app, id, callback, flags)',
    'result.getString("stdout"',
    'result.getString("stderr"',
    'result.getInt("exitCode"',
]:
    require(token in termux,"kotlin:"+token)

for forbidden in [
    'fun runShell(',
    'fun executeShell(',
    'payload.optString("command")',
    'putExtra(EXTRA_COMMAND_PATH, profile)',
]:
    require(forbidden not in termux,"arbitrary_shell:"+forbidden)

for token in [
    'HakimTermuxControl.ready(app)',
    'HakimTermuxControl.recover(app',
    'onlinePaths.put("termux_local")',
    'configuredPaths.put("termux_local")',
    '.put("termux_control", termux)',
    '.put("termux_local_online", termuxOnline)',
    '.put("local_free_execution_preferred", true)',
]:
    require(token in fabric,"fabric:"+token)

for token in [
    'val termux = HakimTermuxControl.status(context)',
    '"Termux لا يكشف shell حرًا"',
    '"Termux لا يحتاج حصة تحكم بعيدة"',
    '"Termux لا يحتاج مزودًا مدفوعًا"',
    '"لا ادعاء بلا حدود حرفيًا"',
    '.put("termux_control", termux)',
]:
    require(token in selfcheck,"selfcheck:"+token)

for token in [
    'allow-external-apps=true',
    'HAKIM_TERMUX_BOOTSTRAP=PASS',
    'HAKIM_TERMUX_BOOTSTRAP=ROLLED_BACK',
    'com.termux.permission.RUN_COMMAND',
    'pm grant "$PACKAGE" "$RUN_PERMISSION"',
    'pm revoke "$PACKAGE" "$RUN_PERMISSION"',
    '$HOME/.termux/boot',
    'PROFILE="${1:-status}"',
    'HAKIM_TERMUX_CONTROL=BLOCKED reason=profile_not_allowed',
    'timeout 8 "$HOME/.hakim/adb-self.sh"',
    'timeout 6 adb connect "$ep"',
]:
    require(token in bootstrap,"bootstrap:"+token)

for forbidden in [
    '@wonderwhy-er/desktop-commander',
    'npx ',
    'REMOTE_MAINTENANCE',
    'curl ',
    'wget ',
    'scan_host',
    'ports=range(30000,50001)',
]:
    require(forbidden not in bootstrap,"external_dependency:"+forbidden)

require(active["android"]["candidate"]["version_code"]==20336,"active_candidate")
require(active["productization"]["candidate_version"]==20336,"product_candidate")
require(active["productization"]["termux_local_control_source_integrated"] is True,"source_integrated")
require(active["productization"]["termux_local_control_field_verified"] is False,"field_must_be_pending")
require(active["productization"]["termux_external_remote_quota_required"] is False,"quota")
require(active["productization"]["termux_arbitrary_shell_exposed"] is False,"shell")
require(promotion["candidate_version"]==20336,"promotion_candidate")
require(promotion["termux_local_control_source_integrated"] is True,"promotion_source")
require(promotion["termux_local_control_field_verified"] is False,"promotion_field_pending")

print("TERMUX_LOCAL_CONTROL_20336=PASS")


relay=(root/"app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
for token in [
    '"termux_status"',
    '"termux_probe"',
    '"termux_recover"',
    'SAFE_AUTOMATIC_OPS',
    'HakimTermuxControl.status(context)',
    'HakimTermuxControl.probe(context, "secure_relay_probe")',
    'HakimTermuxControl.recover(context, "secure_relay_recover")',
]:
    require(token in relay,"relay:"+token)
