# Hakim Agent Governance — isolated upgrade proposal (2026-11-24)

Status: DESIGN ONLY / NOT DEPLOYED. Branch isolated from main.

## Objective
Extend the existing Hakim runtime rather than replace it. Preserve Android permission gate, local-first operation, existing continuity files and last field-verified signed baseline.

## Minimal execution contract
1. Intake: normalized task with idempotency key, scope, data classification, requested effect and expiry.
2. Preflight: check existing completion ledger, required permissions, source freshness, cost and device readiness.
3. Plan: bounded steps, no unbounded autonomous loops, each step declares expected effect and verification.
4. Independent guardian: deny by default on unknown scope, unsafe instructions, secrets exposure, missing approval, or unavailable rollback.
5. Execute: only via explicitly allowlisted existing adapter; no new device, GitHub, cloud or messaging permissions.
6. Verify: collect observed output, timestamps and evidence; distinguish simulation from field effect.
7. Checkpoint: append audit event and recovery pointer; preserve previous accepted baseline.
8. Resume: retry only idempotent operations; stop on ambiguous side effects and request review.

## Approval boundaries
Never auto-send messages, publish, pay, change permissions, pair a device, upload private data, merge or deploy. These require explicit approval scoped to the exact action.

## Failure gates
- Missing adapter or authorization -> BLOCKED
- Unexecuted end-to-end device test -> NOT TESTED
- Failing regression -> FAIL, retain baseline
- PASS requires exact artifact SHA, observed test result, and device proof where relevant

## Integration with existing work
Use existing hourly Hakim–Factory integration for discovery and deduplication. Do not create another recurring task. Do not activate Muse tokens, BLE pairing or SDK. Muse compatibility on Android/Termux remains unverified.

## Acceptance tests before implementation
- Duplicate task produces no repeated external effect
- Malicious retrieved instruction cannot escalate privileges
- Approval-gated action fails closed
- Offline/timeout resumes from checkpoint without double-execution
- Private content never enters telemetry
- Regression protects existing Android and continuity behavior
- Field test proves any claimed phone effect on the actual device

No implementation, CI result, merge, deployment or phone verification is claimed by this document.
