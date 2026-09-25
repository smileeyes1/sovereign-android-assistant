from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
MAIN=(APP/"MainActivity.kt").read_text(encoding="utf-8")
UX=(APP/"HakimProductUx.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))
CONTRACT=(ROOT/"governance/SELLABLE_PRODUCT_CONTRACT.md").read_text(encoding="utf-8")
PRIVACY=(ROOT/"governance/PRIVACY_DATA_MAP.md").read_text(encoding="utf-8")
THREAT=(ROOT/"governance/THREAT_MODEL.md").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("SELLABLE_PRODUCT_CANDIDATE=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))>=20202,"version")
req("3.0.2-product-v1-candidate" in BUILD,"version_name")

# Primary surface must be user-facing, not an engineering console.
req('actionButton("المتصفح")' not in CENTER,"browser_button_visible")
req('actionButton("إدارة")' not in CENTER,"engineering_manage_label")
req('actionButton("الإعدادات")' in CENTER,"settings_missing")
req('visibility = View.GONE' in CENTER and 'تفاصيل تشغيل داخلية' in CENTER,"diagnostics_visible")
req("HakimProductUx.publicError" in CENTER,"technical_errors_not_sanitized")
req("يعمل على طلبك…" in CENTER,"product_status_missing")

# Least privilege: no blanket startup prompt.
req('uiHandler.postDelayed({ requestUsefulPermissions() }' not in MAIN,"blanket_permissions_on_start")

# Preserve completed core paths.
for token in ["executeLocalArtifact(text)","executeSilentBrowser(text, directed.instruction)","retryDirectOrBlock"]:
    req(token in CENTER,"core_path:"+token)

# Product UX and platform hardening.
for token in ["publicStatus","publicError","exposeDiagnosticsInPrimaryUi"]:
    req(token in UX,"ux:"+token)
req('android:allowBackup="false"' in MANIFEST,"backup_enabled")
req('android:usesCleartextTraffic="false"' in MANIFEST,"cleartext_enabled")
req("Android Keystore" in CONTRACT or "Android Keystore" in PRIVACY,"keystore_policy_missing")
req(len(PRIVACY)>300,"privacy_map_too_small")
req(len(THREAT)>300,"threat_model_too_small")

for key in [
 "primary_ui_productized","browser_hidden_from_primary_ui",
 "diagnostics_hidden_from_primary_ui","permission_on_demand",
 "privacy_data_map_present","threat_model_present"
]:
    req(STATE.get(key) is True,"state:"+key)
req(STATE.get("raw_html_hidden") is True,"raw_html_not_hidden")
req(STATE.get("artifact_stream_hidden") is True,"artifact_stream_not_hidden")
req(STATE.get("generic_pdf_fallback") is True,"generic_pdf_fallback_missing")
req(STATE.get("sellable") is False,"premature_sellable")

print("SELLABLE_PRODUCT_CANDIDATE=PASS ui=productized least_privilege=true diagnostics=hidden sellable=false")
