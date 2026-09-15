from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

catalog = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicResourceCatalog.kt")
source = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicSourceAuthority.kt")
corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranicCorpusPolicy.kt")
method = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranSunnahMethod.kt")
workflow = text(".github/workflows/quranic-resource-catalog.yml")

require("مجمع الملك فهد لطباعة المصحف الشريف" in catalog and "qurancomplex.gov.sa" in catalog,
        "P0: المصدر الرسمي لفهرس الموارد القرآنية غير مثبت")
resources = {
    "KFGQPC_HAFS_SMART_V6": ("53d82b553e5fe919ca1a732e35bf4eb0", "1dbeae3847880b1c21a956dcfdc0a2d9d490e729"),
    "KFGQPC_HAFS_UNICODE_V13": ("cf6841aea5b1d1fd70d032b43ff08278", "36ea5ab0d7ea1702f17ff43f9b50924cccd77ebf"),
    "KFGQPC_WARSH_UNICODE": ("4701e8bbf053098220cf2cf4cda206a1", "44ecea8feb23817fdc01a8ee2162a6a0cf08cae7"),
    "KFGQPC_SHUBAH_UNICODE": ("5cda29121bf0d7234e039002e1fbf600", "8d66bdf0cab96dc7d1032792c19f77980ca6682a"),
    "KFGQPC_QALOUN_UNICODE": ("964208ff04c8aadd3ddc1be262d8cfd3", "81733666be17742e13c9fa4c7d26d42b1adc67c8"),
    "KFGQPC_DOURI_UNICODE": ("a60bdd18397b3e27e4617478968a35c8", "8049482f04b4ff1053a7859f96b2b113b9771efb"),
    "KFGQPC_SOUSI_UNICODE": ("1bf6023e29b7622a52b6171232c17096", "e52dbc6d8b43797a8faa0fd1ec1d8e5000265674"),
    "KFGQPC_TAFSIR_MUYASSAR": ("5601682965e32f4dd6992c7600fdccc3", "5f533113c2f54f32eded734bb49e6a5837965722"),
    "KFGQPC_GHAREEB_MUYASSAR": ("7e22381eedb152ee7ed6488f2395c6cd", "055a908c6ec7f06912c33bd00920406c665cc5f9"),
    "KFGQPC_TAJWEED_MUYASSAR": ("b4a265a810c0ce4a722019791910b67e", "d2496382fc5e843ccb693b94dd19407eaa174bea"),
}
for rid, (md5, sha1) in resources.items():
    require(rid in catalog and md5 in catalog.lower() and sha1 in catalog.lower(),
            f"P0: المورد الرسمي أو بصماته مفقودة: {rid}")

for kind in ["MUSHAF_TEXT", "QIRAAT", "TAFSIR", "GHAREEB", "TAJWEED"]:
    require(kind in catalog, f"P0: نوع مورد قرآني مفقود: {kind}")
require("revelation_and_interpretation_separated" in catalog,
        "P0: فهرس الموارد لا يفصل الوحي عن التفسير")
require("unknown_resource_requires_new_verification" in catalog,
        "P0: مورد غير معروف قد يقبل بلا تحقق")

for layer in ["MUSHAF_TEXT", "QIRAAT", "TAJWEED", "TAFSIR", "GHAREEB", "ASBAB_AL_NUZUL",
              "QURANIC_SCIENCES", "SURAH_METADATA", "TRANSLATION", "FIQH_DERIVATION", "SCHOLARLY_INFERENCE"]:
    require(layer in source, f"P0: طبقة سلطة مصدر مفقودة: {layer}")
require("HakimQuranicResourceCatalog.promptContext()" in source and
        "HakimQuranicResourceCatalog.status()" in source,
        "P0: فهرس الموارد غير موصول بسلطة المصادر")
require("translation_is_meaning_not_arabic_quran" in source,
        "P0: الترجمة قد تعامل كنص القرآن العربي")
require("recognized_disagreement_respected" in source,
        "P0: الخلاف المعتبر غير محروس في سلطة المصادر")

require("QURAN_KNOWLEDGE_DOMAINS.size == 18" in corpus,
        "P0: نطاق علوم القرآن الأحدث انحدر")
for layer in ["RASM_DABT_AYAH_NUMBERING", "TAJWEED_WAQF_IBTIDA", "MUHKAM_MUTASHABIH",
              "NASIKH_MANSUKH_CLAIMS", "GHARIB_LEXICON", "IRAB_BALAGHA_NAZM",
              "AHKAM_FIQH_INFERENCE", "MUSHAF_COMPILATION_TRANSMISSION_HISTORY",
              "TRANSLATIONS_AND_EXPLANATORY_RENDERINGS", "WORLDLY_SCIENCE_BOUNDARY"]:
    require(layer in corpus, f"P0: مجال من علوم القرآن الأحدث سقط: {layer}")
require('put("local_exhaustive_secondary_quranic_sciences_corpus_verified", false)' in corpus,
        "P0: قد يُدعى اكتمال علوم القرآن الثانوية محليًا بلا دليل")

require("HakimPropheticKnowledgePolicy" in method,
        "P0: إدخال البركة أسقط سياسة المعرفة النبوية")
require("القرآن كتاب مبارك" in method,
        "P0: معنى بركة القرآن غير مصرح به")
require("quranic_barakah_is_lawful_benefit_not_technical_guarantee" in method,
        "P0: فصل البركة عن الضمان التقني مفقود")
require("specific_virtues_and_effects_require_evidence" in method,
        "P0: الفضائل أو الآثار المخصوصة قد تنسب بلا دليل")
require("قوة تقنية خفية" in method and "ضمان نتيجة مادية" in method,
        "P0: منع الغلو التقني في مفهوم البركة غير مكتمل")
require("لا تنسب حديثًا أو سنة أو قصة أو فضيلة أو وعدًا" in method,
        "P0: حاجز النسبة النبوية المثبت انحدر")

require("python3 tests/verify_quranic_resource_catalog.py" in workflow,
        "P0: اختبار الموارد والبركة غير موصول بـCI")
print("HAKIM_QURANIC_RESOURCE_CATALOG=PASS")
