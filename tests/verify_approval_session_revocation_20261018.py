#!/usr/bin/env python3
"""Static pairing-session revocation contract and synthetic behavioral examples.
This is NOT an instrumented Android test or a proof of mid-action cancellation.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/ps/hakim/phoneagent"
RELAY = (BASE / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
MAIN = (BASE / "MainActivity.kt").read_text(encoding="utf-8")


def section(source: str, start: str, end: str) -> str:
    assert source.count(start) == 1, "missing or ambiguous " + start
    body = source.split(start, 1)[1]
    assert end in body, "missing end " + end
    return body.split(end, 1)[0]


def guard_present(relay: str) -> bool:
    approve = section(relay, "fun handleApproval(context:", "private fun decodePayload(")
    execute = section(relay, "private fun executeEnvelope(", "private fun status(")
    return (
        "val (envelope, resultTopic, expectedSession) = pending" in approve
        and 'pairPrefs.getString(KEY_APPROVAL_SESSION, null) != expectedSession' in approve
        and "executeEnvelope(context.applicationContext, envelope, expectedSession)" in approve
        and "(expectedSession != null && pairPrefs.getString(KEY_APPROVAL_SESSION, null) != expectedSession)" in execute
    )


def test_pending_session_identity() -> None:
    save = section(RELAY, "private fun savePending(", "private fun takePending(")
    take = section(RELAY, "private fun takePending(", "private fun showApproval(")
    assert 'putString("$id.session", expectedSession)' in save
    assert 'current.getString(KEY_APPROVAL_SESSION, null) != expectedSession' in save
    assert 'current.getString(KEY_RELAY_KEY, null) != expectedKey' in save
    assert 'getString("$requestId.session", null)' in take
    assert 'session.isNullOrBlank()' in take  # untagged pre-upgrade approvals fail closed
    assert 'Triple(JSONObject(raw), topic, session)' in take


def single_use_guard(relay: str) -> bool:
    try:
        take = section(relay, "private fun takePending(", "private fun showApproval(")
    except AssertionError:
        return False
    return (
        "@Synchronized\n    private fun takePending(" in relay
        and '.remove("$requestId.session").commit()' in take
        and "if (!removed) return null" in take
        and ".apply()" not in take
        and "return parsed" in take
    )


def test_pending_can_only_be_consumed_once() -> None:
    assert single_use_guard(RELAY)
    variants = {
        "unsynchronized_claim": RELAY.replace(
            "@Synchronized\n    private fun takePending(", "private fun takePending(", 1
        ),
        "async_claim": RELAY.replace(
            '.remove("$requestId.session").commit()',
            '.remove("$requestId.session").apply()', 1
        ),
    }
    for label, source in variants.items():
        assert source != RELAY
        assert not single_use_guard(source), label
        print("NEGATIVE_REJECTED", label)


def test_approval_and_executor_check_epoch() -> None:
    assert guard_present(RELAY)


def test_entry_binds_authentic_session() -> None:
    carrier = section(RELAY, "private fun handleCarrier(", "private fun decryptCarrier(")
    assert "currentApprovalSession(context) ?: return null" in carrier
    assert 'getString(KEY_RELAY_KEY, null) != relayKey' in carrier
    assert "savePending(context, envelope, resultTopic, approvalSession, relayKey)" in carrier
    assert carrier.index("savePending(context, envelope") < carrier.index("showApproval(context,")


def test_secure_and_legacy_repair_rotate() -> None:
    configure = section(RELAY, "fun configure(", "private fun normalizeBridgeBase(")
    legacy = section(MAIN, "private fun savePairing()", "private fun startHakimService()")
    assert "putString(KEY_APPROVAL_SESSION, UUID.randomUUID().toString())" in configure
    assert "if (committed) revokePendingApprovals(context)" in configure
    assert "putString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, UUID.randomUUID().toString())" in legacy
    assert "if (!edit.commit())" in legacy
    assert "HakimUnifiedRelay.revokePendingApprovals(this)" in legacy


def test_disconnect_rotates_and_clears_before_ack() -> None:
    disconnect = section(MAIN, 'text = "فصل الاقتران"', 'text = "حفظ"')
    assert "putString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, UUID.randomUUID().toString())" in disconnect
    assert 'putBoolean("pairing_disabled_by_user", true)' in disconnect
    assert ".commit()" in disconnect
    assert "if (!disconnected)" in disconnect
    assert "HakimUnifiedRelay.revokePendingApprovals(this@MainActivity)" in disconnect
    revocation = section(RELAY, "fun revokePendingApprovals(", "private fun savePending(")
    assert 'getSharedPreferences("hakim_remote_pending"' in revocation
    assert "pending.edit().clear().commit()" in revocation
    assert "manager?.cancel(it.hashCode())" in revocation


def test_safe_gate_rejects_negative_mutations() -> None:
    approve = section(RELAY, "fun handleApproval(context:", "private fun decodePayload(")
    needle = "pairPrefs.getString(KEY_APPROVAL_SESSION, null) != expectedSession"
    assert approve.count(needle) == 1
    mutant1 = RELAY.replace(needle, "false", 1)
    mutant2 = RELAY.replace(
        "executeEnvelope(context.applicationContext, envelope, expectedSession)",
        "executeEnvelope(context.applicationContext, envelope)",
        1,
    )
    for label, mutant in (("removed_approval_epoch", mutant1), ("removed_execution_epoch", mutant2)):
        assert mutant != RELAY
        assert not guard_present(mutant), label
        print("NEGATIVE_REJECTED", label)


def test_synthetic_session_scenarios() -> None:
    # Reference expectations only, not a simulation of Android scheduling.
    def accepts(current, pending):
        return pending is not None and current is not None and current == pending
    assert accepts("pair-A", "pair-A")
    assert not accepts("pair-B", "pair-A")  # disconnect then re-pair
    assert not accepts("pair-A", None)  # pending approval from an older build
    assert not accepts(None, "pair-A")  # disconnected
    print("SYNTHETIC_SESSION_CASES=PASS")


if __name__ == "__main__":
    tests = sorted((name, fn) for name, fn in globals().items()
                   if name.startswith("test_") and callable(fn))
    for name, fn in tests:
        fn()
        print("PASS", name)
    print(f"APPROVAL_SESSION_CONTRACT_PASS={len(tests)}")
