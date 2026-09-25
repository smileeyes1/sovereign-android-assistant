from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
AUTH = (APP / "HakimGoogleWorkspaceAuthorization.kt").read_text(encoding="utf-8")
DRIVE = (APP / "HakimGoogleDriveBridge.kt").read_text(encoding="utf-8")
INTENT = (APP / "HakimGoogleWorkspaceIntent.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
SELLABLE = json.loads((ROOT / "governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))
CONTRACT = (ROOT / "governance/GOOGLE_WORKSPACE_USER_BRIDGE.md").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("GOOGLE_WORKSPACE_USER_BRIDGE=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m and int(m.group(1)) == 20204, "version")
req("3.0.4-google-workspace-user-bridge-v1-candidate" in BUILD, "version_name")
req("com.google.android.gms:play-services-auth:22.0.0" in BUILD, "auth_dependency")

for token in [
    "AuthorizationRequest.builder()",
    "Identity.getAuthorizationClient(activity)",
    "DRIVE_FILE_SCOPE",
    "https://www.googleapis.com/auth/drive.file",
    "startIntentSenderForResult",
    "getAuthorizationResultFromIntent",
    "ClearTokenRequest.builder().setToken(token)",
]:
    req(token in AUTH, "auth:" + token)

req('@Volatile private var accessToken: String? = null' in AUTH, "token_not_memory_only")
req('putString("accessToken"' not in AUTH, "token_sharedprefs")
req('HakimSecretStore.put' not in AUTH, "token_secret_store")

for token in [
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
    'header("Authorization", "Bearer " + accessToken)',
    "MultipartBody",
    "application/vnd.google-apps.document",
    "application/vnd.google-apps.presentation",
    "application/vnd.google-apps.spreadsheet",
]:
    req(token in (DRIVE + INTENT), "drive:" + token)

for token in ["Google Docs", "Google Slides", "Google Sheets", "جوجل درايف"]:
    req(token.lower() in INTENT.lower(), "intent:" + token)

req("GOOGLE_WORKSPACE" in ROUTER, "router_channel")
req("HakimGoogleWorkspaceIntent.canHandle(context, q)" in ROUTER, "router_intent")
req(ROUTER.index("HakimGoogleWorkspaceIntent.canHandle(context, q)") < ROUTER.index("HakimMultiFormatArtifactFactory.canHandle(context, q)"), "workspace_not_before_local_default")
req("executeGoogleWorkspace(text)" in CENTER, "center_execution")
req("HakimGoogleWorkspaceAuthorization.handleActivityResult" in CENTER, "center_resolution")
req("HakimGoogleDriveBridge.upload" in CENTER, "center_upload")
req("ربط Google Workspace" in HOME, "settings_link")
req("HakimGoogleDriveBridge.ping" in HOME, "settings_ping")
req("python3 tests/verify_20204_google_workspace_user_bridge.py" in WORKFLOW, "ci_gate")

ws = STATE.get("google_workspace", {})
req(STATE["android"]["candidate"]["version_code"] == 20204, "state_version")
req(STATE["android"]["candidate"]["field_verified"] is False, "state_field")
req(STATE["android"]["candidate"]["promoted"] is False, "state_promoted")
req(ws.get("source_integrated") is True, "workspace_source")
req(ws.get("scope") == "https://www.googleapis.com/auth/drive.file", "workspace_scope")
req(ws.get("access_token_persisted") is False, "token_persisted")
req(ws.get("embedded_webview") is False, "embedded_webview")
req(ws.get("android_package") == "ps.hakim.stable", "package")
req(ws.get("d1_sha1") == "17:5D:2F:49:B1:F9:4C:B6:94:D1:79:92:7A:B8:EA:54:FC:68:4F:8C", "d1_sha1")
req(ws.get("oauth_android_client_registered") is False, "premature_oauth_claim")
req(ws.get("drive_api_enabled") is False, "premature_drive_claim")
req(ws.get("field_verified") is False, "premature_field_claim")
req(PROMOTION["candidate_version"] == 20204 and PROMOTION["promoted"] is False, "promotion")
req(SELLABLE["candidate_version"] == 20204 and SELLABLE["sellable"] is False, "sellable")

req("17:5D:2F:49:B1:F9:4C:B6:94:D1:79:92:7A:B8:EA:54:FC:68:4F:8C" in CONTRACT, "contract_sha1")
req("oauth_android_client_registered=false" in CONTRACT, "contract_no_fake_cloud_setup")

print("GOOGLE_WORKSPACE_USER_BRIDGE=PASS version=20204 scope=drive.file cloud_setup=false field=false")
