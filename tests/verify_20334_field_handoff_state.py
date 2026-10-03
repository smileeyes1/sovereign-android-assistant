from pathlib import Path
import json
d=json.loads(Path("governance/HAKIM_20334_FIELD_HANDOFF.json").read_text())
assert d["candidate_version_code"]==20334
assert d["source_head"]=="a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"
assert d["source_ci"]["run_number"]==1521 and d["source_ci"]["conclusion"]=="success"
assert d["frozen_unsigned_artifact"]["artifact_id"]==11270247244
assert d["frozen_unsigned_artifact"]["sha256"]=="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"
assert d["field_signing"]["private_material_in_ci"] is False
assert d["field_signing"]["signed_sha256"] is None and d["field_signing"]["verified"] is False
assert d["field_install"]["verified_baseline_floor"]==20317 and d["field_install"]["field_installed"] is False
assert d["state"]=="SOURCE_CI_VERIFIED_AWAITING_D1_SIGNING"
print("FIELD_HANDOFF_STATE_20334=PASS")
