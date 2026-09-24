from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
POLICY=(APP/"HakimEnterprisePolicy.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
XML=(ROOT/"app/src/main/res/xml/app_restrictions.xml").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("ENTERPRISE_POLICY_GATE=FAIL reason="+reason)

for token in [
    "RestrictionsManager",
    "applicationRestrictions",
    "allow_external_ai",
    "allow_web",
    "allow_attachments",
    "allow_voice",
    "allow_diagnostics",
]:
    req(token in POLICY or token in XML,"policy:"+token)

req('android.content.APP_RESTRICTIONS' in MANIFEST,"managed_config_manifest")
req("@xml/app_restrictions" in MANIFEST,"managed_config_resource")
req("POLICY_BLOCKED" in ROUTER,"policy_channel_missing")
for capability in ['"attachments"','"web"','"external_ai"']:
    req("HakimEnterprisePolicy.blockReason" in ROUTER and capability in ROUTER,"router_policy:"+capability)
req('"voice"' in CENTER and "HakimEnterprisePolicy.blockReason" in CENTER,"voice_policy")
req("هذا الجهاز مُدار بسياسة المؤسسة." in HOME,"managed_status_missing")
req("تأسيس ADB المحلي" not in HOME,"adb_leaked")
req("مفتاح OpenRouter" not in HOME and "Gemini API" not in HOME,"secret_provider_ui_leaked")

print("ENTERPRISE_POLICY_GATE=PASS managed_config=true least_privilege=true provider_jargon=false")
