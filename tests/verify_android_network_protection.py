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
req('probeF8040WebSurface(context)' in guardian, "f8040_web_probe_missing")
req('conn.requestMethod = "GET"' in guardian, "f8040_probe_must_be_read_only")
req('HAKIM-F8040-SafeProbe/1' in guardian, "f8040_probe_marker_missing")
req('web_probe_candidate_paths' in guardian, "f8040_probe_evidence_missing")
req('POST' not in guardian.split('private fun probeF8040WebSurface')[1].split('private fun probeExpectedF8040')[0], "f8040_probe_post_forbidden")
req('strictGchDnsCompatible' in guardian, "strict_gch_dns_gate_missing")
req('extractSessionToken' in guardian and '_SESSION_TOKEN' in guardian, "zte_session_token_gate_missing")
req('nonDnsFingerprint' in guardian, "non_dns_mutation_guard_missing")
req('rollbackStrictGchDns' in guardian, "web_dns_rollback_missing")
req('pathWithQuery !in setOf(ZTE_GCH_DHCP_PATH, ZTE_MODERN_DHCP_PATH)' in guardian, "post_scope_not_bounded")
req('form["DNSServer1"] = FAMILY_DNS_1' in guardian and 'form["DNSServer2"] = FAMILY_DNS_2' in guardian, "family_dns_web_write_missing")
req('form["DnsServerSource"] = "0"' in guardian, "manual_dns_source_missing")
req('web_dns_readback_verified' in guardian and 'web_dns_rollback_verified' in guardian, "web_dns_evidence_missing")
req('ZTE_MODERN_VIEW_PATH' in guardian and 'ZTE_MODERN_DHCP_PATH' in guardian, "modern_zte_paths_missing")
req('tryModernZteMenuDns' in guardian and 'buildModernDhcpForm' in guardian, "modern_zte_adapter_missing")
req('OBJ_Br0AndDhcpsHosCfg_ID' in guardian and 'OBJ_LANDNS_ID' in guardian, "modern_zte_object_gate_missing")
req('RSA/ECB/PKCS1Padding' in guardian and 'zteIntegrityCheck' in guardian, "modern_zte_integrity_check_missing")
req('rollbackModernZteMenuDns' in guardian, "modern_zte_rollback_missing")
req('web_dns_adapter", "modern_menu"' in guardian, "modern_zte_observability_missing")
req('CookieManager.getInstance()' in guardian, "local_router_cookie_store_missing")
req('getCookie("https://$EXPECTED_GATEWAY/")' in guardian, "local_router_cookie_scope_missing")
req('persistLocalRouterCookie(cookie)' in guardian, "local_router_cookie_persistence_missing")
req('web_local_session_present_at_attempt' in guardian, "local_router_session_evidence_missing")

print("ANDROID_NETWORK_PROTECTION_GATE=PASS")
