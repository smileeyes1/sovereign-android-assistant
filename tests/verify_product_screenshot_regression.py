from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
OUT=(APP/"HakimProductOutput.kt").read_text(encoding="utf-8")
FACTORY=(APP/"HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
LOOP=(APP/"HakimExecutiveLoop.kt").read_text(encoding="utf-8")
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
req('unwrapStructuredPayload' in OUT and 'JSONObject(t)' in OUT,"json_html_wrapper_not_unwrapped")
req('HakimProductOutput.containsRawMarkup(saved)' in CENTER,"persisted_raw_markup_not_migrated")
req('if (role == "حكيم") HakimProductOutput.clean(message)' in CENTER,"messages_not_sanitized")
req('HakimProductOutput.requestsPdfArtifact(text)' in CENTER,"pdf_acceptance_missing")
req('val artifactMode = HakimProductOutput.requestsPdfArtifact(text)' in CENTER,"artifact_mode_missing")
direct_fn=CENTER.split('private fun executeDirectModel',1)[1].split('private fun retryDirectOrBlock',1)[0]
direct_block=direct_fn.split('val result = engine.complete(instruction, snapshot)',1)[1].split('val latency =',1)[0]
req('streamingBuffer.append(delta)' in direct_block,"model_stream_not_buffered")
req('appendStreamingDelta(delta)' not in direct_block,"unverified_stream_visible_before_gate")
req('Fail-closed language policy' in CENTER,"ar_ps_stream_gate_missing")
req('executeGeneratedPdfArtifact(text, artifactText)' in CENTER,"generic_pdf_delivery_missing")
req('HakimProductOutput.worksheetMustUseStructuredFactory(text)' in CENTER,"worksheet_structured_gate_missing")
req(CENTER.index('HakimProductOutput.worksheetMustUseStructuredFactory(text)') < CENTER.index('executeGeneratedPdfArtifact(text, artifactText)'),"worksheet_guard_after_generic_pdf")
req('HakimLocalArtifactFactory.createTextPdf(this, title, preparedContent)' in CENTER,"generic_pdf_factory_not_using_validated_content")
req('generated_pdf_ar_ps_gate' in CENTER,"generic_pdf_ar_ps_gate_missing")
req('HakimLocalArtifactFactory.canHandle(this, text)' in CENTER,"pdf_local_fallback_missing")
req('fun createTextPdf(' in FACTORY,"generic_text_pdf_missing")
req('HakimProductOutput.containsRawMarkup(content)' in FACTORY,"raw_markup_guard_missing")

SPEC=(APP/"HakimTeacherArtifactSpec.kt").read_text(encoding="utf-8")
for phrase in ['"within 10"','"الجمع"','"الطرح"','"بي دي اف"','"للتحميل"','"للطباعة"']:
    req(phrase in SPEC or phrase in FACTORY,"context:"+phrase)

# Exact field regression: «انشئ لي ملف بي دي اف الجمع ضمن ١٠» must resolve directly.
req('fun resolveSpec(context: Context, prompt: String)' in FACTORY,"resolve_spec_missing")
req('HakimTeacherArtifactSpec.resolveExplicit(prompt)' in FACTORY,"explicit_topic_resolution_missing")
req('HakimTeacherArtifactSpec.looksLikeArtifactFollowUp(prompt)' in FACTORY,"context_followup_missing")

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
for phrase in ["save as pdf","ctrl + p","انسخ الكود","collection within 10"]:
    req(phrase in OUT.lower(),"field_bad_dump_sentinel:"+phrase)

print("PRODUCT_SCREENSHOT_REGRESSION=PASS markdown=hidden html=hidden artifact_stream=silent pdf=file context=restored")


# 20331 product-final regression from the real field screenshot:
# internal diagnostic blocks must be removed as blocks, without deleting later user turns.
req("stripInternalDiagnosticBlocks" in OUT,"internal_block_stripper_missing")
req("s = stripInternalDiagnosticBlocks(s, internalHeadings)" in OUT,"internal_block_stripper_not_applied")
for leaked in ["الدليل والقيود","القيود التقنية","التحقق الداخلي","ملاحظة أخيرة","internal reasoning","technical limitations"]:
    req(leaked in OUT,"internal_block_marker_missing:"+leaked)
req('trimmed == "أنت:" || trimmed == "حكيم:"' in OUT,"conversation_boundary_not_preserved")

# Primary product surface remains calm while exposing bounded user-facing progress.
# Raw event diagnostics stay internal; the visible strip is compact and expands only to
# the governed local task state.
req('operations.visibility = View.VISIBLE' in CENTER,"safe_operation_status_hidden")
req('operations.maxLines = 1' in CENTER and 'TextUtils.TruncateAt.END' in CENTER,
    "safe_operation_status_not_compact")
req('operations.text = HakimExecutiveLoop.latestOperationText(this)' in CENTER,
    "safe_operation_summary_missing")
req('operations.text = HakimExecutiveLoop.operationText(this)' in CENTER,
    "safe_operation_details_missing")
panel=LOOP.split("fun operationText",1)[1].split("fun providerInstruction",1)[0]
req('e.optString("detail")' not in panel,"raw_internal_operation_detail_visible")
req('HakimTaskManager.get(context, it)' in panel,"operation_state_not_from_canonical_task")
req('actionButton("المهام")' in CENTER,"tasks_button_missing")
req('actionButton("مشاركة")' in CENTER and 'userInitiated = true' in CENTER,
    "explicit_manual_share_missing")

# Persisted old screenshots with Markdown/internal headings must be migrated through clean().
req('"###", "**"' in OUT,"markdown_not_detected_for_migration")
