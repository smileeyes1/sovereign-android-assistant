# Tool annotation justifications

## get_device_status
- readOnlyHint: true — retrieves only a minimal connection/status summary and changes no device or server state.
- openWorldHint: false — accesses only the single bounded device paired to the authenticated Hakim credential.
- destructiveHint: false — performs no write, deletion, send, or irreversible action.

## open_target
- readOnlyHint: false — requests a visible state change on the paired Android device.
- openWorldHint: true — when given an HTTP/HTTPS URL it may open an external public internet destination; the package form may also open an installed app.
- destructiveHint: false — opening an app or link is visible and state-changing but does not itself delete, send, purchase, or commit a transaction; Android approval is still required.

## navigate_device
- readOnlyHint: false — requests Home, Back, or Recents navigation, changing visible device state.
- openWorldHint: false — navigation is confined to the paired Android device and does not contact an open-ended external entity by itself.
- destructiveHint: false — Home/Back/Recents navigation is reversible and does not delete or overwrite data; Android approval remains required.

## get_request_result
- readOnlyHint: true — checks status of an existing operation without replaying it.
- openWorldHint: false — reads only the bounded paired-device operation state.
- destructiveHint: false — does not cause a new action or mutate device state.
