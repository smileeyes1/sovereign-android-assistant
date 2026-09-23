from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MAIN = (ROOT / "app/src/main/java/ps/hakim/phoneagent/MainActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("ANDROID_NATIVE_20095=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20095, "version")
req("versionName '2.0.95-android-native-only'" in BUILD, "version_name")
req("رمز اقتران الجسر التنفيذي" in MAIN, "bridge_pairing_label_missing")
req("ليس رمز إضافة ChatGPT" in MAIN, "pairing_ambiguity_not_closed")
req("إضافة ChatGPT هذه لسطح المكتب فقط" in MAIN, "desktop_only_warning_missing")
req("إضافة MCP الخاصة بـChatGPT ليست قناة أندرويد" in MAIN, "android_mcp_boundary_missing")
req("chatgpt.com/plugins/" in MAIN, "desktop_plugin_page_detection_missing")
req("LOCAL_RESPONSE" in ROUTER, "local_first_missing")
req("com.openai.chatgpt" in ROUTER, "chatgpt_app_fallback_missing")
req("com.google.android.apps.bard" in ROUTER, "gemini_app_fallback_missing")

# Known failure: generic pairing text must not be the only Android status anymore.
req('!paired -> "الحالة: يحتاج رمز الاقتران"' not in MAIN, "legacy_pairing_ambiguity_returned")

print("ANDROID_NATIVE_20095=PASS mobile=local+provider-app+browser desktop_plugin=excluded")
