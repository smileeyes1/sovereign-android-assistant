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
    'COPILOT_CLI_VERSION: "1.0.88"',
    "id-token: write",
    "copilot-requests: write",
    "contents: read",
    "contents: write",
    "pull-requests: write",
    "actions: write",
    "audience=hakim-development-worker",
    "/development/v1/lease",
    "/complete",
    "source_mutation_on_device == false",
    "github_secret_on_device == false",
    "field_install_requires_separate_authorization == true",
    "same_artifact_field_evidence_required == true",
    "no_permission_expansion == true",
    "governance/HAKIM_SOVEREIGN_DEVELOPMENT_CONTRACT.json",
    "لا تعدّل أي workflow أو عقد حوكمة أو ملفات توقيع/إصدار/نشر",
    "لا تعمل commit أو push أو PR",
    "git diff --check",
    "python3 tests/verify_unified_hakim.py",
    "gh workflow run android.yml --ref",
    "gh run watch",
    "gh pr merge",
]:
    req(token in WORKFLOW,"missing:"+token)

develop=WORKFLOW.split("
  develop:",1)[1].split("
  publish:",1)[0]
publish=WORKFLOW.split("
  publish:",1)[1].split("
  acknowledge-agent-failure:",1)[0]

req("contents: write" not in develop,"agent_has_repo_write")
req("pull-requests: write" not in develop,"agent_has_pr_write")
req("actions: write" not in develop,"agent_has_actions_write")
req("copilot-requests: write" in develop,"agent_missing_copilot")
req("copilot-requests: write" not in publish,"publisher_has_copilot")
req("contents: write" in publish and "pull-requests: write" in publish,"publisher_missing_scoped_write")
req("persist-credentials: false" in develop,"agent_checkout_persists_credentials")

for forbidden in [
    "secrets.COPILOT",
    "secrets.GITHUB",
    "PERSONAL_ACCESS_TOKEN",
    "ghp_",
    "github_pat_",
    "HAKIM_SIGNING",
]:
    req(forbidden not in WORKFLOW,"long_lived_secret_surface:"+forbidden)

req("\\${" not in WORKFLOW,"escaped_expression_would_break_worker")
req("autonomous/hakim-development" in WORKFLOW,"staging_line_missing")
req("release/hakim-20316-autoupdate-rootfix-candidate" not in WORKFLOW,"worker_writes_field_line_directly")

print("HAKIM_DEVELOPMENT_WORKER_GATE=PASS")
