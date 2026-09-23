from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BASELINE = ROOT / "governance/LAST_VERIFIED_BASELINE.md"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"ROUTER_GATE=FAIL reason={label}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"ROUTER_GATE=FAIL reason={label}")


baseline = BASELINE.read_text(encoding="utf-8")
router = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
attachments = (APP / "HakimAttachmentGateway.kt").read_text(encoding="utf-8")
center = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")

require(
    baseline,
    "c84359e422dad0aa205f59a153a1f29d7f4c4e81",
    "verified_baseline_missing",
)
require(router, "LOCAL_BROWSER", "browser_channel_missing")
require(router, "PROVIDER_APP", "provider_app_channel_missing")
require(router, "SYSTEM_SHARE", "system_share_channel_missing")
require(router, "PROVIDER_WEB", "provider_web_channel_missing")

for provider in [
    "com.openai.chatgpt",
    "com.google.android.apps.bard",
    "com.anthropic.claude",
    "com.deepseek.chat",
]:
    require(router, provider, f"provider_missing:{provider}")
    require(manifest, provider, f"package_visibility_missing:{provider}")

require(router, "needsFreshWeb", "fresh_web_policy_missing")
require(router, "recordOutcome", "observed_outcome_learning_missing")
require(router, "coerceIn(-20, 40)", "bounded_learning_missing")
require(attachments, "Intent.FLAG_GRANT_READ_URI_PERMISSION", "scoped_share_missing")

require(attachments, "Intent.ACTION_OPEN_DOCUMENT", "document_picker_missing")
require(attachments, "Intent.EXTRA_ALLOW_MULTIPLE", "multi_pick_missing")
require(attachments, "Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION", "persistable_uri_missing")
require(attachments, "Intent.ACTION_SEND_MULTIPLE", "multi_share_missing")
require(attachments, "ClipData.newUri", "clipdata_uri_grant_missing")

require(manifest, "android.intent.action.SEND_MULTIPLE", "manifest_multi_share_missing")
require(center, "HakimModelToolRouter.decide", "router_not_wired")
require(center, "HakimAttachmentGateway.pickerIntent", "attachment_picker_not_wired")
require(center, "sendToProviderApp", "provider_dispatch_missing")
require(center, "openProviderWeb", "provider_web_fallback_missing")

for forbidden in [
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "GEMINI_API_KEY",
    "DEEPSEEK_API_KEY",
    "sk-proj-",
]:
    forbid(router + attachments + center, forbidden, f"paid_or_secret_dependency:{forbidden}")

# Known-failure sentinel: if this deliberately impossible provider appears,
# the gate must fail. This proves the check is not a no-op.
sentinel = "com.example.hakim.known.failure"
try:
    require(router, sentinel, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("ROUTER_GATE=FAIL reason=known_failure_not_detected")

print("ROUTER_GATE=PASS baseline=c84359e providers=4 attachments=unified")
