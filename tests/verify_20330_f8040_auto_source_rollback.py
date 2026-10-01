from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
router=(APP/"HakimRouterAuthActivity.kt").read_text(encoding="utf-8")
guardian=(APP/"HakimNetworkGuardian.kt").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("F8040_AUTO_SOURCE_R20=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",build)
req(m is not None and int(m.group(1))>=20330,"version")
req("f8040-auto-source-rollback-r20" in build,"lineage")
req('if (source == "1" && !isIpv4OrBlank(dns1)) "" else dns1' in router,"auto_dns1_normalization_missing")
req('if (source == "1" && !isIpv4OrBlank(dns2)) "" else dns2' in router,"auto_dns2_normalization_missing")
req('source == "0" && !isIpv4OrBlank(dns1) -> "GATE_DNS1"' in router,"manual_dns1_gate_missing")
req('source == "0" && !isIpv4OrBlank(dns2) -> "GATE_DNS2"' in router,"manual_dns2_gate_missing")
req('sourceOnlyAutoRollback = rollback && source == "1"' in router,"source_only_rollback_missing")
req('dns1ok=sourceOnlyAutoRollback,dns2ok=sourceOnlyAutoRollback' in router,"rollback_must_not_touch_dns_values")
req('if (baselineSource == "1")' in guardian and 'source == baselineSource' in guardian,
    "semantic_rollback_verification_missing")
req('if (source == "0" && dns1.isBlank()) return false' in guardian,"manual_empty_baseline_guard_missing")
req("removeAttribute('disabled')" not in router and "btn.disabled=false" not in router,
    "force_enable_forbidden")
req(".addJavascriptInterface(" not in router,"javascript_interface_forbidden")
print("F8040_AUTO_SOURCE_R20=PASS auto_source_semantic_baseline=true source_only_rollback=true manual_fail_closed=true")
