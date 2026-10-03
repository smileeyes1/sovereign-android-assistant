from pathlib import Path
import json

d=json.loads(Path("governance/HAKIM_20334_FIELD_HANDOFF.json").read_text(encoding="utf-8"))
assert d["schema_version"] == 2
assert d["candidate_version_code"] == 20334
assert d["package"] == "ps.hakim.stable"
assert d["source_head"] == "a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"
assert d["source_ci"]["run_number"] == 1521
assert d["source_ci"]["run_id"] == 37113673116
assert d["source_ci"]["conclusion"] == "success"
for key in (
    "autonomous_continuation_gate","autonomous_runtime_continuation",
    "autonomous_headless_safe_runner","autonomous_event_recovery",
    "goal_supervisor_policy","goal_executor_policy",
    "field_acceptance_autostart","build_release","alignment_16kb"
):
    assert d["source_ci"][key] is True, key

u=d["frozen_unsigned_artifact"]
assert u["artifact_id"] == 11270247244
assert u["sha256"] == "8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"
assert u["must_not_be_rebuilt_for_same_artifact_signing"] is True

s=d["field_signing"]
assert s["private_material_in_ci"] is False
assert s["private_material_must_not_be_committed"] is True
assert s["certificate_sha256"].replace(":","").lower() == "d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3"
assert s["signature_scheme"] == "APK Signature Scheme v2"
assert s["signature_algorithm_id"] == "0x0104"
assert s["signed_sha256"] == "ddacc815bfd5033bd41e6e7db0c498594c3a4aa6f95430c96e80f176ec3e9f4b"
assert s["signed_size_bytes"] == 14234978
assert s["verified"] is True

c=d["signing_reproduction_control"]
assert c["control_version_code"] == 20306
assert c["control_unsigned_sha256"] == "8e1f91ba1df0f89aa70c677f08d1ec49d72d8a88bbd80932befb6e311e1c7395"
assert c["expected_signed_sha256"] == "0590df14e754005638702e5dc6001b6ef337fe17c6b536e58470a25d3f6d89a1"
assert c["reproduced_signed_sha256"] == c["expected_signed_sha256"]
assert c["byte_for_byte_match"] is True

v=d["independent_verification"]
for key in (
    "rsa_signature_verified","content_digest_verified","public_key_matches_certificate",
    "negative_signature_mutation_rejected","payload_prefix_matches_ci_unsigned",
    "central_directory_matches_ci_unsigned","eocd_matches_except_central_directory_offset",
    "zip_entry_names_match","zip_metadata_match","all_uncompressed_entry_contents_match",
    "android_manifest_byte_identical","native_libraries_16kb_aligned",
    "resources_arsc_stored_and_4byte_aligned"
):
    assert v[key] is True, key
assert v["native_libraries_count"] == 8
assert v["verification_record_sha256"] == "c6c49ef353b6b1212106d5814cf6e51a4a3f151f3e72e9e88bf54ec6948ce0b8"

lib=d["private_library_delivery"]
assert lib["apk_path"] == "/مصنع/Hakim-20334-D1.apk"
assert lib["verification_path"] == "/مصنع/Hakim-20334-D1.verification.json"
assert lib["saved"] is True

f=d["field_install"]
assert f["verified_baseline_floor"] == 20317
assert f["observed_unpromoted_runtime_version"] == 20333
assert f["field_installed"] is False
assert f["same_signed_artifact_verified"] is False
assert f["no_uninstall"] is True and f["no_clear_data"] is True and f["forward_only"] is True

assert d["state"] == "D1_SIGNED_AND_INDEPENDENTLY_VERIFIED_AWAITING_FIELD_INSTALL"
assert "claim_field_approval_without_same_artifact_evidence" in d["forbidden_shortcuts"]
print("FIELD_HANDOFF_STATE_20334=PASS")
