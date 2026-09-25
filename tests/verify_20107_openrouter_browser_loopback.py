from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
OAUTH = (ROOT / "app/src/main/java/ps/hakim/phoneagent/OpenRouterOAuthManager.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("OPENROUTER_BROWSER_LOOPBACK_20107=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20107, "version")
req(re.search(r"versionName\s+'[^']+'", BUILD) is not None, "version_name")

# Official OpenRouter localhost PKCE flow stays intact in 20107 and later candidates.
for needed in [
    "ServerSocket(0",
    'InetAddress.getByName("127.0.0.1")',
    '"http://127.0.0.1:"',
    "code_challenge_method",
    '"S256"',
    "state",
]:
    req(needed in OAUTH, "oauth:" + needed)

# The authorization page must be launched in a browser process, not Hakim/WebView.
for needed in [
    "launchExternalBrowser",
    "PackageManager.MATCH_DEFAULT_ONLY",
    "it.activityInfo.packageName != activity.packageName",
    "Intent.CATEGORY_APP_BROWSER",
]:
    req(needed in OAUTH, "external_browser:" + needed)

# Security must remain strict: never solve this by globally permitting cleartext in Hakim.
req('android:usesCleartextTraffic="false"' in MANIFEST, "cleartext_security_weakened")

# Known field failure: launcher claiming all http/https can recapture the loopback URL in Hakim.
req('<data android:scheme="http" />' not in MANIFEST, "generic_http_claim_returned")
req('<data android:scheme="https" />' not in MANIFEST, "generic_https_claim_returned")

# The custom post-callback deep link remains the only browser -> Hakim return.
req('android:scheme="hakim" android:host="openrouter-connected"' in MANIFEST, "hakim_return_deeplink_missing")

print("OPENROUTER_BROWSER_LOOPBACK_20107=PASS auth=external_browser callback=loopback security=no_cleartext_downgrade")
