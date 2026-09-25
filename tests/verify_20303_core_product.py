from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
REGISTRY = (APP / "HakimEngineRegistry.kt").read_text(encoding="utf-8")
PDF = (APP / "HakimPdfVisualAdapter.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

def req(value, reason):
    if not value:
        raise SystemExit("CORE_PRODUCT_20303=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20303, "version")
req("3.2.0-core-product-v1" in BUILD, "version_name")

for token in [
    "PdfRenderer",
    "MAX_PAGES_PER_PDF = 8",
    "MAX_TOTAL_PAGES = 12",
    "MAX_TOTAL_DERIVED_BYTES",
    "renderedPageCount",
    "الأصل بقي محفوظًا ومرجعيًا",
]:
    req(token in PDF, "pdf:" + token)

req("bestImageGeneralChat" in REGISTRY, "image_engine_selector_missing")
req("HakimPdfVisualAdapter.canAdapt(attachments)" in CENTER, "pdf_adapter_not_connected")
req("HakimPdfVisualAdapter.adapt(this, sourceSnapshot)" in CENTER, "pdf_adapter_not_executed")
req("deliveryAttachments = visual.attachments" in CENTER, "pdf_pages_not_delivered_in_app")
req('actionButton("مشاركة")' in CENTER, "manual_share_button_missing")
req("المرفق محفوظ داخل حكيم" in CENTER, "manual_handoff_state_missing")
req("المرفقات محفوظة — لم تُرسل خارجيًا" in CENTER, "retry_external_handoff_regression")

attachment_start = ROUTER.index("if (attachments.isNotEmpty())")
attachment_end = ROUTER.index("if (HakimFreePolicy.freeOnly(context))", attachment_start)
attachment_block = ROUTER[attachment_start:attachment_end]
req("FREE_ENGINE_SETUP" in attachment_block, "free_setup_before_handoff_missing")
req("directEngines(context).isEmpty()" in attachment_block, "setup_not_scoped_to_zero_engines")
req("SYSTEM_SHARE" in attachment_block, "manual_share_fallback_missing")
req("requiresUserChoice = true" in attachment_block, "external_share_not_explicit")

req("python3 tests/verify_20303_core_product.py" in WORKFLOW, "workflow_gate_missing")
print("CORE_PRODUCT_20303=PASS in_app_first=true pdf_visual_full=true external_share=manual free_setup=one_time")
