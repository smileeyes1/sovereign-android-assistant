from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
router=(APP/"HakimRouterAuthActivity.kt").read_text(encoding="utf-8")
tasks=(APP/"HakimTaskManager.kt").read_text(encoding="utf-8")
device=(APP/"HakimDeviceProtection.kt").read_text(encoding="utf-8")
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("R18_SPLIT_FRAME_DEVICE_FIRST=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",build)
req(m is not None and int(m.group(1))>=20328,"version")
req("split-frame-dns-device-first-r18" in build,"lineage")

# Same-origin multi-frame aggregation must exist without broadening router scope.
for token in (
    "globalDns1",
    "globalDns2",
    "aggregateDns1",
    "aggregateDns2",
    "aggregateSource",
    "variant:'split'",
    "split_components_missing",
):
    req(token in router,"split_frame:"+token)

req('private const val ROUTER_HOST = "192.168.1.1"' in router,"router_host_not_pinned")
req('private const val ROUTER_ORIGIN = "https://192.168.1.1"' in router,"router_origin_not_pinned")
req(".addJavascriptInterface(" not in router,"javascript_interface_forbidden")

# Device DNS consent may move ahead only for a proven stalled router candidate.
for token in (
    "stalledSplitCandidate",
    'state == "TR064_LANHOST_NOT_FOUND"',
    'guardian.optString("web_dns_adapter", "none") == "none"',
    'guardian.optBoolean("webview_probe_has_apply", false)',
    'guardian.optBoolean("webview_probe_has_dns", false)',
    'guardian.optBoolean("webview_probe_has_source", false)',
    'guardian.optBoolean("webview_probe_has_dhcp", false)',
    "shouldRequestDeviceDnsConsent(context)",
):
    req(token in tasks,"device_first_gate:"+token)

# Android VPN consent remains mandatory; no privilege regression.
req("VpnService.prepare(context)" in device,"vpn_consent_check_missing")
req('android.permission.BIND_VPN_SERVICE' in manifest,"vpn_permission_missing")
req("WRITE_SECURE_SETTINGS" not in manifest,"secure_settings_forbidden")
req('android:name=".HakimAccessibilityService"' not in manifest,"accessibility_regression")
req("DevicePolicyManager" not in router+tasks+device,"device_owner_scope_forbidden")

print("R18_SPLIT_FRAME_DEVICE_FIRST=PASS split_frames=true router_pinned=true consent_gate=true device_first_only_when_stalled=true")
