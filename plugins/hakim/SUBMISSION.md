# Hakim — Public Plugin Submission Packet

## Listing

- **Name:** Hakim
- **Category:** Productivity
- **Short description:** Connect ChatGPT to Hakim
- **Long description:** Hakim uses `resume_hakim` as the canonical first read in a new chat/model/session, combining fresh device preflight, durable continuation, bridge identity, and current video capabilities. HAKIM FILM OS adds durable film projects with world/character locks, shot contracts, provider-neutral routing, QA gates, and a grade-one number ٥ Golden Production. Creating/restoring a film project does not itself render media or spend money. The public plugin can also check device connectivity, open selected apps or HTTP/HTTPS links, navigate Home/Back/Recents, and check prior operation results. Raw screenshots, notifications, arbitrary taps, free-form text entry, shell, and root are not exposed.
- **Website:** https://hakim-chatgpt-bridge-production.up.railway.app
- **Support:** https://hakim-chatgpt-bridge-production.up.railway.app/support
- **Privacy:** https://hakim-chatgpt-bridge-production.up.railway.app/privacy
- **Terms:** https://hakim-chatgpt-bridge-production.up.railway.app/terms
- **MCP endpoint:** https://hakim-chatgpt-bridge-production.up.railway.app/mcp
- **MCP URL type:** Universal
- **Authentication:** OAuth 2.1 authorization code + PKCE (S256), with reviewer fixture credentials
- **Public tool surface:** resume_hakim, get_device_status, get_video_capabilities, plan_video_project, get_film_os_capabilities, create_film_project, get_film_project, open_target, navigate_device, get_continuation_state, save_continuation_checkpoint, get_request_result

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
**Expected behavior:** Use `resume_hakim` first. If an operation token is pending, use `get_request_result` with that same token. Never replay the original state-changing request merely because the chat changed.  
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
- `resume_hakim`
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

### resume_hakim
- `readOnlyHint=true`: combines live preflight, durable continuation, bridge identity, and video capability metadata without replaying work.
- `openWorldHint=false`: it reads only the paired Hakim runtime and bridge-local state.
- `destructiveHint=false`: it performs no mutation or rendering.

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
- `readOnlyHint=true`: reads Film OS capability metadata only.
- `openWorldHint=false`: it does not invoke a renderer or outside provider.
- `destructiveHint=false`: it changes no project or device state.

### create_film_project
- `readOnlyHint=false`: creates a bounded project record in Hakim durable storage.
- `openWorldHint=false`: project creation stays inside the Hakim bridge and does not invoke a renderer.
- `destructiveHint=false`: it does not overwrite another project, render media, spend credits, or mutate the phone.
- `idempotentHint=true`: the operation has no external side effect; each project receives a new isolated identifier.

### get_film_project
- `readOnlyHint=true`: reads one durable Film OS project by its project identifier.
- `openWorldHint=false`: it reads only Hakim bridge-local project storage.
- `destructiveHint=false`: it performs no write or external action.

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
