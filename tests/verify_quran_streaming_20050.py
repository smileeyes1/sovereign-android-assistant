from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("JsonReader" in corpus and "JsonToken" in corpus,
        "P0: استيراد القرآن لا يستخدم قارئ JSON تدفقي")
require("streamBundledMirrorIntoDatabase" in corpus,
        "P0: مسار القرآن المضمّن ليس تدفقيًا")
require("assetDigest(app, BUNDLED_ASSET_PATH, \"SHA-256\")" in corpus,
        "P0: فحص SHA-256 للـasset ليس تدفقيًا")
require("db.beginTransaction()" in corpus and "db.setTransactionSuccessful()" in corpus,
        "P0: الإدخال التدفقّي لا يملك rollback ذريًا")
require("ayahCount == EXPECTED_AYA_COUNT" in corpus and
        "surahCount == 114" in corpus and
        "surahAlAlaCount == 19" in corpus,
        "P0: فحص ١١٤/٦٢٣٦/الأعلى مفقود")
require("BUNDLED_CANONICAL_SHA256" in corpus and
        "canonicalSha256" in corpus,
        "P0: canonical SHA-256 مفقود")
require("readBytes()" not in corpus[corpus.index("fun installBundledMirrorIfNeeded"):corpus.index("fun importOfficialArchive")],
        "P0: مسار القرآن المضمّن يعيد تحميل الملف كاملًا في RAM")
require("readBundledMirrorJson" not in corpus,
        "P0: parser القديم كامل الذاكرة ما زال موجودًا")
require("JSONArray(text)" not in corpus,
        "P0: JSONArray الكامل قد يعيد OutOfMemoryError")
require("streaming_import" in corpus,
        "P1: حالة المصدر لا تسجل أن الاستيراد تدفقي")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) == 20050, "P0: الإصدار يجب أن يكون ٢٠٠٥٠")
require(policy["current_candidate_version"] == 20050,
        "P0: سياسة التوقيع لا تسجل ٢٠٠٥٠ كمرشح")

print("HAKIM_QURAN_STREAMING_IMPORT=PASS")
print("HAKIM_QURAN_OOM_REGRESSION_GUARD=PASS")
