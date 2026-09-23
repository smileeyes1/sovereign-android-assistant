#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
src = (root / "app/src/main/java/ps/hakim/phoneagent/HakimNetworkGuardian.kt").read_text()
app = (root / "app/src/main/java/ps/hakim/phoneagent/HakimApp.kt").read_text()
boot = (root / "app/src/main/java/ps/hakim/phoneagent/BootReceiver.kt").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
gradle = (root / "app/build.gradle").read_text()
workflow = (root / ".github/workflows/android.yml").read_text()
health = (root / "app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt").read_text()
relay = (root / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text()

required = [
    'EXPECTED_GATEWAY = "192.168.1.1"',
    'FAMILY_DNS_1 = "185.228.168.168"',
    'FAMILY_DNS_2 = "185.228.169.168"',
    '"SetDNSServer"',
    '"GetDNSServers"',
    '"baseline_dns"',
    '"ROUTER_AUTH_REQUIRED"',
    '"FAMILY_DNS_ROLLED_BACK_UNVERIFIED"',
    '"rollback_state"',
    'rollbackDns(context, endpoint, gateway, currentDns)',
    '"full_bypass_prevention", false',
    '"dns_redirect_forced", false',
    '"dot_blocked", false',
    '"doh_controlled", false',
    '"vpn_blocked", false',
    'fingerprint.contains("ZTE"',
    'fingerprint.contains("ZXHN"',
]
missing = [x for x in required if x not in src]
if missing:
    print("NETWORK_GUARDIAN_GATE=FAIL missing=", missing)
    sys.exit(1)

if '.put("network_guardian", HakimNetworkGuardian.status(context))' not in health:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=health_observability_missing")
    sys.exit(1)
if '.put("network_guardian", HakimNetworkGuardian.status(context))' not in relay:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=relay_observability_missing")
    sys.exit(1)

if "HakimNetworkGuardian.install(this)" not in app:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=app_not_installed")
    sys.exit(1)
if "HakimNetworkGuardian.install(context)" not in boot:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=boot_not_installed")
    sys.exit(1)
if 'android:name=".HakimNetworkGuardianJobService"' not in manifest:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=job_not_manifested")
    sys.exit(1)
if "versionCode 20108" not in gradle:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=wrong_version")
    sys.exit(1)
if "verify_20108_network_guardian.py" not in workflow:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=ci_not_wired")
    sys.exit(1)
if 'test "$VERSION_CODE" = "20108"' not in workflow:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=ci_version_not_pinned")
    sys.exit(1)

# Known failure: a public resolver substituted for the family resolver must be detected.
mutant = src.replace(
    'FAMILY_DNS_1 = "185.228.168.168"',
    'FAMILY_DNS_1 = "8.8.8.8"'
)
if 'FAMILY_DNS_1 = "185.228.168.168"' in mutant:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=mutation_failed")
    sys.exit(1)
mutant_ok = all(x in mutant for x in required)
if mutant_ok:
    print("NETWORK_GUARDIAN_GATE=FAIL reason=known_failure_not_detected")
    sys.exit(1)

print("NETWORK_GUARDIAN_GATE=PASS")
