from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")
LOOP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("INTENT_DIRECTOR_20101=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20101, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for needed in [
    "MAX_INTERNAL_REVISIONS = 3",
    "صغ داخليًا لنفسك أفضل وأدق وأكفأ أمر عمل",
    "لا تعرض سلسلة التفكير",
    "لا تكتفِ بالخطة أو الاقتراح",
    "أثر تنفيذي قابل للتحقق",
    "ناتج نهائي قابل للاستخدام",
    "أصغر تدخل لازم فقط",
]:
    req(needed in DIRECTOR, "director:" + needed)

req("HakimIntentDirector.build(context, raw).instruction" in LOOP, "executive_not_using_director")
req("HakimIntentDirector.build(this, text, attachments.size)" in CENTER, "ui_not_deriving_contract")
req("directed.acceptance" in CENTER, "acceptance_not_intent_specific")
req("صياغة أمر تنفيذي أعلى للمحرك" in CENTER, "visible_planning_stage_missing")
req("HakimExecutiveLoop.providerInstruction(context, prompt)" in ROUTER, "router_bypasses_director")
req("HakimAttachmentGateway.buildShareIntent(context, prompt, attachments)" not in ROUTER, "raw_prompt_leak")

print("INTENT_DIRECTOR_GATE=PASS candidate>=20101 self_direction=private revisions=3")
