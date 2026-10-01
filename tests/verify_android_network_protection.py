from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMobileTaskActivity.kt").read_text(encoding="utf-8")
task = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimNetworkProtectionTask.kt").read_text(encoding="utf-8")
guardian = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimNetworkGuardian.kt").read_text(encoding="utf-8")
pairing = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimLocalPairing.kt").read_text(encoding="utf-8")

def req(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)

req('android:name=".HakimMobileTaskActivity"' in manifest, "mobile_task_activity_missing")
req('android:scheme="hakim"' in manifest, "hakim_scheme_missing")
req('android:host="task"' in manifest, "bounded_task_host_missing")
req('android:path="/network-protection"' in manifest, "network_protection_path_missing")

# Preserve safe-install P0: no Accessibility surface is declared.
req('android:name=".HakimAccessibilityService"' not in manifest, "accessibility_service_must_remain_undeclared")
req('android.permission.BIND_ACCESSIBILITY_SERVICE' not in manifest, "accessibility_binding_must_remain_hidden")

req('data?.scheme == "hakim"' in activity, "scheme_not_validated")
req('data.host == "task"' in activity, "host_not_validated")
req('data.path == "/network-protection"' in activity, "path_not_validated")
req('AlertDialog.Builder' in activity and 'موافقة وبدء' in activity, "local_approval_gate_missing")
req('HakimNetworkProtectionTask.start' in activity, "bounded_task_not_invoked")
req('لن يستخدم ADB أو إمكانية الوصول أو root' in activity, "zero_burden_router_copy_missing")

req('V3-ROUTER-GUARDIAN' in task, "router_guardian_version_marker_missing")
req('HakimNetworkGuardian.inspectAndProtect' in task, "router_guardian_execution_missing")
req('"router_guardian"' in task, "router_guardian_method_missing")
req('"home_router"' in task, "home_router_scope_missing")
req('"VERIFIED"' in task and '"BLOCKED"' in task, "verified_or_blocked_state_missing")
req('family_dns_configured' in task and 'family_resolver_verified' in task, "router_dns_verification_missing")
req('ROUTER_AUTH_REQUIRED' in task, "router_auth_blocker_missing")
req('TR064_LANHOST_NOT_FOUND' in task, "tr064_blocker_missing")
req('HakimLocalPairing.dismissPrompt' in task, "obsolete_pairing_prompt_not_dismissed")
req('fun dismissPrompt(context: Context)' in pairing, "pairing_prompt_dismiss_api_missing")

# The primary path must not use ADB, Accessibility, root, or unrestricted process execution.
req('HakimAdbConnectionManager.get' not in task, "primary_path_must_not_use_adb")
req('openWirelessDebuggingSettings' not in task, "primary_path_must_not_request_pairing")
req('Settings.Global' not in task, "primary_path_must_not_write_phone_dns")
req('Runtime.getRuntime' not in task and 'ProcessBuilder' not in task and 'su -c' not in task.lower(), "root_or_process_shell_forbidden")

# Guardian remains fail-closed and reversible.
req('EXPECTED_GATEWAY = "192.168.1.1"' in guardian, "home_gateway_gate_missing")
req('FAMILY_DNS_1 = "185.228.168.168"' in guardian, "family_dns_1_missing")
req('FAMILY_DNS_2 = "185.228.169.168"' in guardian, "family_dns_2_missing")
req('"baseline_dns"' in guardian, "baseline_missing")
req('rollbackDns(' in guardian, "rollback_missing")
req('fieldIdentity.model.equals("F8040"' in guardian, "f8040_identity_gate_missing")
req('probeExpectedF8040()' in guardian, "secondary_gateway_upstream_probe_missing")
req('via_secondary_gateway' in guardian and 'upstream_f8040_proven' in guardian, "secondary_gateway_evidence_missing")
req('targetGateway = EXPECTED_GATEWAY' in guardian, "upstream_target_gate_missing")

print("ANDROID_NETWORK_PROTECTION_GATE=PASS")
