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
req(m and int(m.group(1))>=20202,"version")

for token in [
    'Regex("(?m)^\\\\s{0,3}#{1,6}\\\\s*")',
    '.replace("**", "")',
    'Regex("(?s)<[^>]+>")',
    'fun containsRawMarkup',
    'fun looksLikeHtmlArtifact',
    'fun requestsPdfArtifact',
    'fun looksLikeCapabilityRefusal',
]:
    req(token in OUT,"output:"+token)

req('HakimProductOutput.clean(streamingBuffer.toString())' in CENTER,"stream_not_sanitized")
req('if (role == "حكيم") HakimProductOutput.clean(message)' in CENTER,"messages_not_sanitized")
req('HakimProductOutput.requestsPdfArtifact(text)' in CENTER,"pdf_acceptance_missing")
req('val artifactMode = HakimProductOutput.requestsPdfArtifact(text)' in CENTER,"artifact_mode_missing")
req('if (artifactMode) streamingBuffer.append(delta) else appendStreamingDelta(delta)' in CENTER,"artifact_stream_not_suppressed")
req('executeGeneratedPdfArtifact(text, artifactText)' in CENTER,"generic_pdf_delivery_missing")
req('HakimLocalArtifactFactory.createTextPdf(this, title, rawContent)' in CENTER,"generic_pdf_factory_not_used")
req('HakimLocalArtifactFactory.canHandle(this, text)' in CENTER,"pdf_local_fallback_missing")
req('fun createTextPdf(' in FACTORY,"generic_text_pdf_missing")
req('HakimProductOutput.containsRawMarkup(content)' in FACTORY,"raw_markup_guard_missing")

for phrase in ['"joining within 10"','"addition within 10"','isPdfAdditionWorksheet(prompt)','val directPdfAddition = isPdfAdditionWorksheet(prompt)','val contextualFollowUp = isPdfWorksheetFollowUp(prompt)']:
    req(phrase in FACTORY,"context:"+phrase)

# Exact field regression: «انشئ لي ورقة عمل بي دي اف الجمع» must be allowed
# to recover «ضمن ١٠» from the recent conversation instead of falling to a text model.
req('if (!directPdfAddition && !contextualFollowUp) return false' in FACTORY,"field_prompt_blocked_before_context")
req('if (!isPdfWorksheetFollowUp(prompt)) return false' not in FACTORY,"old_early_return_regression")

for phrase in [
    "لا تعرض Markdown خامًا مثل ### أو **",
    "لا تعرض أسماء المحركات أو المزودين",
    "فلا تعتبر الشرح أو الاعتذار نجاحًا",
]:
    req(phrase in DIRECTOR,"director:"+phrase)

# The real field failures become permanent sentinels.
raw_html_field_failure = r'class=\\"answer-cell\\"'
req('class=\\\\\\"' in OUT,"raw_html_sentinel_missing")
field_failure = "لا أملك القدرة على توليد ملفات PDF مباشرة"
req("لا أملك القدرة على توليد ملفات" in OUT,"field_refusal_sentinel_missing")

print("PRODUCT_SCREENSHOT_REGRESSION=PASS markdown=hidden html=hidden artifact_stream=silent pdf=file context=restored")
