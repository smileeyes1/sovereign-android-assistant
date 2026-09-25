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
req(m is not None and int(m.group(1)) >= 20105, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

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
req("ربط خدمة الذكاء" in HOME or "فعّل الذكاء المجاني — مرة واحدة" in HOME, "one_click_setup_missing")

for needed in [
    "FREE_ENGINE_SETUP",
    "HakimFreePolicy.freeOnly(context)",
    "يتوقف حكيم مغلقًا بدل فتح تطبيق نموذج خارجي",
]:
    req(needed in ROUTER, "router:" + needed)

provider_scan = ROUTER.index("val installed = providers.filter")
attachment_handoff = ROUTER.index("if (attachments.isNotEmpty())", provider_scan)
free_gate = ROUTER.index("if (HakimFreePolicy.freeOnly(context))", attachment_handoff)
normal_provider_handoff = ROUTER.index("val preferredInstalled", free_gate)

# Narrow exception: an explicitly selected attachment may use an already-installed
# provider app under the user's own account before FREE_ENGINE_SETUP. Ordinary
# text chat must still hit the free-only fail-closed gate before provider handoff.
req(provider_scan < attachment_handoff < free_gate < normal_provider_handoff, "free_only_boundary_broadened")
attachment_block = ROUTER[attachment_handoff:free_gate]
req("Channel.PROVIDER_APP" in attachment_block, "attachment_account_handoff_missing")
req("attachments.isNotEmpty()" in attachment_block, "attachment_exception_not_scoped")
normal_block = ROUTER[free_gate:normal_provider_handoff]
req("FREE_ENGINE_SETUP" in normal_block, "ordinary_chat_free_gate_missing")

req("beginFreeEngineSetup" in CENTER, "setup_handler_missing")
req("OpenRouterOAuthManager.start(this, pendingPrompt = text)" in CENTER, "oauth_not_started_from_pending_goal")
req("executeBestRoute(pending, appendUserMessage = false)" in CENTER, "automatic_resume_missing")
req(
    "بعد موافقتك سيعود العمل إلى حكيم ويكمل طلبك هنا." in CENTER
    or "لن تُرسل المهمة تلقائيًا إلى تطبيق ChatGPT" in CENTER,
    "user_boundary_missing"
)
setup_start = CENTER.index("private fun beginFreeEngineSetup")
setup_end = CENTER.index("private fun executeDirectModel", setup_start)
setup_block = CENTER[setup_start:setup_end]
req("startActivity(" not in setup_block, "automatic_provider_handoff")

req('private const val MODEL = "openrouter/free"' in OPENROUTER, "paid_model_risk")
req("readAttachmentBounded" in OPENROUTER, "bounded_attachment_read_missing")

print("FREE_OAUTH_NO_HANDOFF_GATE=PASS candidate>=20105 oauth=pkce_s256 free_only=fail_closed attachment_exception=scoped auto_resume=true")
