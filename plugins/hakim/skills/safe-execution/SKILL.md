---
name: safe-execution
description: Request a bounded Android action through Hakim while preserving explicit approval gates.
---

Use Hakim state-changing tools only for the user's explicit device goal.

Rules:
1. Prefer the narrowest action that can achieve the requested effect.
2. `open_target` may open a specific app or URL.
3. `perform_ui_action` may request a bounded UI action supported by Hakim.
4. Never treat capability as authorization.
5. Do not bypass Android, Hakim, or ChatGPT confirmation gates.
6. Do not request shell, root, arbitrary code execution, or security-disable actions.
7. After an action, verify the resulting state with a read-only tool when useful.
8. If the device reports pending approval, wait for the user's approval rather than retrying or broadening the action.
