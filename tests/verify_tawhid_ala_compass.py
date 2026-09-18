from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

compass = text("app/src/main/java/ps/hakim/phoneagent/HakimTawhidAlaCompass.kt")
norm = text("app/src/main/java/ps/hakim/phoneagent/HakimNormativeSovereignty.kt")
one = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
fabric = text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
build = text("app/build.gradle")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

require("object HakimTawhidAlaCompass" in compass, "P0: بوصلة التوحيد والأعلى مفقودة")
for token in [
    "la_ilaha_illa_allah_tawhid",
    "worship_belongs_to_allah_alone",
    "hakim_is_not_divine",
    "hakim_has_no_sacred_self_authority",
    "hakim_cannot_claim_revelation",
    "muhammad_is_messenger_of_allah",
    "muhammad_is_seal_of_prophets",
    "no_new_revelation_from_model_or_system",
    "surah_al_ala_special_compass",
    "surah_al_ala_exact_text_requires_verified_corpus",
    "surah_al_ala_not_technical_algorithm",
    "surah_al_ala_no_hidden_power_claim",
    "spiritual_state_not_measurable_by_software",
]:
    require(token in compass, f"P0: قيد عقد التوحيد/الأعلى مفقود: {token}")

for ref in ["87:1", "87:2-3", "87:8-10", "87:14-15", "87:16-17", "87:18-19"]:
    require(ref in compass, f"P0: مرجع من سورة الأعلى مفقود: {ref}")

require("لا تُستخرج منها قوانين برمجية أو فيزيائية بلا دليل مستقل" in compass,
        "P0: قد تتحول سورة الأعلى إلى مصدر قانون تقني")
require("البرنامج قد يذكّر فقط ولا يدعي أداء العبادة أو قياس صلاح القلب" in compass,
        "P0: البرنامج قد يدعي أداء العبادة أو قياس الإيمان")
require("لا تُشترى قيمة أعلى دائمة بمكسب دنيوي أدنى" in compass,
        "P0: مبدأ 87:16-17 غير محروس")
require("لا يخترع حكيم نصوصًا من صحف سابقة" in compass,
        "P0: 87:18-19 لا يمنع اختلاق نصوص سابقة")

require("HakimTawhidAlaCompass.status()" in norm,
        "P0: المرجعية السيادية لا تحمل بوصلة التوحيد والأعلى")
require("HakimTawhidAlaCompass.require()" in one,
        "P0: النواة الواحدة لا تفشل مغلقًا عند غياب بوصلة التوحيد")
require("tawhid_ala_compass" in one,
        "P0: حالة النواة لا تكشف بوصلة التوحيد")

for edge in [
    "normative_sovereignty→tawhid_ala_compass",
    "tawhid_ala_compass→one_sovereign_kernel",
    "tawhid_ala_compass→sovereign_engine",
]:
    require(edge in fabric, f"P0: وصلة بوصلة التوحيد مفقودة: {edge}")

require("tawhid_ala_compass_integrated" in fabric,
        "P0: نسيج التكامل لا يعد بوصلة التوحيد شرط سلامة")
require("لا إله إلا الله — التوحيد مثبت" in selfcheck,
        "P0: الفحص الذاتي لا يحرس التوحيد")
require("محمد رسول الله — الرسالة مثبتة" in selfcheck,
        "P0: الفحص الذاتي لا يحرس الرسالة")
require("سورة الأعلى ليست خوارزمية تقنية" in selfcheck,
        "P0: الفحص الذاتي لا يمنع الخلط التقني بسورة الأعلى")

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20047, "P0: المرشح يجب ألا يرجع قبل ٢٠٠٤٧")
candidate = int(m.group(1))
require(policy["current_candidate_version"] == candidate,
        "P0: سياسة التوقيع لا تطابق رقم المرشح الحالي")
require(policy["current_field_version"] == 20040,
        "P0: تم تغيير خط الميدان بلا دليل مباشر")

print("HAKIM_TAWHiD_RISALAH=PASS")
print("HAKIM_SURAH_AL_ALA_COMPASS=PASS")
print("HAKIM_NO_RELIGIOUS_TECHNICAL_MYSTIFICATION=PASS")
