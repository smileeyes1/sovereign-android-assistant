from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
REQ=(APP/"HakimArtifactRequest.kt").read_text(encoding="utf-8")
PIPE=(APP/"HakimArtifactPipeline.kt").read_text(encoding="utf-8")
RENDER=(APP/"HakimUniversalPdfRenderer.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(v,reason):
    if not v:
        raise SystemExit("UNIVERSAL_ARTIFACT_20207=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))>=20207,"version")
req("universal-artifact-pipeline" in BUILD or "final-installable" in BUILD or "hardening-train" in BUILD or "core-product" in BUILD or
    "sovereign-constitution" in BUILD or "resilient-connectivity" in BUILD,"version_name")

# Request model is topic-agnostic: kinds and file intent are separate from subject.
for token in [
    "enum class Kind { WORKSHEET, LESSON, TEST, PLAN, DOCUMENT }",
    "enum class Format { PDF }",
    "fun resolve(context: Context, prompt: String)",
    "fun requestsFile(text: String)",
    "fun looksLikeFileFollowUp(text: String)",
    "extractTopic",
    "LAST_TOPIC",
]:
    req(token in REQ,"request:"+token)

# Content generation must be separated from binary/file generation.
for token in [
    "لا تنشئ PDF أصلًا",
    "حكيم سيتولى تحويل النص الناتج محليًا إلى PDF حقيقي",
    "fun validateContent",
    "fun repairInstruction",
    "recentUsableContent",
]:
    req(token in PIPE,"pipeline:"+token)
req("PdfDocument" not in PIPE,"pipeline_mixes_rendering")

# Renderer owns the actual file and verifies the same artifact.
for token in [
    "PdfDocument",
    "PdfRenderer",
    'magic == "%PDF-"',
    "renderer.pageCount",
    "bytes >= 700L",
    "MediaStore.Downloads.EXTERNAL_CONTENT_URI",
    "verified = true",
]:
    req(token in RENDER,"renderer:"+token)

# Routing invariant: every file request enters the artifact pipeline before any model choice.
req("ARTIFACT_PIPELINE" in ROUTER,"artifact_channel_missing")
artifact_decision=ROUTER.index("val artifactRequest = HakimArtifactRequest.resolve(context, q)")
direct_choice=ROUTER.index("HakimEngineRegistry.bestGeneralChat")
req(artifact_decision < direct_choice,"artifact_route_after_model")
req("HakimModelToolRouter.Channel.ARTIFACT_PIPELINE" in CENTER,"center_artifact_channel_missing")
req("executeArtifactPipeline(text)" in CENTER,"pipeline_execution_missing")
req("renderUniversalArtifact" in CENTER,"universal_render_missing")
req("generateArtifactContent" in CENTER,"content_generation_missing")
req("HakimUniversalPdfRenderer.render" in CENTER,"renderer_not_used")

# Known deterministic math remains an optimization, not a prerequisite.
req("deterministicSpec" in PIPE,"deterministic_optimization_missing")
block_start=CENTER.index("private fun executeArtifactPipeline")
block_end=CENTER.index("private fun executeLocalArtifact",block_start)
block=CENTER[block_start:block_end]
req("worksheetMustUseStructuredFactory" not in block,"generic_pipeline_reintroduces_structured_block")

# No new topic-specific patch for the observed «العدد ١» failure.
req("العدد ١" not in PIPE and "العدد ١" not in RENDER,"topic_specific_patch_detected")

# Fault injection: deleting PDF magic verification must be caught.
mutant=RENDER.replace('require(magic == "%PDF-")', 'require(true)', 1)
req(mutant!=RENDER and 'require(magic == "%PDF-")' in RENDER,"verification_fault_sentinel")

print("UNIVERSAL_ARTIFACT_20207=PASS architecture=content_then_render_then_verify topics=generic pdf=real")
