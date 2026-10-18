#!/usr/bin/env python3
"""Source contract: exported deep links must not reconfigure Hakim without on-device consent.
Static checks and negative mutations only; no instrumentation of Android dialogs.
"""
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "app/src/main"
ACTIVITY = (BASE / "java/ps/hakim/phoneagent/HakimPairingActivity.kt").read_text(encoding="utf-8")
RELAY = (BASE / "java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
MANIFEST = (BASE / "AndroidManifest.xml").read_text(encoding="utf-8")


def between(source, start, end):
    assert source.count(start) == 1, f"ambiguous start: {start}"
    content = source.split(start, 1)[1]
    assert end in content, f"missing end: {end}"
    return content.split(end, 1)[0]


def consent_guard(activity):
    entry = between(activity, "private fun handlePair(i: Intent?)", "private fun confirmPairing(")
    accept = between(activity, "private fun confirmPairing(", "\n}")
    prompt = between(entry, ".setMessage(", ".setPositiveButton(")
    return (
        "Intent.ACTION_VIEW" in entry
        and "uri == null" in entry
        and "!uri.isHierarchical" in entry
        and 'uri.scheme != "hakim"' in entry
        and 'uri.host != "pair"' in entry
        and "rejectInvalidPairing()" in entry
        and "pairingDestinationHost(" in entry
        and ".setPositiveButton(\"أوافق على الاقتران\") { _, _ ->" in entry
        and "confirmPairing(token, topic, resultTopic, relayKey, bridgeBase)" in entry
        and ".setNegativeButton(\"إلغاء\") { _, _ -> finish() }" in entry
        and ".setOnCancelListener { finish() }" in entry
        and "pairingDialog?.show()" in entry
        and "HakimUnifiedRelay.configure(" not in entry
        and "startForegroundService(" not in entry
        and "HakimUnifiedRelay.configure(this, topic, resultTopic, relayKey, bridgeBase)" in accept
        and 'putBoolean("pairing_disabled_by_user", false)' in accept
        and "HakimUnifiedRelay.start(applicationContext)" in accept
        and "relayKey" not in prompt
        and "token" not in prompt
        and "destinationHost" in prompt
    )


def test_no_side_effects_before_consent():
    assert consent_guard(ACTIVITY)


def test_exported_activity_and_lifecycle_covered():
    assert 'android:name=".HakimPairingActivity"' in MANIFEST
    assert 'android:exported="true"' in MANIFEST.split('android:name=".HakimPairingActivity"', 1)[1].split("</activity>", 1)[0]
    assert "override fun onNewIntent(intent: Intent?)" in ACTIVITY
    assert "pairingDialog?.dismiss()" in ACTIVITY
    assert "override fun onDestroy()" in ACTIVITY


def test_pure_preview_uses_same_credential_rules_as_commit():
    validator = between(RELAY, "fun pairingDestinationHost(", "fun configure(")
    configure = between(RELAY, "fun configure(", "private fun normalizeBridgeBase(")
    assert 'Regex("^[A-Za-z0-9_-]{20,120}$")' in validator
    assert "RELAY_KEY.matches(relayKey)" in validator
    assert "normalizeBridgeBase(bridgeBase)" in validator
    assert 'pairingDestinationHost(topic, resultTopic, relayKey, bridgeBase) == null' in configure
    assert "getSharedPreferences(" not in validator
    assert ".commit()" not in validator


def test_negative_mutations_rejected():
    mutations = {
        "missing_positive_consent": ACTIVITY.replace(
            '.setPositiveButton("أوافق على الاقتران")',
            '.setNeutralButton("اقتران")', 1),
        "no_cancel": ACTIVITY.replace(
            '.setNegativeButton("إلغاء") { _, _ -> finish() }',
            '.setNegativeButton("إلغاء") { _, _ -> Unit }', 1),
        "unprompted_configure": ACTIVITY.replace(
            "        val destinationHost = HakimUnifiedRelay.pairingDestinationHost(",
            "        HakimUnifiedRelay.configure(this, topic, resultTopic, relayKey, bridgeBase)\n"
            "        val destinationHost = HakimUnifiedRelay.pairingDestinationHost(", 1),
        "no_prompt_display": ACTIVITY.replace("        pairingDialog?.show()", "        // missing show", 1),
    }
    for name, mutant in mutations.items():
        assert mutant != ACTIVITY, f"mutation not applied: {name}"
        assert not consent_guard(mutant), f"unsafe source passed: {name}"
        print("NEGATIVE_REJECTED", name)


if __name__ == "__main__":
    tests = sorted((name, fn) for name, fn in globals().items()
                   if name.startswith("test_") and callable(fn))
    for name, fn in tests:
        fn()
        print("PASS", name)
    print(f"EXPORTED_PAIRING_CONSENT_GATE=PASS {len(tests)}")
