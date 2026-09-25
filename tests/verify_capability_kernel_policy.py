from pathlib import Path

APP=Path("app/src/main/java/ps/hakim/phoneagent")
K=(APP/"HakimCapabilityKernel.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
MAIN=(APP/"MainActivity.kt").read_text(encoding="utf-8")
UPDATER=(APP/"AutoUpdater.kt").read_text(encoding="utf-8")

required=[
    'unknown_capability_denied',
    '"deny", "unknown_capability"',
    'sensitive_material_requires_explicit_once',
    'high_impact_or_irreversible',
    'detail_sha256',
    'Capability("financial_action"',
    'Capability("grant_permission"',
    'Capability("install_candidate"',
    'Capability("send_external"',
    'fun grantOnce',
    'explicit_user_grant_once',
]
missing=[x for x in required if x not in K]
assert not missing, f"missing capability-kernel invariants: {missing}"

forbidden=[
    '?: "allow"',
    'else "allow" // unknown',
    'unknown_capability_allowed',
    'fun grantPersistent',
    'explicit_user_grant_persistent',
]
assert not any(x in K for x in forbidden), "persistent_or_implicit_high_impact_grant_regression"

for x in [
    'HakimCapabilityKernel.authorize(',
    '"send_external"',
    'userInitiated: Boolean = false',
    'HakimCapabilityKernel.grantOnce(this, "send_external", target)',
]:
    assert x in CENTER, "external_send_not_wired:"+x

for x in [
    'explicitUserGrant: Boolean = false',
    'HakimCapabilityKernel.grantOnce(this, "grant_permission", target)',
    'HakimCapabilityKernel.authorize(',
]:
    assert x in MAIN, "permission_gate_not_wired:"+x

# Updating Hakim must not grant itself an installer surface.
assert 'PackageInstaller' not in UPDATER
assert 'session.commit' not in UPDATER
assert '"installer_capability", false' in UPDATER
assert 'exportVerifiedUpdate' in UPDATER

print("CAPABILITY_KERNEL_POLICY=PASS executable_authority=true scoped_once=true persistent_high_impact=false external_send=true permissions=true self_installer=false")
