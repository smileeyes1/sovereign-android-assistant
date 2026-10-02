from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
PAIR=(APP/"HakimPairingActivity.kt").read_text(encoding="utf-8")

def require(text,needle,reason):
    if needle not in text:
        raise SystemExit("SECONDARY_CONTROL_PLANE_GATE=FAIL reason="+reason)

def forbid(text,needle,reason):
    if needle in text:
        raise SystemExit("SECONDARY_CONTROL_PLANE_GATE=FAIL reason="+reason)

for needle,reason in [
    ('KEY_BRIDGE_FALLBACK_BASE = "relay_bridge_fallback_base"',"fallback_slot"),
    ("fallbackBridgeBase: String? = null","optional_fallback"),
    ("normalizedFallback == normalizedBridge","same_host_rejected"),
    ("old pairing link that knows only the primary must not erase a verified fallback","legacy_pairing_preserves_fallback"),
    ("private fun bridgeBases(context: Context): List<String>","bridge_list"),
    ("listOfNotNull(bridgeBase(context), fallbackBridgeBase(context)).distinct()","dedupe"),
    ("private fun orderedBridgeBases(context: Context, preferred: String? = null)","preferred_origin"),
    ("directPollAcrossBridges(context, topic, resultTopic, relayKey)","poll_failover"),
    ("val waitMs = if (bases.size > 1) 4_000 else 25_000","bounded_multi_host_poll"),
    ("handleCarrier(context, carrier, resultTopic, relayKey, base)","origin_propagation"),
    ("ackDirect(base, topic, relayKey, requestId)","same_host_ack"),
    ('putString("$id.bridge_base", normalizedBridge)',"pending_origin"),
    ("Triple<JSONObject, String, String?>","pending_origin_restore"),
    ("for (base in orderedBridgeBases(context, preferredBridgeBase))","result_failover"),
    ('"direct_secondary_sent"',"secondary_result_observability"),
    ('URL("https://ntfy.sh/" + resultTopic)',"legacy_independent_transport_preserved"),
]:
    require(RELAY,needle,reason)

for needle,reason in [
    ('getQueryParameter("bridge_fallback_base")',"pairing_param"),
    ("fallbackBridgeBase","pairing_fallback_wiring"),
]:
    require(PAIR,needle,reason)

# A secondary host may only be accepted through the same strict HTTPS origin normalizer.
require(RELAY,'uri.scheme.equals("https", true)',"https_only")
require(RELAY,'uri.userInfo != null',"userinfo_rejected")
require(RELAY,'uri.query != null || uri.fragment != null',"query_fragment_rejected")

# Never manufacture a fake backup provider in source. It must arrive from a verified pairing/config.
for needle,reason in [
    ("DEFAULT_FALLBACK_BRIDGE_BASE","hardcoded_unverified_fallback"),
    ("http://","insecure_bridge_literal"),
]:
    forbid(RELAY,needle,reason)

# Known-failure sentinel: removing the origin-aware result loop must fail this gate.
probe=RELAY.replace("for (base in orderedBridgeBases(context, preferredBridgeBase))","for (base in bridgeBases(context))")
if "for (base in orderedBridgeBases(context, preferredBridgeBase))" in probe:
    raise SystemExit("SECONDARY_CONTROL_PLANE_GATE=FAIL reason=sentinel_setup")
try:
    require(probe,"for (base in orderedBridgeBases(context, preferredBridgeBase))","known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("SECONDARY_CONTROL_PLANE_GATE=FAIL reason=sentinel_not_detected")

print("SECONDARY_CONTROL_PLANE_GATE=PASS android_failover_source=true host_deployment_claim=false")
