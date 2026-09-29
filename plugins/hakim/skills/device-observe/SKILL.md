---
name: device-observe
description: Inspect an authorized Hakim Android device without changing its state.
---

Use Hakim read-only tools when the user asks what is happening on their authorized Android device.

For every Hakim-related task, especially after a new chat/session or when prior state may be stale, call `get_device_status` first. Treat its fresh live preflight plus returned continuation state as authoritative for that turn. Never treat a previous chat's description as current device truth when this live tool is available.

Prefer the smallest sufficient observation:
- `get_device_status` for runtime and connection state.
- `get_current_ui` for the current interface tree.
- `list_notifications` only when notification context is needed and the user has granted access.
- `capture_screenshot` only when visual inspection materially helps.

Do not infer success from a tool request alone. Report what the returned evidence proves. If the device is unavailable, say so rather than inventing state.
