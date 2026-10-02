from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
service = (APP / "HakimFamilyDnsVpnService.kt").read_text(encoding="utf-8")
protection = (APP / "HakimDeviceProtection.kt").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("VPN_FAIL_OPEN_R21=FAIL reason=" + reason)

m = re.search(r"versionCode\\s+(\\d+)", build)
req(m is not None and int(m.group(1)) >= 20331, "version")
req("vpn-dns-fail-open-r21" in build, "lineage")
req("return START_NOT_STICKY" in service, "sticky_restart_forbidden")
req('failOpenAndStop("upstream_timeout")' in service, "upstream_fail_open_missing")
req('failOpenAndStop("selfcheck_" + it.javaClass.simpleName)' in service, "selfcheck_fail_open_missing")
req('HakimDeviceProtection.failOpen(this, "establish_failed")' in service, "establish_fail_open_missing")
req("if (!HakimDeviceProtection.failOpenActive(this))" in service, "fail_open_state_overwrite_guard_missing")
req("FAIL_OPEN_COOLDOWN_MS" in protection, "cooldown_missing")
req("fun failOpenActive(context: Context)" in protection, "cooldown_query_missing")
req('putString("state", "FAIL_OPEN")' in protection, "fail_open_state_missing")
req('putString("state", "FAIL_OPEN_COOLDOWN")' in protection, "restart_guard_missing")
req('.remove("fail_open_until")' in protection, "success_rearm_missing")
req('.put("fail_open_active", failOpenUntil > now)' in protection, "observability_missing")
print("VPN_FAIL_OPEN_R21=PASS internet_priority=true bounded_retry=true sticky_restart=false")
