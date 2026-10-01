from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

guard = (APP / "HakimPhoneBypassGuard.kt").read_text(encoding="utf-8")
manager = (APP / "HakimTaskManager.kt").read_text(encoding="utf-8")
health = (APP / "HakimHealthBeacon.kt").read_text(encoding="utf-8")
relay = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise AssertionError(reason)

req("object HakimPhoneBypassGuard" in guard, "guard_missing")
req('ADGUARD_DEFAULT = "dns.adguard-dns.com"' in guard, "adguard_default_detection_missing")
req('ADGUARD_UNFILTERED = "unfiltered.adguard-dns.com"' in guard, "adguard_unfiltered_detection_missing")
req('ADGUARD_FAMILY = "family.adguard-dns.com"' in guard, "adguard_family_detection_missing")
req('CLEANBROWSING_FAMILY = "family-filter-dns.cleanbrowsing.org"' in guard, "cleanbrowsing_family_detection_missing")
req("isPrivateDnsActive" in guard and "privateDnsServerName" in guard, "private_dns_probe_missing")
req("TRANSPORT_VPN" in guard, "vpn_probe_missing")
req("httpProxy" in guard, "proxy_probe_missing")
req('"bypass_risk"' in guard and '"phone_dns_layer_state"' in guard, "bounded_posture_missing")
req("Settings.Global" not in guard and "WRITE_SECURE_SETTINGS" not in guard, "privileged_dns_write_forbidden")
req("addJavascriptInterface" not in guard, "javascript_bridge_forbidden")
req("browser" not in guard.lower() and "history" not in guard.lower(), "browsing_history_collection_forbidden")
req("HakimPhoneBypassGuard.status(context)" in manager, "task_manager_not_integrated")
req("familyConfigured && resolverVerified && phoneBypass" in manager, "phone_bypass_does_not_block_completion")
req('.put("phone_bypass_guard", HakimPhoneBypassGuard.status(context))' in health, "health_posture_missing")
req('.put("phone_bypass_guard", HakimPhoneBypassGuard.status(context))' in relay, "relay_posture_missing")
req("versionCode 20326" in gradle and "phone-bypass-guard-r16" in gradle, "version_not_bumped")

print("PHONE_BYPASS_GUARD=PASS private_dns=true vpn=true proxy=true no_privileged_write=true privacy_bounded=true")
