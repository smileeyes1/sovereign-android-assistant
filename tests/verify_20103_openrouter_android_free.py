from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
PKCE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterPkceAuth.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterDirectEngine.kt").read_text(encoding="utf-8")
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("OPENROUTER_ANDROID_FREE_20103=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20103, "version")
req("versionName '2.1.03-openrouter-android-pkce'" in BUILD, "version_name")

for needed in [
    'ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))',
    '"code_challenge_method", "S256"',
    '"state", state',
    'https://openrouter.ai/api/v1/auth/keys',
    'HakimSecretStore.put(activity, SECRET_OPENROUTER_KEY, key)',
    'Intent(Intent.ACTION_VIEW, authUri)',
]:
    req(needed in PKCE, "pkce:" + needed)

for needed in [
    'DEFAULT_MODEL = "openrouter/free"',
    'https://openrouter.ai/api/v1/chat/completions',
    '.put("stream", true)',
    '"Authorization", "Bearer " + key',
    '"paid_fallback=false"',
    'Capability.GENERAL_CHAT',
    'Capability.IMAGES',
]:
    req(needed in ENGINE, "engine:" + needed)

req("OpenRouterDirectEngine(context)" in REGISTRY, "registry_missing_openrouter")
req(REGISTRY.index("OpenRouterDirectEngine(context)") < REGISTRY.index("GeminiDirectEngine(context)"), "openrouter_not_preferred")
req('button("ربط OpenRouter المجاني")' in HOME, "android_link_button_missing")
req('button("اختبار OpenRouter المجاني")' in HOME, "android_test_button_missing")
req('android:usesCleartextTraffic="false"' in MANIFEST, "global_cleartext_must_stay_disabled")

# Regression for the exact field failure: no hard-coded desktop loopback port or callback URL.
req("46041" not in PKCE + HOME, "desktop_fixed_port_returned")
req("ERR_CLEARTEXT_NOT_PERMITTED" not in HOME, "webview_error_workaround_leaked_to_ui")

print("OPENROUTER_ANDROID_FREE_20103=PASS pkce=loopback_ephemeral browser=external model=openrouter/free paid_fallback=false")
