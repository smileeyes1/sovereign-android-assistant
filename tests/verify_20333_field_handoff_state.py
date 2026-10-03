#!/usr/bin/env python3
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
state=json.loads((root/"governance/HAKIM_20333_FIELD_HANDOFF.json").read_text(encoding="utf-8"))
sign=(root/"scripts/hakim-sign-exact-20333-ci1509.sh").read_text(encoding="utf-8")
install=(root/"scripts/hakim-install-exact-20333-via-adb.sh").read_text(encoding="utf-8")
promote=(root/"scripts/hakim-promote-exact-20333.sh").read_text(encoding="utf-8")

assert state["candidate_version_code"] == 20333
assert state["package"] == "ps.hakim.stable"
assert state["release_base_head"] == "32d3cf9cea526291021f7e68502ee19c649e5648"
assert state["autonomous_feature_head"] == "ed475233746388e1b8b94324874adc743957a1ef"
assert state["source_head"] == "9ef17d8c9cd176296795bdf7db35778e8eaceabf"
assert state["source_ci"]["run_number"] == 1509
assert state["source_ci"]["run_id"] == 37112123246
assert state["source_ci"]["conclusion"] == "success"
assert state["source_ci"]["autonomous_continuation_gate"] is True
assert state["source_ci"]["autonomous_headless_safe_runner"] is True
assert state["source_ci"]["version_monotonicity_20333"] is True
assert state["frozen_unsigned_artifact"]["artifact_id"] == 11270059099
sha=state["frozen_unsigned_artifact"]["sha256"]
assert sha == "944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"
assert state["frozen_unsigned_artifact"]["must_not_be_rebuilt_for_same_artifact_signing"] is True
assert state["field_signing"]["private_material_in_ci"] is False
assert state["field_signing"]["private_material_must_not_be_committed"] is True
assert state["field_signing"]["signed_sha256"] is None
assert state["field_signing"]["verified"] is False
assert state["field_install"]["verified_baseline_floor"] == 20317
assert state["field_install"]["same_signed_artifact_verified"] is False
assert state["promotion"]["default_mode"] == "sign_only"
assert state["promotion"]["install_requires_explicit_flag"] is True
assert state["state"] == "SOURCE_CI_VERIFIED_AWAITING_D1_SIGNING"

for token in [
    'EXPECTED_HEAD="9ef17d8c9cd176296795bdf7db35778e8eaceabf"',
    f'EXPECTED_UNSIGNED_SHA256="{sha}"',
    'EXPECTED_VERSION_CODE="20333"',
]:
    assert token in sign, token
for token in [
    'EXPECTED_SOURCE_HEAD="9ef17d8c9cd176296795bdf7db35778e8eaceabf"',
    f'EXPECTED_UNSIGNED_SHA256="{sha}"',
    'EXPECTED_VERSION_CODE="20333"',
    'EXPECTED_MIN_CURRENT_VERSION_CODE="20317"',
    "current_field_signer_mismatch",
    "installed_field_signer_mismatch",
    "package_uid_changed",
    "first_install_time_changed",
]:
    assert token in install, token
assert 'MODE="sign"' in promote
assert "--install" in promote

print("FIELD_HANDOFF_STATE_20333=PASS")
