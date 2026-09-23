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

### P5 — Check prior request
**Prompt:** Check the result of Hakim request ID <fixture-request-id>.  
**Expected behavior:** Use `get_request_result` only; do not replay the original action.  
**Expected result:** Completed/pending result for the same request ID.

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

## Reviewer fixture requirement

A dedicated Hakim Android review fixture is still required before final submission. It must:
- pair without MFA/SMS/email confirmation;
- contain no personal user data;
- permit read tests and approval-gated write tests;
- be resettable between review runs.

**Status:** NOT YET PROVISIONED. Do not submit for review until this fixture exists.

## Domain verification

When the OpenAI submission portal provides a domain token, set it only as the Railway environment variable:

`OPENAI_APPS_CHALLENGE=<portal-token>`

The service exposes it at:

`/.well-known/openai-apps-challenge`

Do not commit the token to GitHub.

## Release notes

Initial public-submission candidate. Adds an OAuth 2.1 + PKCE MCP bridge that uses ChatGPT as the intelligence layer and Hakim as an encrypted, permission-gated Android execution arm. Read and write capabilities are explicitly separated; state-changing operations retain device approval gates.
