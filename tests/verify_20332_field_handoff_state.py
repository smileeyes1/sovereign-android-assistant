#!/usr/bin/env python3
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
state=json.loads((root/"governance/HAKIM_20332_FIELD_HANDOFF.json").read_text(encoding="utf-8"))
sign=(root/"scripts/hakim-sign-exact-20332-ci1485.sh").read_text(encoding="utf-8")
install=(root/"scripts/hakim-install-exact-20332-via-adb.sh").read_text(encoding="utf-8")

assert state["candidate_version_code"] == 20332
assert state["package"] == "ps.hakim.stable"
assert state["source_head"] == "ed475233746388e1b8b94324874adc743957a1ef"
assert state["source_ci"]["run_number"] == 1485
assert state["source_ci"]["conclusion"] == "success"
assert state["frozen_unsigned_artifact"]["artifact_id"] == 11270005895
sha=state["frozen_unsigned_artifact"]["sha256"]
assert sha == "182b06dea9ea0e94fa6240d2d2663aa5e5af265283510c69e2a8050e0feb7cf7"
assert state["frozen_unsigned_artifact"]["must_not_be_rebuilt_for_same_artifact_signing"] is True
assert state["field_signing"]["private_material_in_ci"] is False
assert state["field_signing"]["private_material_must_not_be_committed"] is True
assert state["field_signing"]["signed_sha256"] is None
assert state["field_signing"]["verified"] is False
assert state["field_install"]["verified_baseline_floor"] == 20317
assert state["field_install"]["same_signed_artifact_verified"] is False
assert state["field_install"]["field_installed"] is False
assert state["state"] == "SOURCE_CI_VERIFIED_AWAITING_D1_SIGNING"

for token in [
    'EXPECTED_HEAD="ed475233746388e1b8b94324874adc743957a1ef"',
    f'EXPECTED_UNSIGNED_SHA256="{sha}"',
    'EXPECTED_VERSION_CODE="20332"',
]:
    assert token in sign, token
for token in [
    'EXPECTED_SOURCE_HEAD="ed475233746388e1b8b94324874adc743957a1ef"',
    f'EXPECTED_UNSIGNED_SHA256="{sha}"',
    'EXPECTED_VERSION_CODE="20332"',
    'EXPECTED_MIN_CURRENT_VERSION_CODE="20317"',
]:
    assert token in install, token

print("FIELD_HANDOFF_STATE_20332=PASS")
