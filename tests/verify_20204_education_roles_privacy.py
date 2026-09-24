from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"

BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
PROFILE=(APP/"HakimEducationProfile.kt").read_text(encoding="utf-8")
PRIVACY=(APP/"HakimEducationPrivacyPolicy.kt").read_text(encoding="utf-8")
ENTERPRISE=(APP/"HakimEnterprisePolicy.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimModelToolRouter.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")
RESTRICTIONS=(ROOT/"app/src/main/res/xml/app_restrictions.xml").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
SELLABLE=json.loads((ROOT/"governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))

def req(v,reason):
    if not v:
        raise SystemExit("EDUCATION_ROLES_PRIVACY=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))==20204,"version")
req("3.0.4-education-roles-privacy-v1-candidate" in BUILD,"version_name")

for token in [
    'TEACHER("teacher"',
    'STUDENT("student"',
    'SCHOOL_ADMIN("school_admin"',
    'SUPERVISOR("supervisor"',
    'STAFF("staff"',
    'GUARDIAN("guardian"',
    "forcedEducationRole(context)",
]:
    req(token in PROFILE or token in ENTERPRISE,"role:"+token)

for token in [
    "studentExternalAiAllowed",
    "studentExternalAttachmentsAllowed",
    "externalStudentDataAllowed",
    "containsProtectedStudentData",
    "رقم الهوية",
    "هوية الطالب",
    "قائمة الطلبة",
    "Regex",
]:
    req(token in PRIVACY or token in ENTERPRISE,"privacy:"+token)

req("role == HakimEducationProfile.Role.STUDENT" in PRIVACY,"student_gate")
req("!enterprise.studentExternalAiAllowed" in PRIVACY,"student_ai_default_closed")
req("!enterprise.studentExternalAttachmentsAllowed" in PRIVACY,"student_attachment_default_closed")
req("!enterprise.externalStudentDataAllowed" in PRIVACY,"student_data_default_closed")

for capability in [
    "Capability.EXTERNAL_ATTACHMENT",
    "Capability.WEB",
    "Capability.EXTERNAL_AI",
]:
    req(capability in ROUTER,"router:"+capability)

privacy_pos=ROUTER.index("HakimEducationPrivacyPolicy.blockReason")
direct_pos=ROUTER.index("HakimEngineRegistry.bestGeneralChat")
req(privacy_pos < direct_pos,"privacy_after_model")

for key in [
    'android:key="education_role"',
    'android:key="allow_student_external_ai"',
    'android:key="allow_student_external_attachments"',
    'android:key="allow_external_student_data"',
]:
    req(key in RESTRICTIONS,"managed:"+key)

req('"اختيار دوري التعليمي"' in HOME,"role_ui")
req("showEducationRolePicker()" in HOME,"role_picker")
req("بيانات الطلبة الحساسة" in HOME,"privacy_copy")
req("python3 tests/verify_20204_education_roles_privacy.py" in WORKFLOW,"ci_gate")

req(STATE["android"]["candidate"]["version_code"]==20204,"state_version")
req(STATE["android"]["candidate"]["field_verified"] is False,"state_field")
req(STATE["android"]["candidate"]["promoted"] is False,"state_promoted")
req(PROMOTION["candidate_version"]==20204 and PROMOTION["promoted"] is False,"promotion")
req(SELLABLE["candidate_version"]==20204 and SELLABLE["sellable"] is False,"sellable")

edu=STATE.get("education_product",{})
req(edu.get("source_integrated") is True,"state_integrated")
req(edu.get("student_external_ai_default")=="BLOCKED","state_student_ai")
req(edu.get("student_external_attachments_default")=="BLOCKED","state_student_attachments")
req(edu.get("external_student_data_default")=="BLOCKED","state_student_data")
req(edu.get("field_verified") is False,"state_field_verified")

# Known-failure sentinel: removing the student AI guard must be detectable.
mutated=PRIVACY.replace("!enterprise.studentExternalAiAllowed","true",1)
req(mutated != PRIVACY and "!enterprise.studentExternalAiAllowed" in PRIVACY,"sentinel")

print("EDUCATION_ROLES_PRIVACY=PASS version=20204 student_external_ai=blocked student_data=blocked field=false")
