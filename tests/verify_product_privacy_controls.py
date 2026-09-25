from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
AUDIT=(APP/"HakimAuditTrail.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("PRODUCT_PRIVACY_GATE=FAIL reason="+reason)

for token in ["MAX_LINES = 200","event","success","fun clear","filesDir"]:
    req(token in AUDIT,"audit:"+token)
for forbidden in ["prompt","message","attachment","secret","auth_key","api_key"]:
    req(('.put("'+forbidden+'"') not in AUDIT,"audit_sensitive_field:"+forbidden)

req("HakimAuditTrail.record(this, route, success)" in CENTER,"audit_not_connected")
req("مسح سجل المحادثة المحلي" in HOME,"local_clear_missing")
req('getSharedPreferences("hakim_conversation"' in HOME and ".edit().clear().apply()" in HOME,"conversation_clear_missing")
req("HakimAuditTrail.clear(this)" in HOME,"audit_clear_missing")
req("إلغاء ربط خدمات الذكاء" in HOME,"disconnect_missing")
req("HakimSecretStore.remove" in HOME,"secret_removal_missing")
req('android:allowBackup="false"' in MANIFEST,"backup_must_remain_off")

print("PRODUCT_PRIVACY_GATE=PASS audit=minimized deletion=local disconnect=true")
