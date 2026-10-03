#!/usr/bin/env python3
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
ev=json.loads((root/"governance/HAKIM_20334_FIELD_EVIDENCE.json").read_text(encoding="utf-8"))
promo=json.loads((root/"governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))

assert ev["candidate_version"]==20334
assert ev["package"]=="ps.hakim.stable"
assert ev["source"]["head"]=="a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"
assert ev["source"]["ci_run_number"]==1521
assert ev["source"]["ci_conclusion"]=="success"
assert ev["source"]["ci_artifact_id"]==11270247244
assert ev["source"]["unsigned_sha256"]=="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"
assert ev["signed_artifact"]["sha256"]=="ddacc815bfd5033bd41e6e7db0c498594c3a4aa6f95430c96e80f176ec3e9f4b"
assert ev["signed_artifact"]["certificate_sha256"]=="d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3"
assert ev["signed_artifact"]["local_apksigner_verified"] is True
assert ev["signed_artifact"]["signer_reproduction_control_byte_identical"] is True
assert ev["field"]["installed_version"]==20334
assert ev["field"]["installed_apk_sha256"]==ev["signed_artifact"]["sha256"]
assert ev["field"]["same_signed_apk_field_verified"] is True
assert ev["field"]["service_running"] is True
assert ev["field"]["self_check"]=="PASS"
assert ev["field"]["execution_fabric_online"] is True
assert ev["field"]["full_product_acceptance_matrix"] is False
assert ev["field"]["product_promoted"] is False

assert promo["candidate_version"]==20334
assert promo["same_signed_apk_field_verified"] is True
assert promo["field_install_verified"] is True
assert promo["field_installed_version"]==20334
assert promo["field_installed_sha256"]==ev["signed_artifact"]["sha256"]
assert promo["signed_candidate_d1_verified"] is True
assert promo["signed_candidate_version"]==20334
assert promo["source_head"]==ev["source"]["head"]
assert promo["source_ci_run_id"]==ev["source"]["ci_run_id"]
assert promo["source_ci_artifact_id"]==ev["source"]["ci_artifact_id"]
assert promo["promoted"] is False

print("FIELD_EVIDENCE_20334=PASS")
