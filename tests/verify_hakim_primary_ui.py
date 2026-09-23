from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise SystemExit("PRIMARY_UI_GATE=FAIL reason=" + reason)


require(MANIFEST.count("android.intent.category.LAUNCHER") == 1, "launcher_count")
launcher_block = re.search(
    r'<activity\s+android:name="\.CommandCenterActivity"[\s\S]*?<action android:name="android.intent.action.MAIN" />[\s\S]*?<category android:name="android.intent.category.LAUNCHER" />',
    MANIFEST,
)
require(launcher_block is not None, "command_center_not_launcher")

for text in [
    "اكتب رسالتك إلى حكيم",
    "إرفاق",
    "أنجز",
    "صوت",
    "إدارة",
]:
    require(text in CENTER, "missing_primary_control:" + text)

require("HakimModelToolRouter.decide" in CENTER, "router_not_connected")
require("HakimAttachmentGateway.pickerIntent" in CENTER, "attachments_not_connected")
require("RecognizerIntent.ACTION_RECOGNIZE_SPEECH" in CENTER, "voice_not_connected")
require("UnifiedHomeActivity::class.java" in CENTER, "device_management_unreachable")

# Provider selection stays behind the router; the primary interface must not
# expose one button per vendor.
for forbidden in [
    'actionButton("شات جي بي تي"',
    'actionButton("جيميني"',
    'actionButton("كلود"',
    'actionButton("ديب سيك"',
]:
    require(forbidden not in CENTER, "provider_button_leaked:" + forbidden)

print("PRIMARY_UI_GATE=PASS launcher=CommandCenterActivity")
