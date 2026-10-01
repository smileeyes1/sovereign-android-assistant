from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
router=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimRouterAuthActivity.kt").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("F8040_SOURCE_R19=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",build)
req(m is not None and int(m.group(1))>=20329,"version")
req("f8040-source-inference-r19" in build,"lineage")
for token in (
    "function selectedByUi(e)",
    "aria-selected",
    "data-selected",
    "data-state",
    "function dnsControlState()",
    "if(editable===controls.length) return '0'",
    "if(locked===controls.length) return '1'",
    "readSource(d,src0,src1,srcDirect,hidden1,hidden2)",
):
    req(token in router,"missing:"+token)
req("btn.disabled=false" not in router and "removeAttribute('disabled')" not in router,
    "force_enable_forbidden")
req("GATE_SOURCE" in router and "recordLocalWebViewBaseline" in router,
    "baseline_gate_missing")
req("rollbackDns(target)" in router and "recordLocalWebViewRollback" in router,
    "rollback_missing")
req("wanted.click()" in router,"native_source_selection_missing")
req('private const val ROUTER_HOST = "192.168.1.1"' in router,"router_scope_changed")
req(".addJavascriptInterface(" not in router,"javascript_interface_forbidden")
print("F8040_SOURCE_R19=PASS source_ui_inference=true reversible=true router_pinned=true")
