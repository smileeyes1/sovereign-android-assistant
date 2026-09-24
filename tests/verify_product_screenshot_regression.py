from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
OUT=(APP/"HakimProductOutput.kt").read_text(encoding="utf-8")
FACTORY=(APP/"HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
DIRECTOR=(APP/"HakimIntentDirector.kt").read_text(encoding="utf-8")
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("PRODUCT_SCREENSHOT_REGRESSION=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))>=20201,"version")

for token in [
    'Regex("(?m)^\\\\s{0,3}#{1,6}\\\\s*")',
    '.replace("**", "")',
    'fun requestsPdfArtifact',
    'fun looksLikeCapabilityRefusal',
]:
    req(token in OUT,"output:"+token)

req('HakimProductOutput.clean(streamingBuffer.toString())' in CENTER,"stream_not_sanitized")
req('if (role == "حكيم") HakimProductOutput.clean(message)' in CENTER,"messages_not_sanitized")
req('HakimProductOutput.requestsPdfArtifact(text)' in CENTER,"pdf_acceptance_missing")
req('HakimLocalArtifactFactory.canHandle(this, text)' in CENTER,"pdf_local_fallback_missing")

for phrase in ['"joining within 10"','"addition within 10"','isPdfAdditionWorksheet(prompt)']:
    req(phrase in FACTORY,"context:"+phrase)

for phrase in [
    "لا تعرض Markdown خامًا مثل ### أو **",
    "لا تعرض أسماء المحركات أو المزودين",
    "فلا تعتبر الشرح أو الاعتذار نجاحًا",
]:
    req(phrase in DIRECTOR,"director:"+phrase)

# The real field-failure wording becomes a permanent sentinel.
field_failure = "لا أملك القدرة على توليد ملفات PDF مباشرة"
req("لا أملك القدرة على توليد ملفات" in OUT,"field_refusal_sentinel_missing")

print("PRODUCT_SCREENSHOT_REGRESSION=PASS markdown=hidden pdf=file context=restored internal_details=hidden")
