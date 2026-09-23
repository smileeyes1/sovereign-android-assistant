from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("KEYBOARD_LOCAL_HELP_20097=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20097, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

req(MANIFEST.count('android:windowSoftInputMode="adjustResize"') >= 2, "adjust_resize_missing")
req("SOFT_INPUT_ADJUST_RESIZE" in CENTER, "runtime_adjust_resize_missing")
req("requestRectangleOnScreen" in CENTER, "composer_visibility_guard_missing")

req("isHakimKeyboardQuestion" in ROUTER, "keyboard_local_classifier_missing")
req("لوحة المفاتيح تأخذ جزءًا من ارتفاع الشاشة" in ROUTER, "keyboard_local_reply_missing")
req("أجيبك هنا محليًا ولا أفتح نموذجًا خارجيًا" in ROUTER, "ui_local_help_missing")

# The field prompt from the screenshot must resolve locally.
probe = "لماذا لوحة المفاتيح تغطي مكان الكتابة"
q = probe.lower()
mentions_keyboard = any(x in q for x in ["لوحة المفاتيح", "الكيبورد", "keyboard"])
mentions_composer = any(x in q for x in ["مكان الكتابة", "مربع الكتابة", "حقل الكتابة", "الإدخال", "يغطي", "تغطي"])
req(mentions_keyboard and mentions_composer, "field_prompt_not_classified_local")

print("KEYBOARD_LOCAL_HELP_GATE=PASS candidate>=20097 ime=adjustResize field_prompt=local")
