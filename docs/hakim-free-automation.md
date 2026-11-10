# Hakim free automation — safe baseline

This branch adds a scheduled GitHub Actions check only. It does not call Manus, send data to third parties, deploy services, modify production, or consume Manus API credits.

- Schedule: 06:17 UTC daily (GitHub may delay scheduled workflows).
- Manual trigger: workflow_dispatch.
- No secrets required.
- No write permission: contents: read.
- Fail-closed flag: MANUS_EXECUTION_ENABLED=false.
- Report: short-lived Actions artifact.

## Limits and next gate
The workflow must be merged to the default branch before GitHub's scheduled trigger will run. A manual run can be tested on this branch if GitHub exposes it. A successful workflow run must be observed before claiming operational success. GitHub free-tier policies and quotas may change.

Do not enable Manus calls without separately verifying API billing, adding a spending limit or equivalent guard, and securely storing a secret in GitHub Actions. Never put student data or credentials in repository files or workflow logs.
