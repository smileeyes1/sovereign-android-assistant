# HAKIM PRODUCT V1 — FINAL END-USER CONTRACT

This branch is not another field-patch line. It defines the minimum product contract for an end-user assistant that can legitimately compete on experience and orchestration.

## Non-negotiable user experience
1. The user remains inside Hakim for ordinary conversation.
2. Normal chat never treats opening ChatGPT/Gemini/Claude/DeepSeek as the answer.
3. Text, image, document, audio and video attachments enter one composer and return results in the same conversation.
4. The composer, send/cancel controls and latest operation state remain visible with the Android keyboard open.
5. Operations are concise by default and expandable; private model chain-of-thought is never exposed.
6. Conversations persist locally and can resume after process death.

## Execution contract
1. Hakim owns the goal, acceptance criteria, routing, verification and recovery.
2. Models/tools are interchangeable engines; no single vendor is the manager.
3. Every request becomes an intent-specific completion contract.
4. An engine receives a compact governed task envelope, privately optimizes its method, and returns result + evidence/limitations only.
5. Provider launch, browser opening, Intent dispatch, HTTP 2xx or CI success are not task completion.
6. A task is complete only when the result is returned to Hakim and the acceptance criterion is verified.

## Required engine classes
- LOCAL: deterministic/private tasks and device-side operations.
- DIRECT_MODEL: official model inference channel returning output directly into Hakim.
- WEB_TOOL: fresh web/search/browser work whose result is ingested back into Hakim.
- DEVICE_TOOL: authorized Android actions with explicit effect verification.
- FILE_TOOL: local attachment extraction/transformation.
- FALLBACK_HANDOFF: external app/browser handoff only as degraded fallback; never marketed as full autonomy.

## Shipping gate
A release may not be called FINAL, PROMOTED, ChatGPT-like, autonomous-complete, or end-user ready unless:
- at least one GENERAL_CHAT DIRECT_MODEL engine is configured and field-proven;
- ordinary non-local chat returns into Hakim without requiring the user to switch apps;
- multi-turn conversation can call the direct engine repeatedly;
- attachments are passed through the direct engine or a compatible tool and the result comes back into Hakim;
- provider failure produces bounded failover or a precise blocker, not an endless loop;
- the same signed APK passes field acceptance.

## Commercial/identity boundary
A consumer ChatGPT login is identity/product access, not an OpenAI API entitlement. Hakim must not scrape or repurpose consumer sessions as an unofficial API. Direct model inference must use a provider-supported API/SDK/authorized integration, or a local model.

## Superiority target
Do not claim universal model superiority. Hakim may compete by combining:
- one interface across multiple engines;
- stronger task continuation and verification;
- Android/local execution;
- privacy-aware local-first routing;
- model/tool failover;
- user-specific workflows.
Absolute superiority over every model, benchmark and product surface is not a valid acceptance claim.
