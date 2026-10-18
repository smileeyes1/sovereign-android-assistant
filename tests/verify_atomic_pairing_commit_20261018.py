#!/usr/bin/env python3
"""Static gate for all-or-nothing owner-approved secure pairing writes.

Tests include source-negative mutations and a simple reference-state simulation.
They do not emulate SharedPreferences failures on an actual Android device.
"""
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "app/src/main/java/ps/hakim/phoneagent"
RELAY = (BASE / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
ACTIVITY = (BASE / "HakimPairingActivity.kt").read_text(encoding="utf-8")

FIELDS = (
    'putString(KEY_TOPIC, topic)',
    'putString(KEY_RESULT_TOPIC, resultTopic)',
    'putString(KEY_RELAY_KEY, relayKey)',
    'putString(KEY_BRIDGE_BASE, normalizedBridge)',
    'putString(KEY_APPROVAL_SESSION, UUID.randomUUID().toString())',
    'putString("pair_token", pairToken)',
    'putLong("secure_pairing_at", System.currentTimeMillis())',
    'putBoolean("pairing_disabled_by_user", false)',
    'putBoolean("secure_relay_configured", true)',
)


def section(src: str, start: str, end: str) -> str:
    assert src.count(start) == 1, "missing/ambiguous start: " + start
    text = src.split(start, 1)[1]
    assert end in text, "missing end: " + end
    return text.split(end, 1)[0]


def atomic_commit_guards(relay: str, activity: str) -> bool:
    try:
        commit = section(relay, "fun configure(", "private fun normalizeBridgeBase(")
        confirmation = section(activity, "private fun confirmPairing(", "\n}")
    except AssertionError:
        return False

    expected = (
        "pairToken: String" in commit,
        "pairToken.length !in 32..256" in commit,
        "pairingDestinationHost(topic, resultTopic, relayKey, bridgeBase) == null" in commit,
        commit.count(".commit()") == 1,
        ".apply()" not in commit,
        commit.count("getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()") == 1,
        all(commit.count(field) == 1 for field in FIELDS),
        all(commit.index(field) < commit.index(".commit()") for field in FIELDS),
        commit.index("if (pairToken.length !in 32..256)") < commit.index("getSharedPreferences("),
        commit.index("if (pairingDestinationHost(") < commit.index("getSharedPreferences("),
        commit.index(".commit()") < commit.index("if (committed) revokePendingApprovals(context)"),
        "return committed" in commit,
        'HakimUnifiedRelay.configure(' in confirmation,
        "this, topic, resultTopic, relayKey, bridgeBase, token" in confirmation,
        'getSharedPreferences("hakim"' not in confirmation,
        ".commit()" not in confirmation,
        ".apply()" not in confirmation,
        confirmation.index("if (!configured)") < confirmation.index("HakimUnifiedRelay.start(applicationContext)"),
        confirmation.index("if (!configured)") < confirmation.index("sendPairingAckAsync("),
    )
    return all(expected)


def test_atomic_owner_approved_pairing() -> None:
    assert atomic_commit_guards(RELAY, ACTIVITY)


def test_negative_mutations_rejected() -> None:
    mutations = {
        "missing_token": (
            RELAY.replace('putString("pair_token", pairToken)', '// token lost', 1), ACTIVITY
        ),
        "premature_enable": (
            RELAY.replace('putBoolean("pairing_disabled_by_user", false)', '// missing enabled state', 1), ACTIVITY
        ),
        "missing_session_epoch": (
            RELAY.replace('putString(KEY_APPROVAL_SESSION, UUID.randomUUID().toString())', '// no epoch', 1), ACTIVITY
        ),
        "skip_commit": (
            RELAY.replace('            .commit()\n        if (committed)', '            .apply()\n        if (committed)', 1), ACTIVITY
        ),
        "revoke_before_commit": (
            RELAY.replace('if (committed) revokePendingApprovals(context)', 'revokePendingApprovals(context)', 1), ACTIVITY
        ),
        "second_independent_write": (
            RELAY, ACTIVITY.replace(
                '        HakimUnifiedRelay.start(applicationContext)',
                '        getSharedPreferences("hakim", MODE_PRIVATE).edit().commit()\n'
                '        HakimUnifiedRelay.start(applicationContext)', 1,
            ),
        ),
    }
    for name, (relay, activity) in mutations.items():
        assert relay != RELAY or activity != ACTIVITY, "mutation not applied: " + name
        assert not atomic_commit_guards(relay, activity), "unsafe mutation passed: " + name
        print("NEGATIVE_REJECTED", name)


def test_reference_transaction_semantics() -> None:
    original = {"pair_token": "old", "pairing_disabled_by_user": True}
    candidate = {"pair_token": "new", "pairing_disabled_by_user": False,
                 "relay_approval_session": "epoch-new"}
    for commit_succeeded in (False, True):
        persisted = original.copy()
        if commit_succeeded:
            persisted.update(candidate)
        if commit_succeeded:
            assert persisted["pair_token"] == "new"
            assert persisted["relay_approval_session"] == "epoch-new"
        else:
            assert persisted == original
    print("SYNTHETIC_ATOMICITY_REFERENCE=PASS")


if __name__ == "__main__":
    tests = sorted((name, fn) for name, fn in globals().items()
                   if name.startswith("test_") and callable(fn))
    for name, fn in tests:
        fn()
        print("PASS", name)
    print(f"HAKIM_ATOMIC_PAIRING_GATE=PASS {len(tests)}")
