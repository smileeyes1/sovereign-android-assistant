from pathlib import Path
import copy
import json

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "governance/HAKIM_INDEPENDENCE_BASELINE.json"
ACTIVE = ROOT / "governance/HAKIM_ACTIVE_STATE.json"
CONTRACT = ROOT / "governance/HAKIM_SOVEREIGN_DEVELOPMENT_CONTRACT.json"
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

REQUIRED_DIMENSIONS = {
    "user_authority_and_permission_boundaries",
    "model_provider_portability",
    "local_general_intelligence",
    "execution_path_redundancy",
    "control_plane_host_portability",
    "portable_nonsecret_state",
    "backup_and_restore",
    "cross_device_migration",
    "secret_custody",
    "signing_continuity",
    "offline_resilience",
    "update_and_recovery",
    "continuity_across_sessions_and_agents",
}
VALID_STATES = {"VERIFIED", "SOURCE_VERIFIED", "PARTIAL", "NOT_IMPLEMENTED", "NOT_PROVEN", "BLOCKED_EXTERNAL"}

def fail(reason: str) -> None:
    raise SystemExit("INDEPENDENCE_GATE=FAIL reason=" + reason)

def can_claim_complete(doc: dict) -> bool:
    dims = doc.get("dimensions", {})
    critical = [v for v in dims.values() if v.get("critical") is True]
    return bool(critical) and all(v.get("state") == "VERIFIED" for v in critical)

doc = json.loads(BASELINE.read_text(encoding="utf-8"))
active = json.loads(ACTIVE.read_text(encoding="utf-8"))
contract = json.loads(CONTRACT.read_text(encoding="utf-8"))

if doc.get("schema_version") != 1:
    fail("schema_version")
if doc.get("objective") != "MAXIMUM_PRACTICAL_SOVEREIGNTY":
    fail("objective")
if not doc.get("single_point_of_failure_policy", {}).get("forbidden_for_critical_software_paths"):
    fail("single_point_policy")
if set(doc.get("dimensions", {})) != REQUIRED_DIMENSIONS:
    fail("dimension_set")

for name, dim in doc["dimensions"].items():
    if dim.get("state") not in VALID_STATES:
        fail("invalid_state:" + name)
    if dim.get("critical") is not True:
        fail("critical_dimension_not_marked:" + name)
    if dim.get("target_state") != "VERIFIED":
        fail("target_not_verified:" + name)

claimable = can_claim_complete(doc)
if doc.get("complete") is True and not claimable:
    fail("premature_complete_claim")
if doc.get("complete") is not False:
    fail("current_complete_must_be_false_until_gaps_close")
if claimable:
    fail("baseline_unexpectedly_all_verified")

ind = active.get("independence", {})
if ind.get("baseline_file") != "governance/HAKIM_INDEPENDENCE_BASELINE.json":
    fail("active_state_missing_baseline_reference")
if ind.get("complete") is not False:
    fail("active_state_premature_complete")

provider = contract.get("provider_independence", {})
if not provider.get("single_model_dependency_forbidden"):
    fail("single_model_dependency_not_forbidden")
if not provider.get("providers_are_replaceable"):
    fail("providers_not_replaceable")

router = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
for package_name in [
    "com.openai.chatgpt",
    "com.google.android.apps.bard",
    "com.anthropic.claude",
    "com.deepseek.chat",
]:
    if package_name not in router:
        fail("provider_route_missing:" + package_name)
for needle in ["DIRECT_MODEL", "FREE_ENGINE_SETUP", "LOCAL_RESPONSE"]:
    if needle not in router:
        fail("router_channel_missing:" + needle)

fabric = (APP / "HakimExecutionFabric.kt").read_text(encoding="utf-8")
for needle in ["secure_relay", "legacy_websocket", "local_adb", "single_path_failure_does_not_close_goal"]:
    if needle not in fabric:
        fail("execution_redundancy_missing:" + needle)

secret = (APP / "HakimSecretStore.kt").read_text(encoding="utf-8")
for needle in ["AndroidKeyStore", "AES/GCM/NoPadding"]:
    if needle not in secret:
        fail("secret_custody_missing:" + needle)

# Known-failure sentinel proves the completion check is active.
probe = copy.deepcopy(doc)
for dim in probe["dimensions"].values():
    dim["state"] = "VERIFIED"
if not can_claim_complete(probe):
    fail("known_success_sentinel_not_detected")
probe["dimensions"]["backup_and_restore"]["state"] = "PARTIAL"
if can_claim_complete(probe):
    fail("known_failure_sentinel_not_detected")

print("INDEPENDENCE_GATE=PASS complete=false critical_gaps_preserved=true")
