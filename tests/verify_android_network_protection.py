from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMobileTaskActivity.kt").read_text(encoding="utf-8")
task = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimNetworkProtectionTask.kt").read_text(encoding="utf-8")
adb = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimAdbConnectionManager.kt").read_text(encoding="utf-8")
pairing = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimLocalPairing.kt").read_text(encoding="utf-8")

def req(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)

req('android:name=".HakimMobileTaskActivity"' in manifest, "mobile_task_activity_missing")
req('android:scheme="hakim"' in manifest, "hakim_scheme_missing")
req('android:host="task"' in manifest, "bounded_task_host_missing")
req('android:path="/network-protection"' in manifest, "network_protection_path_missing")

# Preserve the safe-install P0 contract: no Accessibility surface is declared.
req('android:name=".HakimAccessibilityService"' not in manifest, "accessibility_service_must_remain_undeclared")
req('android.permission.BIND_ACCESSIBILITY_SERVICE' not in manifest, "accessibility_binding_must_remain_hidden")

req('data?.scheme == "hakim"' in activity, "scheme_not_validated")
req('data.host == "task"' in activity, "host_not_validated")
req('data.path == "/network-protection"' in activity, "path_not_validated")
req('AlertDialog.Builder' in activity and 'موافقة وبدء' in activity, "local_approval_gate_missing")
req('HakimNetworkProtectionTask.start' in activity, "bounded_task_not_invoked")

req('family-filter-dns.cleanbrowsing.org' in task, "family_dns_missing")
req('V2-LOCAL-ADB' in task, "local_adb_version_marker_missing")
req('HakimAdbConnectionManager.get' in task, "local_adb_path_missing")
req('PAIRING_REQUIRED' in task, "pairing_required_state_missing")
req('HakimLocalPairing.openWirelessDebuggingSettings' in task, "pairing_flow_missing")
req('resumeAfterPairing' in task and 'HakimNetworkProtectionTask.resumeAfterPairing(app)' in pairing, "auto_resume_after_pairing_missing")
req('Settings.Global.getString' in task, "dns_verification_read_missing")
req('Settings.Global.put' not in task, "direct_secure_settings_write_forbidden")
req('WRITE_SECURE_SETTINGS' not in task, "secure_settings_permission_forbidden")
req('Runtime.getRuntime' not in task and 'ProcessBuilder' not in task and 'su -c' not in task.lower(), "root_or_process_shell_forbidden")
req('"VERIFIED"' in task and 'isProtected' in task, "verified_state_without_check")
req('"ROLLED_BACK"' in task and '"ROLLBACK_FAILED"' in task, "rollback_states_missing")
req('restorePrivateDns(before.mode, before.specifier)' in task, "baseline_restore_missing")
req('.put("family_dns_active"' in task, "privacy_safe_result_missing")
req('.put("specifier"' not in task, "raw_dns_specifier_must_not_be_exposed")

# ADB manager exposes only a bounded Private DNS capability, not a general shell API.
req('fun applyFamilyPrivateDns(host: String)' in adb, "bounded_private_dns_apply_missing")
req('fun restorePrivateDns(mode: String, specifier: String)' in adb, "bounded_private_dns_restore_missing")
req('private fun runFixedShell(command: String)' in adb, "bounded_shell_helper_missing")
req('ADB_COMMAND_NOT_ALLOWED' in adb, "adb_command_allowlist_missing")
req('settings put global private_dns_mode hostname' in adb, "private_dns_mode_command_missing")
req('settings put global private_dns_specifier' in adb, "private_dns_specifier_command_missing")
req('settings get global private_dns_mode' in adb and 'settings get global private_dns_specifier' in adb, "private_dns_readback_missing")
req('command.length > 96' in adb, "adb_command_length_guard_missing")
req('fun runShell(' not in adb and 'fun shell(' not in adb, "general_shell_api_forbidden")

print("ANDROID_NETWORK_PROTECTION_GATE=PASS")
