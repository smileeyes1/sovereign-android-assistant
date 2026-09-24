from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
state = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
promotion = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("ACTIVE_STATE_GATE=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", gradle)
req(bool(m), "version_missing")
candidate = int(m.group(1))

req(state.get("single_source_of_truth") is True, "single_source")
req(state["cloud"]["last_verified_baseline_commit"] == "c84359e422dad0aa205f59a153a1f29d7f4c4e81", "cloud_baseline")
req(state["android"]["latest_source_parent"]["commit"] == "744e803ef10cd4ac56f9af75a5c4c005f69ce19c", "source_parent")
req(state["android"]["latest_source_parent"]["ci_run_number"] == 912, "source_parent_ci")
req(state["android"]["candidate"]["version_code"] == candidate == 20110, "candidate_version")
req(state["android"]["candidate"]["field_verified"] is False, "field_must_remain_false")
req(state["android"]["candidate"]["promoted"] is False, "must_not_promote")
req(state["local_execution"]["phone_channel"] == "OFFLINE_EXTERNAL_CONNECTOR", "phone_claim")
req(state["local_execution"]["execution_fabric_source_integrated"] is True, "execution_fabric_source")
req(state["local_execution"]["execution_fabric_field_online"] is False, "must_not_claim_field_online")
req(state["local_execution"]["production_bridge_contract"] == "relay_result_topic+HR1", "bridge_contract")
req(state["local_execution"]["termux_channel"] == "NOT_PROVEN_CURRENTLY", "termux_claim")
req(state["material_factory"]["physical_output_verified"] is False, "physical_claim")
req(state["human_biology"]["whole_body_systems_covered"] is True, "biology_coverage")
req(state["human_biology"]["brain_covered"] is True, "brain_coverage")
req(state["human_biology"]["heart_covered"] is True, "heart_coverage")
req(state["human_biology"]["autonomous_clinical_action"] is False, "clinical_autonomy")
req(state["human_biology"]["direct_intervention_requires_qualified_gate"] is True, "clinical_gate")
req(promotion.get("candidate_version") == candidate, "promotion_state_version")
req(promotion.get("promoted") is False, "promotion_state_must_be_false")
req(state["android"]["candidate"]["source_ci_state"] in {"PENDING_CURRENT_HEAD_CI", "SOURCE_CI_VERIFIED"}, "candidate_ci_state")

print("ACTIVE_STATE_GATE=PASS")
