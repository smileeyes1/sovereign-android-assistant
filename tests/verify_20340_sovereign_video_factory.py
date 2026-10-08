from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
VIDEO=(APP/"HakimVideoFactory.kt").read_text(encoding="utf-8")
KERNEL=(APP/"HakimCapabilityKernel.kt").read_text(encoding="utf-8")
REGISTRY=(APP/"HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimCapabilityFabric.kt").read_text(encoding="utf-8")
VERIFIER=(APP/"HakimCapabilityVerifier.kt").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMO=json.loads((ROOT/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
STACK=json.loads((ROOT/"governance/HAKIM_VIDEO_FACTORY_STACK.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("SOVEREIGN_VIDEO_FACTORY_20340=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20340,"version")
req("private-voice-vault-r29-sovereign-video-factory-r30" in BUILD,"lineage")

for token in [
    "VIDEO-FACTORY-2026-10-08-v2",
    "HAKIM-SOVEREIGN-VIDEO-STACK-2026-10-08-r30",
    "provider_lock_in",
    "self_hosted_open_runtime_supported",
    "phone_heavy_generation_currently_available",
    "compute_independence_currently_proven",
    "paid_without_explicit_approval",
    "private_media_upload_without_scope",
    "voice_clone_without_rights_confirmation",
    "ARTIFACT_HASH_MATCH",
    "RIGHTS_AND_LICENSE",
    "runtime_provider_discovery_required",
]:
    req(token in VIDEO,"video:"+token)

req('.put("provider_lock_in", false)' in VIDEO,"provider_lock_in_false")
req('.put("phone_heavy_generation_currently_available", false)' in VIDEO,"phone_heavy_generation_not_overclaimed")
req('.put("compute_independence_currently_proven", false)' in VIDEO,"compute_independence_not_overclaimed")
req('.put("artifact_created", false)' in VIDEO,"planning_not_rendering")
req("regenerate_failed_shot_only" in VIDEO,"segment_repair")

req('Capability("plan_media"' in KERNEL,"kernel_plan_media")
for token in [
    'Contract("video.capabilities"',
    'Contract("video.plan"',
    '"video.capabilities", "video.plan" -> true',
]:
    req(token in REGISTRY,"registry:"+token)

for token in [
    '"video.capabilities" -> HakimVideoFactory.capabilities(context)',
    '"video.plan" -> HakimVideoFactory.plan(payload)',
]:
    req(token in FABRIC,"fabric:"+token)

for token in ['"video.capabilities" ->','"video.plan" ->','provider_blueprints','quality_gates']:
    req(token in VERIFIER,"verifier:"+token)

# R30 is planning/orchestration only; it must not silently expand Android authority.
for permission in [
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG",
]:
    req(permission not in MANIFEST,"unexpected_permission:"+permission)

req(STACK["stack_version"]=="HAKIM-SOVEREIGN-VIDEO-STACK-2026-10-08-r30","stack_version")
req(STACK["principles"]["provider_lock_in"] is False,"stack_provider_lock")
req(STACK["principles"]["paid_requires_explicit_approval"] is True,"stack_paid_gate")
req(STACK["current_device_boundary"]["android_phone_heavy_frontier_generation_proven"] is False,"phone_compute_boundary")
req(STACK["current_device_boundary"]["no_claim_of_compute_independence_without_gpu_runtime"] is True,"compute_claim_gate")
ids={x["id"] for x in STACK["open_stack"]}
for x in ["comfyui","ltx2","wan_open_video","hunyuanvideo","ffmpeg"]:
    req(x in ids,"open_stack:"+x)

candidate=STATE["android"]["candidate"]
req(candidate["version_code"]==20340,"state_candidate")
req(candidate["field_verified"] is False,"field_inheritance")
req(candidate["same_signed_apk_field_verified"] is False,"same_artifact_inheritance")
product=STATE["productization"]
req(product["candidate_version"]==20340,"product_candidate")
req(product["sovereign_video_factory_r30_source_integrated"] is True,"source_integrated")
req(product["sovereign_video_factory_r30_source_ci_verified"] is True,"source_ci_verified")\nreq(product["video_factory_r30_source_ci_run_number"]==1661,"product_source_ci_run")\nreq(product["video_factory_r30_advanced_unsigned_sha256"]=="51d25167422d9329c6108ee5c4bcde0235d45744473d4ea2057f837ba36ac949","product_advanced_unsigned_sha")
req(product["sovereign_video_factory_r30_field_verified"] is False,"field_starts_false")
req(product["video_provider_lock_in"] is False,"product_provider_lock")
req(product["video_paid_without_explicit_approval"] is False,"paid_without_approval")
req(PROMO["candidate_version"]==20340 and PROMO["promoted"] is False,"promotion_closed")
req(PROMO["sovereign_video_factory_r30_source_ci_verified"] is True,"promotion_source_ci_verified")\nreq(PROMO["video_factory_r30_source_ci_run_number"]==1661,"source_ci_run")\nreq(PROMO["video_factory_r30_source_ci_verified_head"]=="712b39aae652f3682562264d1e2663564913877d","source_ci_head")\nreq(PROMO["video_factory_r30_advanced_unsigned_sha256"]=="51d25167422d9329c6108ee5c4bcde0235d45744473d4ea2057f837ba36ac949","advanced_unsigned_sha")\nreq(PROMO["sovereign_video_factory_r30_field_verified"] is False,"promotion_field_false")
req("python3 tests/verify_20340_sovereign_video_factory.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20340"' in WORKFLOW,"workflow_version")

print("SOVEREIGN_VIDEO_FACTORY_20340=PASS provider_neutral=true open_path=true paid_gated=true render_not_overclaimed=true")
