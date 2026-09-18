from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

script = text("scripts/prepare-bundled-quran.sh")
corpus = text("app/src/main/java/ps/hakim/phoneagent/HakimVerifiedQuranCorpus.kt")
bootstrap = text("app/src/main/java/ps/hakim/phoneagent/HakimQuranBootstrap.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("a5dd4a46dc6f7830a4303e89c3b4b3a15a213ac9" in script,
        "P0: مصدر القرآن المضمّن غير مثبت على commit معلوم")
require("d2960b3217962e7e4252abdcece67bea3d6b48271e4cd3af45bbbb2dd5c872ca" in script,
        "P0: بصمة ملف القرآن المضمّن مفقودة")
require("c1a2d34f901cfb233cbff8c57c770b76b810cbe51eccab69629529318ea82186" in script,
        "P0: البصمة القانونية للـcorpus مفقودة")
require("6236" in script and "range(1,115)" in script,
        "P0: فحص ١١٤ سورة/٦٢٣٦ آية مفقود وقت البناء")
require("QURAN_ASSET_AL_ALA_COUNT" in script,
        "P0: سورة الأعلى غير مفحوصة وقت البناء")
require("LICENSE-quran-meta.txt" in script and "MIT License" in script,
        "P0: ترخيص المصدر المرآتي غير محفوظ داخل APK")

for token in [
    "BUNDLED_ASSET_PATH",
    "BUNDLED_SOURCE_SHA256",
    "BUNDLED_CANONICAL_SHA256",
    "installBundledMirrorIfNeeded",
    "PINNED_KFQC_DERIVED_MIRROR",
    "official_archive_and_pinned_mirror_are_distinct",
    "bundled_quran_runtime_network_required",
]:
    require(token in corpus, f"P0: قيد runtime للقرآن المضمّن مفقود: {token}")

require('check(sourceSha.equals(BUNDLED_SOURCE_SHA256' in corpus,
        "P0: runtime لا يتحقق من SHA-256 للـasset")
require('check(canonical.equals(BUNDLED_CANONICAL_SHA256' in corpus,
        "P0: runtime لا يتحقق من canonical SHA-256")
require('check(ayat.count { it.surah == 87 } == 19)' in corpus,
        "P0: runtime لا يتحقق من سورة الأعلى")
require("validate(ayat)" in corpus and "replaceDatabaseAtomically(app, ayat)" in corpus,
        "P0: القرآن المضمّن لا يمر بفحص التغطية والاستبدال المحكوم")
require('.putBoolean("preserved_official_archive", false)' in corpus,
        "P0: المرآة المشتقة قد تُعرض كأرشيف رسمي")

bundled_pos = bootstrap.find("installBundledMirrorIfNeeded")
network_pos = bootstrap.find("meteredNetwork")
require(bundled_pos >= 0 and network_pos >= 0 and bundled_pos < network_pos,
        "P0: bootstrap يفحص الشبكة قبل القرآن المحلي")
require("runtime_network_required_for_quran" in bootstrap and
        "official_network_path_is_optional_upgrade" in bootstrap,
        "P0: استقلال القرآن عن الشبكة غير معلن في الحالة")

require("assets.srcDir" in build and "prepareBundledQuranAsset" in build and
        "preBuild" in build,
        "P0: أصل القرآن غير مربوط بكل build")
m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) == 20049, "P0: الإصدار يجب أن يكون ٢٠٠٤٩")
require(policy["current_candidate_version"] == 20049,
        "P0: سياسة التوقيع لا تسجل ٢٠٠٤٩ كمرشح")
require(policy["current_field_version"] == 20040,
        "P0: خط الميدان المباشر تغير بلا دليل جديد")

print("HAKIM_BUNDLED_QURAN_BUILD_VERIFY=PASS")
print("HAKIM_BUNDLED_QURAN_RUNTIME_VERIFY=PASS")
print("HAKIM_QURAN_RUNTIME_NETWORK_INDEPENDENCE=PASS")
