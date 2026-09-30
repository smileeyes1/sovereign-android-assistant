from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORKFLOW=(ROOT/".github/workflows/hakim-development-worker.yml").read_text(encoding="utf-8")

def req(cond,reason):
    if not cond:
        raise SystemExit("HAKIM_DEVELOPMENT_WORKER_GATE=FAIL reason="+reason)

for token in [
    'cron: "*/5 * * * *"',
    "cancel-in-progress: false",
    "HAKIM_DEVELOPMENT_BASE: autonomous/hakim-development",
    'LLAMA_CPP_TAG: "b11193"',
    'LLAMA_CPP_SHA256: "def277c3a4f0c5e2ec3b1413971878d7d5e7da7eee64f5120a0cd8f024f5a364"',
    'HAKIM_LOCAL_MODEL_SHA256: "cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046"',
    "Qwen2.5-Coder-1.5B-Instruct-GGUF",
    "id-token: write",
    "contents: read",
    "contents: write",
    "pull-requests: write",
    "actions: write",
    "audience=hakim-development-worker",
    "/development/v1/lease",
    "/defer",
    "/complete",
    "source_mutation_on_device == false",
    "github_secret_on_device == false",
    "field_install_requires_separate_authorization == true",
    "same_artifact_field_evidence_required == true",
    "no_permission_expansion == true",
    "governance/HAKIM_SOVEREIGN_DEVELOPMENT_CONTRACT.json",
    "NO_SAFE_PATCH",
    "BEGIN_PATCH",
    "END_PATCH",
    "git apply --check candidate.patch",
    "git diff --check",
    "python3 tests/verify_unified_hakim.py",
    "gh workflow run android.yml --ref",
    "gh run watch",
    "gh pr merge",
    "transient_runner_failure",
    "safe_patch_not_found",
]:
    req(token in WORKFLOW,"missing:"+token)

develop=WORKFLOW.split("\n  develop:",1)[1].split("\n  publish:",1)[0]
publish=WORKFLOW.split("\n  publish:",1)[1].split("\n  acknowledge-agent-failure:",1)[0]

req("contents: write" not in develop,"agent_has_repo_write")
req("pull-requests: write" not in develop,"agent_has_pr_write")
req("actions: write" not in develop,"agent_has_actions_write")
req("copilot-requests" not in develop,"paid_credit_agent_permission_present")
req("GITHUB_TOKEN:" not in develop,"agent_receives_github_token")
req("contents: write" in publish and "pull-requests: write" in publish,"publisher_missing_scoped_write")
req("copilot" not in publish.lower(),"publisher_has_ai_surface")
req("persist-credentials: false" in develop,"agent_checkout_persists_credentials")

for forbidden in [
    "secrets.COPILOT",
    "secrets.GITHUB",
    "PERSONAL_ACCESS_TOKEN",
    "ghp_",
    "github_pat_",
    "HAKIM_SIGNING",
    "@github/copilot",
    "copilot -p",
]:
    req(forbidden not in WORKFLOW,"forbidden_surface:"+forbidden)

req("\\${" not in WORKFLOW,"escaped_expression_would_break_worker")
req("autonomous/hakim-development" in WORKFLOW,"staging_line_missing")
req("release/hakim-20316-autoupdate-rootfix-candidate" not in WORKFLOW,"worker_writes_field_line_directly")
req("governance/" in WORKFLOW and "scripts/" in WORKFLOW,"governance_or_script_guard_missing")
req("HakimUnifiedRelay" in WORKFLOW,"remote_relay_guard_missing")
req("HakimCapabilityKernel" in WORKFLOW and "HakimConstitution" in WORKFLOW,"authority_guard_missing")
req("HakimAcceptanceGate" in WORKFLOW and "HakimSelfImprovementLoop" in WORKFLOW,"promotion_gate_guard_missing")
req("HakimFieldAcceptance" in WORKFLOW and "AutoUpdater" in WORKFLOW,"field_update_guard_missing")
req("AndroidManifest" in WORKFLOW and "app/build" in WORKFLOW,"build_identity_guard_missing")
req("uses-permission" in WORKFLOW,"android_permission_guard_missing")
req("versionCode|versionName|applicationId" in WORKFLOW,"identity_guard_missing")
req("github.com/ggml-org/llama.cpp/releases/download/" in WORKFLOW,"llama_binary_not_pinned")
req("huggingface.co/Qwen/" in WORKFLOW,"local_model_not_pinned")

print("HAKIM_DEVELOPMENT_WORKER_GATE=PASS")
