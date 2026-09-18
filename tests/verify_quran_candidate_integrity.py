from pathlib import Path
import importlib.util
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


policy = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranCandidateIntegrity.kt")
source = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicSourceAuthority.kt")
verified = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
script_path = ROOT / "scripts/audit-quran-docx-candidate.py"

require("object HakimQuranCandidateIntegrity" in policy, "P0: حارس ملفات القرآن المرشحة مفقود")
require("EXPECTED_SURAH_COUNT = 114" in policy, "P0: الحارس لا يقفل ١١٤ سورة")
require("EXPECTED_AYA_COUNT = 6236" in policy, "P0: الحارس لا يقفل ٦٢٣٦ آية")
require("officialDigestMatched || evidence.fullPerAyahMatchAgainstVerifiedCorpus" in policy,
        "P0: قد يعتمد ملف خارجي بلا بصمة رسمية أو مطابقة كاملة")
require("structural_count_is_necessary_not_sufficient" in policy,
        "P0: قد يُفهم عد ١١٤/٦٢٣٦ كدليل أصل كافٍ")
require("filename_author_or_link_is_not_provenance" in policy,
        "P0: اسم الملف/مؤلف DOCX/الرابط قد يعامل كسلسلة توثيق")
require("auto_repair_quran_from_memory_forbidden" in policy,
        "P0: قد يصلح حكيم اختلافًا قرآنيًا من الذاكرة")
require("candidate_never_overrides_verified_corpus" in policy,
        "P0: ملف مرشح قد يتغلب على corpus الرسمي")
require("HakimQuranCandidateIntegrity.promptContext()" in source,
        "P0: الحارس موجود لكنه غير موصول بسلطة المصادر")
require("HakimQuranCandidateIntegrity.status()" in source,
        "P0: حالة حارس الملفات غير ظاهرة")

# المسار الرسمي المثبت يبقى المرجع التنفيذي الأعلى للنص الدقيق.
require("official_hash_mismatch" in verified,
        "P0: خزنة القرآن الرسمية لم تعد تفشل مغلقًا عند اختلاف البصمة")
require("EXPECTED_AYA_COUNT = 6236" in verified and "surahs.size == 114" in verified,
        "P0: فحص corpus الرسمي ١١٤/٦٢٣٦ انحدر")
require("acceptedSources.firstOrNull" in verified,
        "P0: الملف الخارجي قد يدخل قاعدة النص دون قائمة المصادر المقبولة")

# أداة DOCX نفسها يجب أن تكون تدقيقًا فقط، لا بوابة اعتماد أو إصلاح.
spec = importlib.util.spec_from_file_location("quran_candidate_audit", script_path)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)
require(sum(module.EXPECTED_COUNTS) == 6236 and len(module.EXPECTED_COUNTS) == 114,
        "P0: جدول العد البنيوي في أداة DOCX غير صحيح")

minimal_xml = """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>
<w:p><w:r><w:t>سورة الفاتحة</w:t></w:r></w:p>
<w:p><w:r><w:t>نص مرشح (1)</w:t></w:r></w:p>
</w:body></w:document>"""
with tempfile.TemporaryDirectory() as td:
    candidate = Path(td) / "candidate.docx"
    with zipfile.ZipFile(candidate, "w") as archive:
        archive.writestr("word/document.xml", minimal_xml)
    before = candidate.read_bytes()
    result = module.audit(candidate)
    after = candidate.read_bytes()
    require(result["canonical_quran_text_source"] is False,
            "P0: مدقق DOCX منح مرشحًا سلطة نص القرآن")
    require(result["classification"].startswith("QUARANTINED_CANDIDATE"),
            "P0: الملف غير المكتمل لم يُحجر")
    require(result["structural_coverage_ok"] is False,
            "P0: ملف ناقص اجتاز التغطية البنيوية")
    require(result["auto_repair_from_memory_forbidden"] is True,
            "P0: منع الإصلاح من الذاكرة غير ظاهر")
    require(before == after and result["original_file_mutated"] is False,
            "P0: التدقيق عدّل الملف الأصلي")

print("HAKIM_QURAN_CANDIDATE_INTEGRITY=PASS")
