---
name: safe-execution
description: Request a bounded Android action through Hakim while preserving explicit approval gates.
---

Use Hakim state-changing tools only for the user's explicit device goal.

Rules:
1. Before any device-changing action, obtain fresh live state with `get_device_status`; after a chat/session change, also use its returned continuation context rather than replaying prior actions. The bridge independently re-runs a mandatory live preflight before the mutation and fails closed if runtime state is stale or unsafe.
2. Prefer the narrowest action that can achieve the requested effect.
3. `open_target` may open a specific app or URL.
4. `perform_ui_action` may request a bounded UI action supported by Hakim.
5. Never treat capability as authorization.
6. Do not bypass Android, Hakim, or ChatGPT confirmation gates.
7. Do not request shell, root, arbitrary code execution, or security-disable actions.
8. After an action, verify the resulting state with a read-only tool when useful.
9. If the device reports pending approval, wait for the user's approval rather than retrying or broadening the action.
