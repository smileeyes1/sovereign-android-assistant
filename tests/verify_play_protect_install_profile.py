from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

# Google Play Protect Enhanced Fraud Protection يركز عند sideload على صلاحيات
# SMS/الإشعارات/Accessibility التي يكثر إساءة استخدامها في الاحتيال المالي.
for forbidden in [
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_SMS",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    'android:name=".HakimNotificationListener"',
    'android:name=".HakimAccessibilityService"',
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
]:
    require(forbidden not in manifest, f"P0: ملف التثبيت يعيد إعلان قدرة حساسة محظورة في ملف Play Protect الآمن: {forbidden}")

require('android.permission.INTERNET' in manifest, "P0: الإنترنت مفقود من حكيم")
require('android.permission.RECORD_AUDIO' in manifest, "P0: الميكروفون اللازم للواجهة الصوتية مفقود")
require('android:name=".MainActivity"' in manifest, "P0: متصفح حكيم مفقود")
require('android:name=".HakimService"' in manifest, "P0: خدمة حكيم الخلفية الآمنة مفقودة")
require('android:usesCleartextTraffic="false"' in manifest, "P0: حماية HTTPS/cleartext انحدرت")

print("PLAY_PROTECT_SAFE_INSTALL_PROFILE=PASS")
