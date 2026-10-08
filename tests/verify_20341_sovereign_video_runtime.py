from pathlib import Path
import importlib.util
import json
import os
import re
import tempfile

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
RUNTIME=(APP/"HakimVideoRuntime.kt").read_text(encoding="utf-8")
VIDEO=(APP/"HakimVideoFactory.kt").read_text(encoding="utf-8")
REGISTRY=(APP/"HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimCapabilityFabric.kt").read_text(encoding="utf-8")
VERIFIER=(APP/"HakimCapabilityVerifier.kt").read_text(encoding="utf-8")
BRIDGE=(ROOT/"tools/hakim_video_runtime_bridge.py").read_text(encoding="utf-8")
STACK=json.loads((ROOT/"governance/HAKIM_VIDEO_FACTORY_STACK.json").read_text(encoding="utf-8"))
PROTO=json.loads((ROOT/"governance/HAKIM_VIDEO_RUNTIME_PROTOCOL.json").read_text(encoding="utf-8"))
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMO=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("SOVEREIGN_VIDEO_RUNTIME_20341=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))>=20341,"version")\nVERSION=int(m.group(1))
req("sovereign-video-factory-r30-sovereign-video-runtime-r31" in BUILD,"lineage")

for token in [
    "VIDEO-RUNTIME-2026-10-08-r31",
    "hakim-video-runtime-v1",
    "AndroidKeyStore",
    "invalid_or_insecure_endpoint",
    'scheme != "https"',
    'scheme == "http" && loopback',
    "runtime_submission_requires_explicit_gate",
    "paid_runtime_requires_explicit_external_approval",
    '"send_external"',
    "arbitrary_shell_exposed",
    "model_download_automatic",
    "paid_execution_automatic",
]:
    req(token in RUNTIME,"android_runtime:"+token)

for forbidden in ["Runtime.getRuntime()", "ProcessBuilder(", "su ", "pm install", "REQUEST_INSTALL_PACKAGES"]:
    req(forbidden not in RUNTIME,"android_runtime_forbidden:"+forbidden)

for token in [
    'HakimVideoRuntime.status(context)',
    'HakimVideoRuntime.submit(context, plan)',
    'HakimVideoRuntime.job(context, jobId)',
]:
    req(token in VIDEO,"factory_runtime:"+token)

for token in [
    'Contract("video.runtime"',
    'Contract("video.job.read"',
    'Contract("video.render"',
    '"video.runtime", "video.job.read", "video.render" -> HakimVideoRuntime.status(context).optBoolean("configured", false)',
]:
    req(token in REGISTRY,"registry:"+token)
for token in [
    '"video.runtime" -> HakimVideoRuntime.probe(context)',
    '"video.job.read" -> HakimVideoRuntime.job(context, payload.optString("job_id"))',
]:
    req(token in FABRIC,"fabric:"+token)
for token in ['"video.runtime" ->','"video.job.read" ->']:
    req(token in VERIFIER,"verifier:"+token)

for token in [
    'PROTOCOL = "hakim-video-runtime-v1"',
    'BIND = os.environ.get("HAKIM_VIDEO_BIND", "127.0.0.1")',
    'token_required_for_non_loopback_bind',
    'COST_CLASS == "paid"',
    'HAKIM_VIDEO_WORKFLOW_TEMPLATE',
    '/hakim/video/v1/capabilities',
    '/hakim/video/v1/jobs',
    'idempotency_key',
    'os.replace(tmp, JOBS_FILE)',
    'COMFY_BASE + "/prompt"',
    'COMFY_BASE + "/history/"',
    'COMFY_BASE + "/view?"',
    '"arbitrary_shell_exposed": False',
    '"model_download_automatic": False',
    '"paid_execution_automatic": False',
]:
    req(token in BRIDGE,"bridge:"+token)
for forbidden in ["subprocess.", "os.system(", "eval(", "exec(", "pip install", "git clone", "curl ", "wget "]:
    req(forbidden not in BRIDGE,"bridge_forbidden:"+forbidden)

# Functional unit test: compile a fixed workflow template without network or shell.
with tempfile.TemporaryDirectory() as td:
    template=Path(td)/"workflow.json"
    template.write_text(json.dumps({
        "1":{"inputs":{
            "text":"__HAKIM_PROMPT__",
            "negative":"__HAKIM_NEGATIVE_PROMPT__",
            "width":"__HAKIM_WIDTH__",
            "height":"__HAKIM_HEIGHT__",
            "frames":"__HAKIM_FRAMES__",
            "fps":"__HAKIM_FPS__",
            "seed":"__HAKIM_SEED__"
        }}
    }),encoding="utf-8")
    old=os.environ.get("HAKIM_VIDEO_WORKFLOW_TEMPLATE")
    os.environ["HAKIM_VIDEO_WORKFLOW_TEMPLATE"]=str(template)
    try:
        spec=importlib.util.spec_from_file_location("hakim_video_runtime_bridge_test", ROOT/"tools/hakim_video_runtime_bridge.py")
        mod=importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(mod)
        compiled=mod.compile_workflow({
            "goal":"طفل يعد سبع كرات برتقالية",
            "negative_prompt":"تشوه",
            "width":1280,"height":720,"fps":24,"duration_sec":5,"seed":7
        })
        inputs=compiled["1"]["inputs"]
        req(inputs["text"]=="طفل يعد سبع كرات برتقالية","compile_prompt")
        req(inputs["negative"]=="تشوه","compile_negative")
        req(inputs["width"]==1280 and inputs["height"]==720,"compile_size")
        req(inputs["frames"]==120 and inputs["fps"]==24,"compile_timing")
        req(inputs["seed"]==7,"compile_seed")
    finally:
        if old is None: os.environ.pop("HAKIM_VIDEO_WORKFLOW_TEMPLATE",None)
        else: os.environ["HAKIM_VIDEO_WORKFLOW_TEMPLATE"]=old

req(PROTO["protocol"]=="hakim-video-runtime-v1","protocol")
req(PROTO["security"]["token_required_for_non_loopback"] is True,"token_gate")
req(PROTO["security"]["arbitrary_shell"] is False,"shell_gate")
req(PROTO["security"]["paid_execution_automatic"] is False,"paid_gate")
req(STACK["runtime"]["protocol"]=="hakim-video-runtime-v1","stack_runtime")
req(STACK["runtime"]["field_runtime_verified"] is False,"runtime_field_false")

candidate=STATE["android"]["candidate"]
req(candidate["version_code"]==VERSION,"state_candidate")
req(candidate["field_verified"] is False and candidate["same_signed_apk_field_verified"] is False,"field_inheritance")
product=STATE["productization"]
req(product["candidate_version"]==VERSION,"product_candidate")
req(product["sovereign_video_runtime_r31_source_integrated"] is True,"runtime_integrated")
req(product["sovereign_video_runtime_r31_source_ci_verified"] is False,"runtime_ci_starts_false")
req(product["sovereign_video_runtime_r31_field_verified"] is False,"runtime_field_starts_false")
req(product["video_runtime_phone_configured"] is False,"phone_runtime_not_claimed")
req(product["video_runtime_external_compute_field_verified"] is False,"compute_field_not_claimed")
req(PROMO["candidate_version"]==VERSION and PROMO["promoted"] is False,"promotion_closed")
req(PROMO["sovereign_video_runtime_r31_field_verified"] is False,"promotion_runtime_field_false")
req("python3 tests/verify_20341_sovereign_video_runtime.py" in WORKFLOW,"workflow_gate")
req(f'test "$VERSION_CODE" = "{VERSION}"' in WORKFLOW,"workflow_version")

print("SOVEREIGN_VIDEO_RUNTIME_20341=PASS protocol=true self_hosted=true auth=true no_shell=true paid_auto=false field=false")
