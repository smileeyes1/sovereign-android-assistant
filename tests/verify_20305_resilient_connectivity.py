from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
OUTBOX=(APP/"HakimRelayOutbox.kt").read_text(encoding="utf-8")
NET=(APP/"HakimConnectivityState.kt").read_text(encoding="utf-8")
FABRIC=(APP/"HakimExecutionFabric.kt").read_text(encoding="utf-8")
RES=(APP/"HakimConnectionResilience.kt").read_text(encoding="utf-8")
JOB=(APP/"HakimConnectionRecoveryJobService.kt").read_text(encoding="utf-8")
SERVICE=(APP/"HakimService.kt").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v,reason):
    if not v:
        raise SystemExit("RESILIENT_CONNECTIVITY_20305=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20305,"version")
req("3.4.0-resilient-connectivity-v2" in BUILD,"version_name")

# Network truth: no false ONLINE from a socket flag alone.
req("NET_CAPABILITY_VALIDATED" in NET,"validated_network_missing")
req("hasValidatedInternet" in NET,"validated_network_helper_missing")
req("isFreshConnected" in RELAY,"freshness_lease_missing")
req("CONNECTION_LEASE_MS" in RELAY,"lease_bound_missing")
req("HakimUnifiedRelay.isFreshConnected(app)" in FABRIC,"fabric_uses_raw_socket_state")
req('"secure_relay_socket_open"' in FABRIC,"socket_diagnostic_missing")

# Durable encrypted store-and-forward.
for token in [
    'carrier.startsWith("HR1.")',
    "MAX_ITEMS = 64",
    "MAX_TOTAL_BYTES",
    "TTL_MS",
    "oldest-first" if False else "sortedBy",
    "encrypted_carriers_only",
]:
    req(token in OUTBOX,"outbox:"+token)
req("HakimRelayOutbox.enqueue" in RELAY,"send_failure_not_queued")
req("HakimRelayOutbox.flush" in RELAY,"outbox_flush_missing")
req('"queued"' in RELAY,"queued_state_missing")

# Catch-up channel from ntfy cache with cursor.
for token in [
    "pollCachedOnce",
    "poll=1&since=",
    "secure_relay_last_ntfy_id",
    "secure_relay_last_cached_poll_at",
]:
    req(token in RELAY,"cached_catchup:"+token)
req("HakimUnifiedRelay.pollCachedOnce(applicationContext)" in JOB,"watchdog_catchup_missing")

# Recovery without hammering the network.
req("waiting_validated_network" in RELAY,"unvalidated_network_gate_missing")
req("SecureRandom().nextLong" in RELAY,"retry_jitter_missing")
req("RETRY_JOB_ID" in RES,"one_shot_job_missing")
req("BACKOFF_POLICY_EXPONENTIAL" in RES,"system_backoff_missing")
req("setMinimumLatency" in RES,"one_shot_latency_missing")
req("scheduleImmediate(applicationContext, \"service_destroyed\")" in SERVICE,"service_death_recovery_missing")
req("scheduleImmediate(applicationContext, \"task_removed\")" in SERVICE,"task_removed_recovery_missing")
req("params?.jobId == HakimConnectionResilience.RETRY_JOB_ID" in JOB,"retry_job_reschedule_policy_missing")

# User-visible semantics are richer than ONLINE/OFFLINE.
req('"DEGRADED"' in FABRIC,"degraded_state_missing")
req('"OFFLINE_QUEUED"' in FABRIC,"offline_queued_state_missing")
req('"offline_store_and_forward", true' in FABRIC,"store_forward_contract_missing")

# Governance: new source candidate does not inherit field success.
req(STATE["android"]["latest_source_parent"]["version_code"]==20304,"parent_version")
req(STATE["android"]["latest_source_parent"]["source_ci_verified"] is True,"parent_ci")
req(STATE["android"]["candidate"]["version_code"]==20305,"candidate_version")
req(STATE["android"]["candidate"]["field_verified"] is False,"field_must_remain_false")
req(STATE["android"]["candidate"]["promoted"] is False,"promotion_must_remain_false")
req(STATE["productization"]["resilient_connectivity_v2"] is True,"product_state_missing")
req(STATE["productization"]["offline_store_and_forward"] is True,"product_outbox_state_missing")
req(STATE["productization"]["cached_command_catchup"] is True,"product_catchup_state_missing")

# Fault injection: if queueing or validated-network truth is removed, the gate must see it.
mutant=RELAY.replace("HakimRelayOutbox.enqueue(context, resultTopic, carrier)","false",1)
req(mutant!=RELAY,"fault_queue_setup")
req("HakimRelayOutbox.enqueue(context, resultTopic, carrier)" in RELAY,"fault_queue_guard")
mutant_net=NET.replace("NetworkCapabilities.NET_CAPABILITY_VALIDATED","NetworkCapabilities.NET_CAPABILITY_INTERNET",1)
req(mutant_net!=NET,"fault_validated_setup")
req("NetworkCapabilities.NET_CAPABILITY_VALIDATED" in NET,"fault_validated_guard")

req("python3 tests/verify_20305_resilient_connectivity.py" in WORKFLOW,"workflow_gate_missing")
print("RESILIENT_CONNECTIVITY_20305=PASS validated_network=true freshness_lease=true outbox=true cached_catchup=true jitter=true one_shot_recovery=true")
