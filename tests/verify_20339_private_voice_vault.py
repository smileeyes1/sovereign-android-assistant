from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MAIN_MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
CONSUMER_MANIFEST = (ROOT / "app/src/consumer/AndroidManifest.xml").read_text(encoding="utf-8")
STORE = (APP / "HakimVoiceProfileStore.kt").read_text(encoding="utf-8")
ACTIVITY = (APP / "HakimVoiceProfileActivity.kt").read_text(encoding="utf-8")
SETTINGS = (APP / "UnifiedHomeActivity.kt").read_text(encoding="utf-8")
REGISTRY = (APP / "HakimCapabilityRegistry.kt").read_text(encoding="utf-8")
PORTABLE = (APP / "HakimPortableState.kt").read_text(encoding="utf-8")
PRIVACY = (ROOT / "governance/PRIVACY_DATA_MAP.md").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok, reason):
    if not ok:
        raise SystemExit("PRIVATE_VOICE_VAULT_20339=FAIL reason=" + reason)


m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20339, "version_floor")
req("product-foundation-v1-private-voice-vault-r29" in BUILD, "lineage")

for token in [
    "VOICE-PROFILE-VAULT-2026-10-06-v1",
    "context.noBackupFilesDir",
    "AndroidKeyStore",
    "AES/GCM/NoPadding",
    "MAX_BYTES = 16 * 1024 * 1024",
    "VOICE_PROFILE_TOO_LARGE",
    "VOICE_PROFILE_NOT_AUDIO",
    "bytes.fill(0)",
    "fd.sync()",
    "fun delete(context: Context)",
]:
    req(token in STORE, "store:" + token)

for forbidden in [
    "HttpURLConnection",
    "OkHttp",
    "Firebase",
    "github.com",
    "ACTION_SEND",
]:
    req(forbidden not in STORE, "store_external_path:" + forbidden)

for token in [
    'setTitle("تأكيد ملكية الصوت")',
    "أؤكد أن العينة صوتي أنا أو أنني أملك الحق في استخدامها",
    "MediaStore.Audio.Media.RECORD_SOUND_ACTION",
    "Intent.ACTION_OPEN_DOCUMENT",
    'type = "audio/*"',
    "HakimVoiceProfileStore.saveFromUri",
    "HakimVoiceProfileStore.materializePreview",
    "HakimVoiceProfileStore.delete",
]:
    req(token in ACTIVITY, "activity:" + token)

# Consumer must not regain direct microphone permission merely to keep a voice sample.
req("Manifest.permission.RECORD_AUDIO" not in ACTIVITY, "activity_direct_mic_permission")
req(
    'android.permission.RECORD_AUDIO' in CONSUMER_MANIFEST
    and 'tools:node="remove"' in CONSUMER_MANIFEST,
    "consumer_mic_permission_not_removed",
)

req('android:name=".HakimVoiceProfileActivity"' in MAIN_MANIFEST, "manifest_activity")
block = MAIN_MANIFEST.split('android:name=".HakimVoiceProfileActivity"', 1)[1].split("/>", 1)[0]
req('android:exported="false"' in block, "activity_exported")

req('button("صوتي في حكيم", primary = true)' in SETTINGS, "settings_entry")
req("لا تدخل تلقائيًا في السحابة أو GitHub أو نسخة الاستقلال" in SETTINGS, "privacy_copy")
req('Contract("voice.profile.local"' in REGISTRY, "registry")
req('"app.launch", "voice.profile.local" -> true' in REGISTRY, "registry_availability")
req("عينة صوت المالك الاختيارية" in PRIVACY, "privacy_map")

# Portable state only contains an explicit allowlist unrelated to the voice profile.
req("hakim_voice_profile_meta_v1" not in PORTABLE, "portable_meta_export")
req("voice-profile" not in PORTABLE, "portable_file_export")

candidate = STATE["android"]["candidate"]
req(candidate["version_code"] == int(m.group(1)), "state_version")
req(candidate["field_verified"] is False, "no_field_inheritance")
req(candidate["same_signed_apk_field_verified"] is False, "no_same_artifact_inheritance")

product = STATE["productization"]
req(product["candidate_version"] == int(m.group(1)), "product_candidate")
req(product["private_voice_vault_r29_source_integrated"] is True, "source_integrated")
req(product["private_voice_vault_r29_source_ci_verified"] is False, "source_ci_must_start_false")
req(product["private_voice_vault_r29_field_verified"] is False, "field_must_start_false")
req(product["voice_sample_external_upload_default"] is False, "external_upload_default")
req(product["voice_sample_portable_state_export"] is False, "portable_export")

req(PROMOTION["candidate_version"] == int(m.group(1)), "promotion_candidate")
req(PROMOTION["promoted"] is False, "promotion_false")
req(PROMOTION["private_voice_vault_r29_field_verified"] is False, "promotion_field_false")

req("python3 tests/verify_20339_private_voice_vault.py" in WORKFLOW, "workflow_gate")

print(
    "PRIVATE_VOICE_VAULT_20339=PASS "
    "encrypted_local_only=true owner_confirmation=true no_auto_upload=true "
    "consumer_direct_mic_permission=false portable_export=false field_inheritance=false"
)
