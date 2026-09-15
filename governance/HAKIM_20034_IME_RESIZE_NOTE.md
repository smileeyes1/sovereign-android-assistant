# HAKIM 20034 — IME resize field fix

Field symptom: when the Android keyboard opens in the main Hakim chat, the composer can be covered and typed text is not visible until the keyboard is dismissed.

Fix contract:
- Main chat activity declares `android:windowSoftInputMode="adjustResize"`.
- Conversation area must resize while the IME is visible so the composer remains visible.
- Keep package `ps.hakim.stable` and D1 signing lineage.
- Do not uninstall or clear app data.
- CI/build success is not the same as physical phone verification.
