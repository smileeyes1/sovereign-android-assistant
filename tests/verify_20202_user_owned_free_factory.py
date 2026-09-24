from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

POLICY = (APP / "HakimUserResourcePolicy.kt").read_text(encoding="utf-8")
REGISTRY = (APP / "HakimEngineRegistry.kt").read_text(encoding="utf-8")
OAUTH = (APP / "OpenRouterOAuthManager.kt").read_text(encoding="utf-8")
FREE = (APP / "HakimFreePolicy.kt").read_text(encoding="utf-8")
GEMINI = (APP / "GeminiDirectEngine.kt").read_text(encoding="utf-8")
HOME = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")
FORMATS = (APP / "HakimArtifactFormatRegistry.kt").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
SELLABLE = json.loads((ROOT / "governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))
CONTRACT = (ROOT / "governance/USER_OWNED_FREE_FACTORY_CONTRACT.md").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("USER_OWNED_FREE_FACTORY=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m and int(m.group(1)) == 20202, "version")
req("3.0.2-user-owned-free-factory-v1-candidate" in BUILD, "version_name")

for token in [
    "MODE_USER_OWNED_FREE_ONLY",
    "centralBillingAllowed(): Boolean = false",
    "automaticPaidUpgradeAllowed(): Boolean = false",
    "fun markUserOwned",
    "fun isUserOwned",
    "HakimFreePolicy.setFreeOnly(context, true)",
]:
    req(token in POLICY, "policy:" + token)

req("HakimUserResourcePolicy.allows(context, OpenRouterFreeEngine.ID)" in REGISTRY, "openrouter_owned_gate")
req("HakimUserResourcePolicy.allows(context, GeminiDirectEngine.ID)" in REGISTRY, "gemini_owned_gate")
req("HakimUserResourcePolicy.markUserOwned(activity, OpenRouterFreeEngine.ID)" in OAUTH, "oauth_ownership")
req("HakimUserResourcePolicy.enforce(this)" in HOME, "home_enforce")
req("HakimUserResourcePolicy.clear(this, OpenRouterFreeEngine.ID)" in HOME, "disconnect_openrouter")
req("HakimUserResourcePolicy.clear(this, GeminiDirectEngine.ID)" in HOME, "disconnect_gemini")
req("getBoolean(KEY_FREE_ONLY, true)" in FREE, "free_default")
req('const val ID = "gemini-direct"' in GEMINI, "gemini_id")

for fmt in ["PDF", "HTML", "DOCX", "PPTX", "XLSX", "PNG", "GOOGLE_DOC", "GOOGLE_SLIDES", "GOOGLE_SHEET"]:
    req(fmt in FORMATS, "format:" + fmt)
req("localDeterministicNow: Set<Format> = setOf(Format.PDF)" in FORMATS, "no_fake_local_formats")

req("python3 tests/verify_20202_user_owned_free_factory.py" in WORKFLOW, "ci_gate")
req(STATE["android"]["candidate"]["version_code"] == 20202, "state_candidate")
req(STATE["android"]["candidate"]["field_verified"] is False, "state_field")
req(STATE["android"]["candidate"]["promoted"] is False, "state_promoted")
req(PROMOTION["candidate_version"] == 20202 and PROMOTION["promoted"] is False, "promotion")
req(SELLABLE["candidate_version"] == 20202 and SELLABLE["sellable"] is False, "sellable")
req("لا مفتاح ذكاء مركزي مشترك" in CONTRACT, "contract_central_key")
req("لا ترقية مدفوعة تلقائيًا" in CONTRACT, "contract_no_paid_upgrade")

# Static secret guard: repository code may check token prefixes but must not embed a full provider credential.
combined = "\n".join([POLICY, REGISTRY, OAUTH, FREE, GEMINI, HOME])
req(re.search(r"AIza[0-9A-Za-z_-]{25,}", combined) is None, "embedded_google_key")
req(re.search(r"sk-or-v1-[0-9A-Za-z_-]{20,}", combined) is None, "embedded_openrouter_key")

print("USER_OWNED_FREE_FACTORY=PASS version=20202 central_billing=false paid_upgrade=false formats_governed=true")
