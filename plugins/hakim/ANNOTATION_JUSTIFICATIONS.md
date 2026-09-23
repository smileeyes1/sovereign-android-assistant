# Hakim MCP annotation justifications

| Tool | readOnlyHint | Why | openWorldHint | Why | destructiveHint | Why |
| --- | --- | --- | --- | --- | --- | --- |
| status | true | Reads status from the authenticated Hakim device only. | false | Scope is one paired private device. | false | No state is changed. |
| ui | true | Reads the current UI tree only. | false | Scope is one paired private device. | false | No state is changed. |
| notifications | true | Reads notifications already exposed by Android permission. | false | Scope is one paired private device. | false | No notification is dismissed or changed. |
| screenshot | true | Returns current screen evidence when Android permission allows it. | false | Source is one paired private device. | false | Capture does not modify device state. |
| check_request | true | Reads the result of an existing request without replaying it. | false | Scope is the authenticated device request channel. | false | It creates no new action. |
| launch | false | Requests opening an app or URL after approval. | true | A URL can point to an external destination. | false | It is limited to opening a target and does not itself delete or overwrite data. |
| action | false | Requests a bounded UI action after approval. | true | The UI action may involve an external app or service. | false | The tool itself is bounded and approval-gated; consequential outcomes remain subject to safety and confirmation gates. |

If behavior changes, update the annotations and these justifications together, then rerun Scan Tools before publication.
