from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CODE=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimQuranicGovernance.kt").read_text(encoding="utf-8")
DOC=(ROOT/"governance/QURANIC_VALUES_MATRIX.md").read_text(encoding="utf-8")
CONSTITUTION=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")
DIRECTOR=(ROOT/"app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")

def req(cond, reason):
    if not cond:
        raise SystemExit("QURANIC_GOVERNANCE_GATE=FAIL reason="+reason)

for token in [
    "TRUTHFULNESS","VERIFY_BEFORE_ACT","TRUST","JUSTICE_IHSAN",
    "COVENANT","PRIVACY","CONSULTATION","NO_DIVINE_TECH_CLAIM"
]:
    req(token in CODE, "missing_rule:"+token)

for ref in [
    "الأحزاب 33:70","الحجرات 49:6","الإسراء 17:36","النساء 4:58",
    "النحل 16:90","المائدة 5:1","الحجرات 49:12","الشورى 42:38",
    "الأعراف 7:180","الشورى 42:11","الإخلاص 112:1-4"
]:
    req(ref in CODE or ref in DOC, "missing_reference:"+ref)

for boundary in [
    "لا تحوّل الأسماء أو الصفات أو حروف القرآن",
    "اسم «حكيم» وصف لغوي للبرنامج",
    "الوسائل الدنيوية",
]:
    req(boundary in CODE, "boundary:"+boundary)

req("HakimQuranicGovernance.install(this)" in CONSTITUTION or "HakimQuranicGovernance.install(context)" in CONSTITUTION, "constitution_not_installing_quranic_governance")
req("HakimQuranicGovernance.instruction()" in DIRECTOR, "director_not_receiving_quranic_governance")

for forbidden in ["حساب الجمل يقرر", "الأسماء تضمن النجاح", "الحروف تتنبأ", "البركة آلية تقنية"]:
    req(forbidden not in CODE+DOC, "forbidden_claim:"+forbidden)

print("QURANIC_GOVERNANCE_GATE=PASS rules=8 divine_tech_claims=forbidden operational_values=testable")
