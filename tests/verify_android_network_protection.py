from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMobileTaskActivity.kt").read_text(encoding="utf-8")
task = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimNetworkProtectionTask.kt").read_text(encoding="utf-8")
accessibility = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimAccessibilityService.kt").read_text(encoding="utf-8")
relay = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
beacon = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt").read_text(encoding="utf-8")

def req(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)

req('android:name=".HakimMobileTaskActivity"' in manifest, "mobile_task_activity_missing")
req('android:scheme="hakim"' in manifest, "hakim_scheme_missing")
req('android:host="task"' in manifest, "bounded_task_host_missing")
req('android:path="/network-protection"' in manifest, "network_protection_path_missing")

req('data?.scheme == "hakim"' in activity, "scheme_not_validated")
req('data.host == "task"' in activity, "host_not_validated")
req('data.path == "/network-protection"' in activity, "path_not_validated")
req('AlertDialog.Builder' in activity and 'موافقة وبدء' in activity, "local_approval_gate_missing")
req('HakimNetworkProtectionTask.start' in activity, "bounded_task_not_invoked")

req('family-filter-dns.cleanbrowsing.org' in task, "family_dns_missing")
req('Settings.ACTION_PRIVATE_DNS_SETTINGS' in task, "private_dns_settings_route_missing")
req('Settings.Global.getString' in task, "dns_verification_read_missing")
req('Settings.Global.put' not in task, "direct_secure_settings_write_forbidden")
req('WRITE_SECURE_SETTINGS' not in task, "secure_settings_permission_forbidden")
req('Runtime.getRuntime' not in task and 'ProcessBuilder' not in task, "shell_execution_forbidden")
req('libadb' not in task.lower() and 'HakimAdb' not in task and 'su -c' not in task.lower(), "adb_or_root_path_forbidden")
req('"VERIFIED"' in task and 'isProtected' in task, "verified_state_without_check")
req('"ROLLED_BACK"' in task and '"ROLLBACK_FAILED"' in task, "rollback_states_missing")
req('before.specifier' in task and 'restore(app, before)' in task, "baseline_restore_missing")
req('.put("family_dns_active"' in task, "privacy_safe_result_missing")
req('.put("specifier"' not in task, "raw_dns_specifier_must_not_be_exposed")

req('fun foregroundPackage()' in accessibility, "foreground_package_helper_missing")
req('fun clickAnyText(labels: List<String>)' in accessibility, "bounded_click_helper_missing")
req('fun setFirstEditableText(value: String)' in accessibility, "bounded_text_helper_missing")
req('HakimNetworkProtectionTask.status(context)' in relay, "relay_status_missing")
req('HakimNetworkProtectionTask.status(context)' in beacon, "health_evidence_missing")

print("ANDROID_NETWORK_PROTECTION_GATE=PASS")
