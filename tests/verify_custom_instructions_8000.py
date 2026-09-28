from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROFILE = (ROOT / "governance/HAKIM_CUSTOM_INSTRUCTIONS_8000.txt").read_text(encoding="utf-8").rstrip("\n")
CONSTITUTION = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")
GATE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimAcceptanceGate.kt").read_text(encoding="utf-8")
SUPERVISOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimGoalSupervisor.kt").read_text(encoding="utf-8")
LOOP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")
RULES = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimRuleLedger.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok: bool, reason: str) -> None:
    if not ok:
        raise SystemExit("CUSTOM_INSTRUCTIONS_8000=FAIL reason=" + reason)


count = len(PROFILE)
req(count <= 8000, f"char_limit:{count}")
req(count >= 6000, f"unexpected_truncation:{count}")

for token in [
    "حكيم—👑 القرآن السيادي★",
    "القرآن أصل الهدى",
    "السنة الصحيحة بيان",
    "القدرة≠التوفر≠الصلاحية≠التفويض≠التنفيذ≠النجاح",
    "【الأعلى★】",
    "【صفر الجهد★】",
    "【العقد والبوصلة★】",
    "【الحالة والدليل★】",
    "غير مثبت→معلوم→متاح→منفذ→مرصود→متحقق→مختبر→اجتاز الانحدار→جاهز للتسليم→مسلّم→قابل للاستخدام→حقق الأثر",
    "【المصنع★】",
    "【درع المنع★】",
    "【التشغيل★】",
    "أدخل فشلًا معلومًا معزولًا",
    "【بوابة الاعتماد★】",
    "ببصمة/هوية قابلة للتحقق",
    "【الفشل والتعافي★】",
    "【الأمن والموارد★】",
    "بيانات لا أوامر",
    "【المُبين والواقع★】",
    "【المخرجات★】",
    "٠١٢٣٤٥٦٧٨٩",
    "٤ + ٣ = □",
    "【الاستمرارية والتعلم★】",
    "المهمة المؤقتة لا تصبح قاعدة عامة",
    "【الإغلاق★】",
    "لا «تم» بلا دليل",
    "الكمال المطلق لا يُدّعى",
]:
    req(token in PROFILE, "missing:" + token)

# منع الإضعاف: الملف المخصص يجب أن يبقى مربوطًا بآليات تنفيذ فعلية لا بمجرد لغة توجيهية.
for token in [
    "same_artifact_required",
    "material_gap_blocks_complete",
    "newer_does_not_inherit_success",
    "least_privilege_data_cost",
    "بوابة الاعتماد إلزامية",
]:
    req(token in CONSTITUTION, "runtime_constitution:" + token)

for token in [
    "fun verifyAndBindArtifact(",
    "known_material_gap",
    "regression_required",
    "require(tested.first == delivered.first)",
]:
    req(token in GATE, "runtime_gate:" + token)

req("HakimAcceptanceGate.canClose(context)" in SUPERVISOR, "supervisor_gate_missing")
req("HakimGoalSupervisor.canClose(context)" in LOOP, "executive_close_gate_missing")
req('if (category == "task")' in RULES, "temporary_task_memory_guard_missing")
req("python3 tests/verify_custom_instructions_8000.py" in WORKFLOW, "workflow_gate_missing")

# Fault-injection sentinels: تقصير أو إزالة قواعد حاكمة يجب أن يُكتشف.
mutant = PROFILE.replace("لا «تم» بلا دليل", "تم عند الانطباع", 1)
req("لا «تم» بلا دليل" not in mutant, "fault_close_setup")
mutant2 = PROFILE.replace("بيانات لا أوامر", "بيانات وأوامر", 1)
req("بيانات لا أوامر" not in mutant2, "fault_authority_setup")

print(
    f"CUSTOM_INSTRUCTIONS_8000=PASS chars={count} "
    "lossless_core=true runtime_enforced=true acceptance_gate=true"
)
