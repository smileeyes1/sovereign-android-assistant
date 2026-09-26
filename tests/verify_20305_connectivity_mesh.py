from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MESH=(APP/"HakimConnectivityMesh.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimExecutionFabric.kt").read_text(encoding="utf-8")
RESILIENCE=(APP/"HakimConnectionResilience.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
ADB=(APP/"HakimAdbConnectionManager.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))

def req(v,reason):
    if not v:
        raise SystemExit("CONNECTIVITY_20305=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))==20305,"version")
req("3.4.0-resilient-connectivity-fabric-v1" in BUILD,"version_name")

for token in [
    "CONNECTIVITY-MESH-2026-09-26-v1",
    "secure_relay",
    "local_adb",
    "legacy_websocket",
    "primary_remote",
    "local_maintenance",
    "last_resort_compatibility",
    "outbound_cloud_only",
    "opens_inbound_listener",
    "hot_redundancy",
    "last_good_path",
    "RECOVERY_DEBOUNCE_MS",
]:
    req(token in MESH,"mesh:"+token)

req('"secure_relay"' in MESH and "priority = 100" in MESH,"secure_relay_not_primary")
req('"local_adb"' in MESH and "priority = 80" in MESH,"local_adb_priority")
req('"legacy_websocket"' in MESH and "priority = 40" in MESH,"legacy_not_last_resort")
req('.put("opens_inbound_listener", false)' in MESH,"inbound_listener_policy_missing")
req("ServerSocket" not in MESH and ".bind(" not in MESH,"mesh_must_not_open_listener")
req("HakimConnectivityMesh.networkProfile" in FABRIC,"fabric_network_profile_missing")
req("HakimConnectivityMesh.status" in FABRIC,"fabric_mesh_status_missing")
req('meshProfile.wifi' in FABRIC,"adb_wifi_scope_missing")
req("HakimConnectivityMesh.mayRecover" in RESILIENCE,"recovery_debounce_missing")
req("registerDefaultNetworkCallback" in RESILIENCE,"network_callback_missing")
req("NETWORK_TYPE_ANY" in RESILIENCE,"periodic_network_job_missing")
req("AES/GCM/NoPadding" in RELAY and "HmacSHA256" in RELAY,"secure_relay_crypto_missing")
req("AndroidKeyStore" in ADB,"adb_identity_not_keystore_backed")

# The mesh must be diagnostic/routing policy, not a bypass around authorization.
req("mutable_actions_need_approval" in MESH,"approval_boundary_missing")
req("single_path_failure_does_not_close_goal" in MESH,"goal_continuity_missing")

req(STATE["android"]["latest_source_parent"]["version_code"]==20304,"parent_version")
req(STATE["android"]["latest_source_parent"]["ci_run_number"]==1111,"parent_ci")
req(STATE["android"]["candidate"]["version_code"]==20305,"candidate_version")
req(STATE["android"]["candidate"]["field_verified"] is False,"field_must_remain_false")
req(STATE["android"]["candidate"]["promoted"] is False,"must_not_promote")
req(STATE["productization"]["connectivity_mesh_v1"] is True,"product_mesh_state")
req(STATE["productization"]["connectivity_mesh_field_verified"] is False,"mesh_field_must_remain_false")
req(STATE["productization"]["opens_inbound_listener"] is False,"inbound_listener_state")
req(STATE["productization"]["local_adb_maintenance_only"] is True,"adb_scope_state")

req("python3 tests/verify_20305_connectivity_mesh.py" in WORKFLOW,"workflow_gate_missing")
print("CONNECTIVITY_20305=PASS multipath=true secure_primary=true local_maintenance=true legacy_last_resort=true inbound_listener=false")
