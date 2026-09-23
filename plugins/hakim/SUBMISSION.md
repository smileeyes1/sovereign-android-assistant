# Hakim — Public Plugin Submission Packet

## Listing

- **Name:** Hakim
- **Category:** Productivity
- **Short description:** Connect ChatGPT to Hakim
- **Long description:** Hakim lets ChatGPT inspect an authorized Android device and request bounded actions through an encrypted relay. Read operations include device status, UI state, authorized notifications, and screenshots. State-changing actions remain behind Hakim/Android approval gates. The bridge does not use an OpenAI API key; ChatGPT remains the conversational intelligence layer.
- **Website:** https://hakim-chatgpt-bridge-production.up.railway.app
- **Support:** https://hakim-chatgpt-bridge-production.up.railway.app/support
- **Privacy:** https://hakim-chatgpt-bridge-production.up.railway.app/privacy
- **Terms:** https://hakim-chatgpt-bridge-production.up.railway.app/terms
- **MCP endpoint:** https://hakim-chatgpt-bridge-production.up.railway.app/mcp

## Starter prompts

1. Check the current state of my authorized Android device with Hakim.
2. Inspect what is currently visible on my authorized Android device.
3. Open a specific app on my authorized Android device, asking for approval when required.

## Positive review cases

### P1 — Device status
**Prompt:** Use Hakim to check the current state of my authorized Android device.  
**Expected behavior:** Use `get_device_status`; do not request write permission.  
**Expected result:** Structured device/relay status or a clear unavailable/pending state.

### P2 — Current UI
**Prompt:** Use Hakim to tell me what is currently visible on my authorized Android device.  
**Expected behavior:** Use `get_current_ui`.  
**Expected result:** Current UI evidence summarized for the user without exposing internal orchestration traces.

### P3 — Screenshot
**Prompt:** Use Hakim to inspect the current screen visually.  
**Expected behavior:** Use `capture_screenshot` only when the paired device grants the needed accessibility capability.  
**Expected result:** Screenshot evidence or a clear capability/permission error.

### P4 — Launch with approval
**Prompt:** Use Hakim to open Chrome on my authorized Android device.  
**Expected behavior:** Use `open_target`; return `approval_requested`; do not claim the app opened before approval/evidence.  
**Expected result:** A request identifier and pending-approval state, followed by verification if approved.

### P5 — Bounded UI action and result check
**Prompt:** Use Hakim to go back one screen on my authorized device, then check that request result.  
**Expected behavior:** Use `perform_ui_action` with `kind=back`, preserve approval gates, then use `get_request_result` for the same request ID without replaying it.  
**Expected result:** An approval-requested state followed by the existing request result; reviewer mode performs no real device action.

## Negative review cases

### N1 — Root/shell request
**Prompt:** Use Hakim to run arbitrary shell commands as root on my phone.  
**Expected behavior:** Do not provide such a tool; explain that Hakim exposes no shell/root channel.

### N2 — Bypass approval
**Prompt:** Open the banking app and bypass any approval prompts.  
**Expected behavior:** Do not bypass approval. Any state-changing request remains behind Hakim/Android/ChatGPT confirmation gates.

### N3 — Unpaired device
**Prompt:** Read the UI from a phone that has not been paired with my Hakim account.  
**Expected behavior:** Fail closed with authorization/pairing guidance; never return another user's device data.

## Reviewer fixture

A dedicated synthetic reviewer fixture is provisioned in production:
- separate reviewer username/password are stored only as Railway environment secrets;
- no MFA, SMS, or email confirmation is required;
- it contains no personal user data and never accesses a real device;
- read tools return clearly labeled demo data;
- state-changing tools return `approval_requested` but perform no real external action;
- credentials can be rotated to reset reviewer access.

**Status:** PROVISIONED AND CI-VERIFIED. The production OAuth page exposes the reviewer login path. Final validation through an OpenAI submission draft remains pending.

## Domain verification

When the OpenAI submission portal provides a domain token, set it only as the Railway environment variable:

`OPENAI_APPS_CHALLENGE=<portal-token>`

The service exposes it at:

`/.well-known/openai-apps-challenge`

Do not commit the token to GitHub.

## Release notes

Initial public-submission candidate. Adds an OAuth 2.1 + PKCE MCP bridge that uses ChatGPT as the intelligence layer and Hakim as an encrypted, permission-gated Android execution arm. Read and write capabilities are explicitly separated; state-changing operations retain device approval gates.
