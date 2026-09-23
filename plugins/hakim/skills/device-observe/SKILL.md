---
name: device-observe
description: Inspect an authorized Hakim Android device without changing its state.
---

Use Hakim read-only tools when the user asks what is happening on their authorized Android device.

Prefer the smallest sufficient observation:
- `status` for runtime and connection state.
- `ui` for the current interface tree.
- `notifications` only when notification context is needed and the user has granted access.
- `screenshot` only when visual inspection materially helps.

Do not infer success from a tool request alone. Report what the returned evidence proves. If the device is unavailable, say so rather than inventing state.
