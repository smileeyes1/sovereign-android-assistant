from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
PORTABLE = (APP / "HakimPortableState.kt").read_text(encoding="utf-8")
HOME = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")

def require(text: str, needle: str, reason: str) -> None:
    if needle not in text:
        raise SystemExit("PORTABLE_STATE_GATE=FAIL reason=" + reason)

def forbid(text: str, needle: str, reason: str) -> None:
    if needle in text:
        raise SystemExit("PORTABLE_STATE_GATE=FAIL reason=" + reason)

for needle, reason in [
    ("HAKIM-PORTABLE-NONSECRET-STATE-2026-10-02-v1", "version"),
    ("SCHEMA_VERSION = 1", "schema"),
    ("MAX_BYTES = 128 * 1024", "size_bound"),
    ("SAFE_PREF_KEYS", "allowlist"),
    ('"hakim_cost_policy"', "cost_policy_missing"),
    ('"hakim_router"', "router_policy_missing"),
    ('.put("device_bound_secrets_exported", false)', "secret_export_claim"),
    ('.put("pairing_exported", false)', "pairing_export_claim"),
    ('.put("conversation_exported", false)', "conversation_export_claim"),
    ('.put("task_content_exported", false)', "task_export_claim"),
    ("HakimFreePolicy.setFreeOnly(context, true)", "free_only_restore_gate"),
    ("GeminiDirectEngine.SECRET_GEMINI_KEY", "gemini_local_credential_gate"),
    ("n !in -20..40", "provider_score_bound"),
    ("portable_state_fingerprint_mismatch", "fingerprint_gate"),
    ("validateShape(prefs)", "shape_gate"),
]:
    require(PORTABLE, needle, reason)

for needle, reason in [
    ("Intent.ACTION_CREATE_DOCUMENT", "create_document_missing"),
    ("Intent.ACTION_OPEN_DOCUMENT", "open_document_missing"),
    ("HakimPortableState.exportToUri(this, uri)", "export_not_wired"),
    ("HakimPortableState.importFromUri(this, uri)", "import_not_wired"),
    ('type = "application/json"', "json_mime_missing"),
]:
    require(HOME, needle, reason)

# The portable bundle must never contain identity, pairing, command transport, conversation,
# task content, or AndroidKeyStore vault material.
for forbidden, reason in [
    ('"hakim_secret_vault"', "secret_vault_exported"),
    ('"relay_topic"', "relay_topic_exported"),
    ('"relay_result_topic"', "relay_result_topic_exported"),
    ('"relay_hmac_key"', "legacy_relay_secret_exported"),
    ('"command_topic"', "command_topic_exported"),
    ('"result_topic"', "result_topic_exported"),
    ('"hakim_conversation"', "conversation_exported"),
    ('"hakim_task_manager"', "task_manager_exported"),
    ("getAllSharedPreferences", "broad_preferences_export"),
]:
    forbid(PORTABLE, forbidden, reason)

# No broad storage permission or direct raw path writes are allowed for this feature.
for forbidden, reason in [
    ("MANAGE_EXTERNAL_STORAGE", "broad_storage_permission"),
    ("WRITE_EXTERNAL_STORAGE", "legacy_write_storage_permission"),
    ("Environment.getExternalStorageDirectory", "raw_external_storage"),
]:
    forbid(PORTABLE + HOME, forbidden, reason)

# Known-failure sentinel: the gate must reject a deliberately injected secret preference.
probe = PORTABLE + '\nprivate val leak = "relay_hmac_key"\n'
try:
    forbid(probe, '"relay_hmac_key"', "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("PORTABLE_STATE_GATE=FAIL reason=sentinel_not_detected")

print("PORTABLE_STATE_GATE=PASS scope=nonsecret allowlist=true saf=true fingerprint=true")
