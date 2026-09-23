# Hakim — Public Plugin Submission Packet

## Listing

- **Name:** Hakim
- **Category:** Productivity
- **Short description:** Connect ChatGPT to Hakim
- **Long description:** Hakim connects ChatGPT to an authorized Android device through a privacy-minimized execution bridge. The public plugin can check device connectivity, request opening a specific app or HTTP/HTTPS link, navigate Home/Back/Recents, and check whether a prior request completed. It does not expose raw screenshots, notifications, arbitrary taps, free-form text entry, shell, or root. State-changing actions remain behind Hakim/Android approval gates. ChatGPT remains the conversational intelligence layer and the bridge does not require an OpenAI API key.
- **Website:** https://hakim-chatgpt-bridge-production.up.railway.app
- **Support:** https://hakim-chatgpt-bridge-production.up.railway.app/support
- **Privacy:** https://hakim-chatgpt-bridge-production.up.railway.app/privacy
- **Terms:** https://hakim-chatgpt-bridge-production.up.railway.app/terms
- **MCP endpoint:** https://hakim-chatgpt-bridge-production.up.railway.app/mcp
- **MCP URL type:** Universal
- **Authentication:** OAuth 2.1 authorization code + PKCE (S256), with reviewer fixture credentials
- **Public tool surface:** get_device_status, open_target, navigate_device, get_request_result

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
**Expected result:** `approval_requested` plus a request identifier. Reviewer mode performs no real device action.

### P3 — Open an HTTPS link
**Prompt:** Use Hakim to open https://example.com on my authorized Android device.  
**Expected behavior:** Use `open_target` with the HTTPS URL and preserve the device approval gate.  
**Expected result:** `approval_requested` plus a request identifier; non-HTTP(S) schemes are not accepted.

### P4 — Safe navigation
**Prompt:** Use Hakim to go back one screen on my authorized Android device.  
**Expected behavior:** Use `navigate_device` with `kind=back`.  
**Expected result:** `approval_requested`; public navigation is limited to home/back/recents.

### P5 — Check an existing request
**Prompt:** Use Hakim to check whether request `review-12345678` completed.  
**Expected behavior:** Use `get_request_result` without replaying the original request.  
**Expected result:** A redacted completion state containing request id/status only; no raw device content.

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
- **Username:** `openai-reviewer`
- **Password:** `Hakim-Review-Demo-Only-2026`
- no MFA, SMS, email confirmation, or private-network access is required;
- the credential is intentionally limited to a synthetic demo fixture and cannot access a real device;
- status returns clearly labeled demo + redacted data;
- opening or navigation returns `approval_requested` but performs no external action;
- reviewer login is rate-limited;
- the fixture is enabled only when `HAKIM_PUBLIC_REVIEW_DEMO=1`.

These credentials are reviewer/demo credentials, not user credentials and not a path to real device data.

## Domain verification

When the OpenAI submission portal provides a domain token, set it only as the Railway environment variable:

`OPENAI_APPS_CHALLENGE=<portal-token>`

The service exposes exactly that value at:

`/.well-known/openai-apps-challenge`

Do not commit the token to GitHub.

## Public safety profile

Production submission uses `HAKIM_PUBLIC_SAFE=1` and `HAKIM_PUBLIC_REVIEW_DEMO=1`. The public catalog must contain exactly:
- `get_device_status`
- `open_target`
- `navigate_device`
- `get_request_result`

Private/development capabilities require explicit `HAKIM_PUBLIC_SAFE=0` and are not part of the public submission.

## Content security policy

No custom plugin UI component is included in this MCP-only submission, so there are no component fetch domains and no UI CSP allowlist is required.

## Release notes

Initial public-submission candidate for the privacy-minimized Hakim bridge. Adds OAuth 2.1 + PKCE, encrypted device relay, synthetic reviewer access, and a restricted public tool surface that uses ChatGPT as the intelligence layer while retaining explicit Android approval gates.
