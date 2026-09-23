from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bio = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimHumanBiology.kt").read_text(encoding="utf-8")
intent = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")
self_check = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("HUMAN_BIOLOGY_GATE=FAIL reason=" + reason)

for token in [
    "الدماغ والجهاز العصبي", "القلب والدورة الدموية", "التنفس والرئتان",
    "الدم والمناعة", "الغدد والهرمونات والاستقلاب", "الكلى والمسالك",
    "العضلات والعظام والمفاصل", "الصحة الإنجابية", "الوراثة والخلايا والأنسجة"
]:
    req(token in bio, "coverage:" + token)

req("autonomousClinicalAction = false" in bio, "clinical_autonomy_must_be_false")
req('"autonomous_clinical_action", false' in bio, "status_clinical_autonomy")
req("DIRECT_INTERVENTION" in bio, "direct_intervention_mode")
req("بوابة إنسان مؤهل" in bio, "qualified_human_gate")
req("HakimHumanBiology.matches(effectiveText)" in intent, "intent_route_missing")
req('"human_biology" -> "أحياء/جسم الإنسان"' in intent, "intent_label_missing")
req("HakimHumanBiology.governedContext(context, raw)" in intent, "governed_context_missing")
req("HakimHumanBiology.status(context)" in self_check, "self_check_missing")
req('.put("human_biology", humanBiology)' in self_check, "self_check_report_missing")

# Known failure: a mutant that enables autonomous clinical action must be detectable.
mutant = bio.replace("autonomousClinicalAction = false", "autonomousClinicalAction = true", 1)
req("autonomousClinicalAction = false" not in mutant, "known_failure_not_detected")

print("HUMAN_BIOLOGY_GATE=PASS")
