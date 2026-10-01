from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
router = (APP / "HakimRouterAuthActivity.kt").read_text(encoding="utf-8")
guardian = (APP / "HakimNetworkGuardian.kt").read_text(encoding="utf-8")
tasks = (APP / "HakimTaskManager.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("WEBVIEW_DNS_R13=FAIL reason=" + reason)

req("versionCode 20325" in build and "webview-dns-adaptive-r13-apply-gate-r14-webview-baseline-r15" in build, "version")
req("iframe,frame" in router and "contentDocument" in router, "same_origin_frame_probe_missing")
req("location.hostname" in router, "router_host_fallback_missing")
req("isIpv4OrBlank" in router and "splitIpv4OrBlank" in router, "blank_baseline_support_missing")
req("recordLocalWebViewProbe" in router and "markLocalWebViewProbeState" in router, "probe_telemetry_missing")
req("Btn_apply_DHCPBasicCfg" in router and "sub_DNSServer1" in router and "sub_DNSServer2" in router,
    "dhcp_dns_fields_missing")
req("webview_probe_state" in guardian and "webview_probe_frame_count" in guardian, "guardian_probe_state_missing")
req("webview_probe_has_apply" in guardian and "webview_probe_has_dns" in guardian and
    "webview_probe_has_source" in guardian and "webview_probe_has_dhcp" in guardian,
    "bounded_structure_evidence_missing")
req("fun recordLocalWebViewProbe(" in guardian and "fun markLocalWebViewProbeState(" in guardian,
    "probe_record_contract_missing")
req("webviewBlocked" in tasks and "TIMEOUT" in tasks and "GATE_BLOCKED" in tasks,
    "task_manager_blocker_missing")
req("addJavascriptInterface" not in router, "javascript_interface_forbidden")
req("evaluateJavascript" in router, "local_webview_execution_missing")
req('android:name=".HakimRouterAuthActivity"' in manifest and 'android:exported="false"' in manifest,
    "router_activity_not_private")
req("FAMILY_DNS_1 = \"185.228.168.168\"" in router and
    "FAMILY_DNS_2 = \"185.228.169.168\"" in router,
    "family_dns_changed")
req("recordLocalWebViewBaseline" in router and "recordLocalWebViewRollback" in router,
    "rollback_contract_missing")
req("family_dns_configured" in guardian and "family_resolver_verified" in guardian,
    "verification_contract_missing")
# Telemetry must be structural only: no DOM text/value/cookies are added to public status fields.
status = guardian.split("fun status(context: Context)", 1)[1].split("fun recordLocalWebViewProbe", 1)[0]
for forbidden in ("webview_dom", "webview_cookie", "webview_password", "webview_field_value"):
    req(forbidden not in status, "telemetry_leak:" + forbidden)

# R14: a naturally disabled Apply button must not be force-enabled or treated as a pre-edit blocker.
req('if (host != ROUTER_HOST || !baselineValuesValid || disabled)' not in router,
    "initial_disabled_button_still_blocks")
req('if(!btn || btn.disabled ||' not in router,
    "apply_button_checked_before_change_events")
req("apply_disabled_after_change" in router and "APPLY_GATE_STILL_DISABLED" in router,
    "post_change_apply_gate_missing")
for forbidden in ("btn.disabled=false", "removeAttribute('disabled')", 'removeAttribute("disabled")'):
    req(forbidden not in router, "force_enable_forbidden:" + forbidden)
req("target.loadUrl(MODERN_VIEW_URL)" in router, "safe_discard_reload_missing")

print("WEBVIEW_DNS_R14=PASS frames=true adaptive=true blank_baseline=true rollback=true apply_gate=ui_native private=true")
