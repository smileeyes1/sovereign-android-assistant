# Hakim MCP annotation justifications

These justifications accompany the annotations advertised by the production MCP server.

| Tool | readOnlyHint | Why | openWorldHint | Why | destructiveHint | Why |
| --- | --- | --- | --- | --- | --- | --- |
| get_device_status | true | Reads bounded runtime/capability status from the authenticated Hakim device only. | false | Scope is one paired private device. | false | No external or device state is changed. |
| get_current_ui | true | Reads the current accessibility/UI tree only. | false | Scope is one paired private device. | false | No external or device state is changed. |
| list_notifications | true | Reads notifications already exposed to Hakim by Android permission. | false | Scope is one paired private device. | false | It does not dismiss, alter, or send notifications. |
| capture_screenshot | true | Returns current screen evidence when Android permission allows it. | false | Source is one paired private device. | false | Capture does not modify device state. |
| get_request_result | true | Reads an existing request result and never replays the original operation. | false | Scope is the authenticated device request channel. | false | It creates no new action. |
| open_target | false | Requests opening exactly one app package or HTTP/HTTPS URL after approval. | true | A URL may point to an open-ended external internet destination. | false | Opening a target changes visible state but does not itself delete or overwrite user data. |
| perform_ui_action | false | Requests one bounded Android UI action after approval. | true | The action may interact with an external app or service displayed on the device. | true | Click, tap, text entry, or gesture actions can cause consequential or hard-to-reverse effects depending on the current UI, so the tool is conservatively marked destructive and remains approval-gated. |

Supported `perform_ui_action` kinds are fixed to: `home`, `back`, `recents`, `notifications`, `quick_settings`, `click_text`, `set_text`, `tap`, and `swipe`.

If behavior changes, update the server annotations and these justifications together, then rerun Scan Tools before publication.
