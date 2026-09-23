from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
OAUTH = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterOAuthManager.kt").read_text(encoding="utf-8")
RETURN = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterOAuthReturnActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
OPENROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterFreeEngine.kt").read_text(encoding="utf-8")
SECRETS = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSecretStore.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("FREE_OAUTH_NO_HANDOFF_20105=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20105, "version")
req("versionName '2.1.05-free-oauth-no-handoff'" in BUILD, "version_name")

for needed in [
    "https://openrouter.ai/auth",
    "https://openrouter.ai/api/v1/auth/keys",
    "code_challenge_method",
    '"S256"',
    '"state"',
    "127.0.0.1",
    "ServerSocket(0",
    "SHA-256",
    "SecureRandom",
    "HakimSecretStore.put",
]:
    req(needed in OAUTH, "oauth:" + needed)

req("AndroidKeyStore" in SECRETS, "keystore_missing")
req('android:scheme="hakim" android:host="openrouter-connected"' in MANIFEST, "oauth_return_deeplink_missing")
req("resume_after_free_oauth" in RETURN and "FLAG_ACTIVITY_CLEAR_TOP" in RETURN, "oauth_resume_missing")
req("فعّل الذكاء المجاني — مرة واحدة" in HOME, "one_click_setup_missing")

for needed in [
    "FREE_ENGINE_SETUP",
    "HakimFreePolicy.freeOnly(context)",
    "يتوقف حكيم مغلقًا بدل فتح تطبيق نموذج خارجي",
]:
    req(needed in ROUTER, "router:" + needed)

free_gate = ROUTER.index("if (HakimFreePolicy.freeOnly(context))")
provider_scan = ROUTER.index("val installed = providers.filter")
req(free_gate < provider_scan, "free_gate_after_provider_handoff")

req("beginFreeEngineSetup" in CENTER, "setup_handler_missing")
req("OpenRouterOAuthManager.start(this, pendingPrompt = text)" in CENTER, "oauth_not_started_from_pending_goal")
req("executeBestRoute(pending, appendUserMessage = false)" in CENTER, "automatic_resume_missing")
req("لن تُرسل المهمة تلقائيًا إلى تطبيق ChatGPT" in CENTER, "user_boundary_missing")

req('private const val MODEL = "openrouter/free"' in OPENROUTER, "paid_model_risk")
req("readAttachmentBounded" in OPENROUTER, "bounded_attachment_read_missing")

print("FREE_OAUTH_NO_HANDOFF_20105=PASS oauth=pkce_s256 free_only=fail_closed auto_resume=true external_chat=not_automatic")
