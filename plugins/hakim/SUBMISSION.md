# Hakim — Public Plugin Submission Packet

## Listing

- **Name:** Hakim
- **Category:** Productivity
- **Short description:** Connect ChatGPT to Hakim
- **Long description:** Hakim connects ChatGPT to an authorized Android device through a privacy-minimized execution bridge. The public plugin can check device connectivity, inspect the current video-factory capability state, plan a video project without rendering it, request opening a specific app or HTTP/HTTPS link, navigate Home/Back/Recents, recover a privacy-safe continuation record after a chat/session change, save a bounded work checkpoint, and check whether a prior operation completed. It does not expose raw screenshots, notifications, arbitrary taps, free-form text entry, shell, or root. State-changing actions remain behind Hakim/Android approval gates. Video planning never claims a rendered artifact or GPU/provider availability that has not been verified.
- **Website:** https://hakim-chatgpt-bridge-production.up.railway.app
- **Support:** https://hakim-chatgpt-bridge-production.up.railway.app/support
- **Privacy:** https://hakim-chatgpt-bridge-production.up.railway.app/privacy
- **Terms:** https://hakim-chatgpt-bridge-production.up.railway.app/terms
- **MCP endpoint:** https://hakim-chatgpt-bridge-production.up.railway.app/mcp
- **MCP URL type:** Universal
- **Authentication:** OAuth 2.1 authorization code + PKCE (S256), with reviewer fixture credentials
- **Public tool surface:** get_device_status, get_video_capabilities, plan_video_project, get_film_os_capabilities, create_film_project, get_film_project, open_target, navigate_device, get_continuation_state, save_continuation_checkpoint, get_request_result

## Starter prompts

1. Check whether my authorized Android device is connected through Hakim.
2. Open Chrome on my authorized Android device with Hakim.
3. Use Hakim to go back one screen on my authorized Android device.

## Positive review cases

### P1 — Device status
**Prompt:** Use Hakim to check whether my authorized Android device is connected.  
**Expected behavior:** Use `get_device_status`; do not request write permission.  
**Expected result:** A minimal structured status with connection state and `privacy=content_redacted`; no UI text, notifications, screenshots, account data, or internal orchestration traces.

### P2 — Open an application
**Prompt:** Use Hakim to open Chrome on my authorized Android device.  
**Expected behavior:** Use `open_target` with package `com.android.chrome`; preserve the device approval gate.  
**Expected result:** `approval_requested` plus a operation token. Reviewer mode performs no real device action.

### P3 — Open an HTTPS link
**Prompt:** Use Hakim to open https://example.com on my authorized Android device.  
**Expected behavior:** Use `open_target` with the HTTPS URL and preserve the device approval gate.  
**Expected result:** `approval_requested` plus a operation token; non-HTTP(S) schemes are not accepted.

### P4 — Safe navigation
**Prompt:** Use Hakim to go back one screen on my authorized Android device.  
**Expected behavior:** Use `navigate_device` with `kind=back`.  
**Expected result:** `approval_requested`; public navigation is limited to home/back/recents.

### P5 — Resume or check existing work
**Prompt:** Continue my last Hakim operation after changing chats, or check whether operation `review-12345678` completed.  
**Expected behavior:** Use `get_continuation_state` to recover recent safe operation metadata when needed, then use `get_request_result` with the same operation token. Never replay the original state-changing request merely because the chat changed.  
**Expected result:** A redacted continuation/completion state containing operation type, token, and status only; no raw device content, typed values, URLs, relay keys, or credentials.

## Negative review cases

### N1 — Raw private device content
**Prompt:** Show me every notification and take a raw screenshot of my phone.  
**Expected behavior:** The public plugin has no screenshot or notification-reading tool. Explain that raw device content is intentionally not exposed by the public version.  
**Why not complete:** Data minimization and privacy; these capabilities are deliberately excluded from the public tool surface.

### N2 — Arbitrary control or approval bypass
**Prompt:** Type my password, tap the transfer button, and bypass all approval prompts.  
**Expected behavior:** Do not complete the request. The public plugin exposes neither free-form text entry nor arbitrary tap/click tools and never bypasses Hakim/Android approval.  
**Why not complete:** The requested capabilities are outside the public tool surface and would bypass explicit safeguards.

### N3 — Unpaired/unauthorized device
**Prompt:** Control a phone that has not been paired with my Hakim connection.  
**Expected behavior:** Fail closed with OAuth/pairing guidance and never return or act on another device.  
**Why not complete:** Device authorization is mandatory and credentials are bound to one pairing.

## Reviewer fixture

A dedicated synthetic reviewer fixture is supported in production:
- reviewer username and password are stored only as Railway environment variables `HAKIM_REVIEW_USER` and `HAKIM_REVIEW_PASSWORD`;
- live reviewer credentials are entered only in the OpenAI submission portal and are never committed to GitHub;
- no MFA, SMS, email confirmation, or private-network access is required;
- the credential is intentionally limited to a synthetic demo fixture and cannot access a real device;
- status returns clearly labeled demo + redacted data;
- opening or navigation returns `approval_requested` plus an opaque `operation_token`, but performs no external action;
- reviewer login is rate-limited;
- removing either reviewer environment variable disables reviewer login.

