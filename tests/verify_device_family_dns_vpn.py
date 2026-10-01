from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

service = (APP / "HakimFamilyDnsVpnService.kt").read_text(encoding="utf-8")
device = (APP / "HakimDeviceProtection.kt").read_text(encoding="utf-8")
activity = (APP / "HakimFamilyDnsVpnActivity.kt").read_text(encoding="utf-8")
tasks = (APP / "HakimTaskManager.kt").read_text(encoding="utf-8")
command = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
app = (APP / "HakimApp.kt").read_text(encoding="utf-8")
health = (APP / "HakimHealthBeacon.kt").read_text(encoding="utf-8")
relay = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("DEVICE_FAMILY_DNS_VPN_R17=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", build)
req(m is not None and int(m.group(1)) >= 20327 and "device-family-dns-vpn-r17" in build, "version")

# Android consent is mandatory and cannot be bypassed.
req("VpnService.prepare(this)" in activity and "RESULT_OK" in activity, "android_vpn_consent_gate_missing")
req("markConsentDenied" in activity, "consent_denial_not_honored")
req("startActivityForResult" not in service, "service_must_not_fake_consent")

# Private VPN surface.
req('android:name=".HakimFamilyDnsVpnService"' in manifest, "vpn_service_missing")
req('android:permission="android.permission.BIND_VPN_SERVICE"' in manifest, "bind_vpn_permission_missing")
req('android:name=".HakimFamilyDnsVpnActivity"' in manifest, "vpn_consent_activity_missing")
req('android:name=".HakimFamilyDnsVpnService"\n            android:permission="android.permission.BIND_VPN_SERVICE"\n            android:exported="false"' in manifest,
    "vpn_service_not_private")
req('android:name=".HakimFamilyDnsVpnActivity"\n            android:exported="false"' in manifest,
    "vpn_activity_not_private")

# Split tunnel: only virtual DNS /32 enters TUN. Never capture general browsing.
req('.addDnsServer(VPN_DNS)' in service and '.addRoute(VPN_DNS, 32)' in service, "dns_split_route_missing")
for forbidden in (
    '.addRoute("0.0.0.0", 0)',
    '.addRoute("::", 0)',
    '.addRoute("0.0.0.0",0)',
    '.addRoute("::",0)',
):
    req(forbidden not in service, "full_tunnel_forbidden:" + forbidden)

# DNS only, and upstream sockets must bypass the VPN.
req("protocol != 17" in service and "dstPort != DNS_PORT" in service, "dns_udp_gate_missing")
req("protect(socket)" in service, "upstream_socket_not_protected")
req("MAX_DNS_PAYLOAD" in service, "dns_payload_not_bounded")

# Family resolver endpoints, including IPv6 fallback.
for token in (
    "185.228.168.168",
    "185.228.169.168",
    "2a0d:2a00:1::",
    "2a0d:2a00:2::",
):
    req(token in service, "family_dns_missing:" + token)

# No query names or packet bodies persisted or exported.
persist_sections = device + health + relay
for forbidden in (
    'putString("query"',
    'putString("domain"',
    'put("query"',
    'put("domain"',
    '"dns_payload"',
    '"packet_body"',
):
    req(forbidden not in persist_sections, "dns_content_persistence_forbidden:" + forbidden)

# Do not overclaim bypass prevention.
req('.put("full_bypass_prevention", false)' in device, "device_full_bypass_must_be_false")
for forbidden in ("okhttp", "HttpURLConnection", "SocketChannel", "tun2socks", "socks5", "tor_client", "doh_endpoint"):
    req(forbidden.lower() not in service.lower(), "vpn_service_scope_creep:" + forbidden)

# Independent P0 task + autonomous resume.
req('DEVICE_DNS_TASK_ID = "system-device-family-dns"' in tasks, "device_p0_task_missing")
req('.put("priority", "P0")' in tasks, "device_task_not_p0")
req("syncDeviceDnsProtection(context)" in tasks, "device_task_not_synced")
req("maybeEnsureDeviceProtection()" in command, "device_autoresume_not_wired")
req("maybeOpenLocalRouterAuth()" in command and
    command.index("maybeOpenLocalRouterAuth()") < command.index("maybeEnsureDeviceProtection()"),
    "router_priority_before_device_consent_missing")
req("HakimDeviceProtection.ensureRunning(this)" in app, "boot_resume_missing")

# Bounded observability only.
req('.put("device_protection", HakimDeviceProtection.status(context))' in health, "health_status_missing")
req('.put("device_protection", HakimDeviceProtection.status(context))' in relay, "relay_status_missing")
status = device.split("fun status(context: Context)", 1)[1]
req("last_error" not in status, "public_status_exposes_error_detail")

# Forbidden privilege regressions.
req("WRITE_SECURE_SETTINGS" not in manifest, "secure_settings_forbidden")
req('android:name=".HakimAccessibilityService"' not in manifest, "accessibility_regression")
req("DevicePolicyManager" not in service + device + activity, "device_owner_scope_forbidden")
req("su " not in service + device + activity, "root_shell_forbidden")

print("DEVICE_FAMILY_DNS_VPN_R17=PASS split_dns=true mobile_wifi=true consent_gate=true private=true content_capture=false full_bypass=false")
