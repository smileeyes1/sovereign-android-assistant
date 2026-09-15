from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

gate = (ROOT / "docs/HAKIM_QURANIC_SOVEREIGN_RELEASE_GATE.md").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

required = [
    "١١٤ سورة و٦٢٣٦ آية",
    "مسح كامل فعلي",
    "البركة لا تُقدّم كمقياس أو خوارزمية أو قوة خفية أو ضمان دنيوي",
    "قلب حكيم المحلي يبقى قابلًا للعمل دون ChatGPT/Gemini/Copilot",
    "لا توسع عبارة عامة سلطة حكيم",
    "FIELD_VERIFIED",
    "NO-GO",
]
for item in required:
    assert item in gate, f"P0: بند إصدار مفقود: {item}"

assert "python3 tests/verify_quranic_whole_corpus_operating_core.py" in workflow

print("HAKIM_QURANIC_SOVEREIGN_RELEASE_GATE=PASS")