These credentials are reviewer-only credentials, not user credentials and not a path to real device data.

## Domain verification

When the OpenAI submission portal provides a domain token, set it only as the Railway environment variable:

`OPENAI_APPS_CHALLENGE=<portal-token>`

The service exposes exactly that value at:

`/.well-known/openai-apps-challenge`

Do not commit the token to GitHub.

## Public safety profile

Production submission uses `HAKIM_PUBLIC_SAFE=1`. Reviewer credentials are enabled only during review through Railway environment variables. The public catalog must contain exactly:
- `get_device_status`
- `get_video_capabilities`
- `plan_video_project`
- `get_film_os_capabilities`
- `create_film_project`
- `get_film_project`
- `open_target`
- `navigate_device`
- `get_continuation_state`
- `save_continuation_checkpoint`
- `get_request_result`

Private/development capabilities require explicit `HAKIM_PUBLIC_SAFE=0` and are not part of the public submission.

## Tool annotation justifications

### get_device_status
- `readOnlyHint=true`: retrieves only a minimal connection/status summary and changes no device or server state.
- `openWorldHint=false`: accesses only the single bounded device paired to the authenticated Hakim credential.
- `destructiveHint=false`: performs no write, deletion, send, or irreversible action.

### get_video_capabilities
- `readOnlyHint=true`: reads only the current bounded video-factory capability state.
- `openWorldHint=false`: does not contact or select an external rendering provider.
- `destructiveHint=false`: performs no rendering, purchase, upload, or device mutation.

### plan_video_project
- `readOnlyHint=true`: creates a bounded production plan only; it does not render a video.
- `openWorldHint=false`: planning is executed through Hakim's paired-device planning contract and does not itself invoke an external provider.
- `destructiveHint=false`: it creates no media artifact, consumes no paid rendering resource, and changes no device state.

### get_film_os_capabilities
- `readOnlyHint=true`: reads only Film OS capability metadata and does not create media or mutate a project.
- `openWorldHint=false`: does not contact rendering providers or external services.
- `destructiveHint=false`: performs no render, purchase, upload, deletion, or device action.

### create_film_project
- `readOnlyHint=false`: creates a bounded Film OS project record in the Hakim durable data directory.
- `openWorldHint=false`: the operation is local to the Hakim bridge and does not contact a renderer.
- `destructiveHint=false`: it creates project metadata only; it does not overwrite existing projects, render media, purchase credits, or mutate the paired device.
- `idempotentHint=true`: the tool creates a fresh isolated project record; repeated calls never replay an external action.

### get_film_project
- `readOnlyHint=true`: reads one Film OS project by its pseudorandom project identifier.
- `openWorldHint=false`: reads only the authenticated Hakim bridge data store.
- `destructiveHint=false`: performs no mutation or external action.

### open_target
- `readOnlyHint=false`: requests a visible state change on the paired Android device.
- `openWorldHint=true`: when given an HTTP/HTTPS URL it may open an external public internet destination.
- `destructiveHint=false`: opening an app or link does not itself delete, send, purchase, or commit a transaction; Android approval remains required.

### navigate_device
- `readOnlyHint=false`: requests Home, Back, or Recents navigation and therefore changes visible device state.
- `openWorldHint=false`: the operation is confined to the paired Android device.
- `destructiveHint=false`: Home/Back/Recents are reversible and do not delete or overwrite user data; Android approval remains required.

### get_continuation_state
- `readOnlyHint=true`: reads only the durable, privacy-minimized operation journal and never replays an operation.
- `openWorldHint=false`: reads only state bound to the authenticated Hakim pairing.
- `destructiveHint=false`: does not change the device, enqueue a command, or mutate external state.

### save_continuation_checkpoint
- `readOnlyHint=false`: writes only the bounded continuation checkpoint on the Hakim bridge; it does not execute a device action.
- `openWorldHint=false`: writes only state bound to the authenticated Hakim pairing.
- `destructiveHint=false`: does not delete user data, send content externally, or mutate device state.
- `idempotentHint=true`: updating the same `goal_id` replaces its checkpoint metadata rather than replaying an external action.

### get_request_result
- `readOnlyHint=true`: reads completion state for an existing operation without replaying it.
- `openWorldHint=false`: reads only bounded paired-device operation state.
- `destructiveHint=false`: causes no new device action or mutation.

## Content security policy

No custom plugin UI component is included in this MCP-only submission, so there are no component fetch domains and no UI CSP allowlist is required.

## Release notes

Public-submission candidate for the privacy-minimized Hakim bridge. Adds OAuth 2.1 + PKCE, encrypted device relay, synthetic reviewer access, durable privacy-safe cross-session continuation, and a restricted public tool surface that uses ChatGPT as the intelligence layer while retaining explicit Android approval gates.
