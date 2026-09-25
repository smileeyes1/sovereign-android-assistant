from pathlib import Path

APP=Path("app/src/main/java/ps/hakim/phoneagent")
K=(APP/"HakimCapabilityKernel.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
UPDATER=(APP/"AutoUpdater.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")
MAIN=(APP/"MainActivity.kt").read_text(encoding="utf-8")

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
    'fun grantPersistent',
    'fun revokePersistent',
    'fun hasPersistentGrant',
    'explicit_user_grant_once',
    'explicit_user_grant_persistent',
]
missing=[x for x in required if x not in K]
assert not missing, f"missing capability-kernel invariants: {missing}"

# Known-failure guards: unknown or high-impact capabilities must never get implicit allow.
forbidden=['?: "allow"', 'else "allow" // unknown', 'unknown_capability_allowed']
assert not any(x in K for x in forbidden)

# Wiring: policy must sit on real side-effect paths, not remain descriptive.
for x in [
    'HakimCapabilityKernel.authorize(',
    '"send_external"',
    'userInitiated: Boolean = false',
    'HakimCapabilityKernel.grantOnce(this, "send_external", target)',
]:
    assert x in CENTER, "external_send_not_wired:"+x

for x in [
    'HakimCapabilityKernel.authorize(',
    '"install_candidate"',
    'install_user_authorization_required',
    'fun autoUpdateAuthorized',
]:
    assert x in UPDATER, "install_not_wired:"+x

for x in [
    'HakimCapabilityKernel.grantPersistent(',
    'HakimCapabilityKernel.revokePersistent(',
    'تفعيل التحديثات التلقائية',
]:
    assert x in HOME, "update_consent_ui_missing:"+x

for x in [
    'explicitUserGrant: Boolean = false',
    'HakimCapabilityKernel.grantOnce(this, "grant_permission", target)',
    'HakimCapabilityKernel.authorize(',
]:
    assert x in MAIN, "permission_gate_not_wired:"+x

print("CAPABILITY_KERNEL_POLICY=PASS executable_authority=true scoped_grants=true external_send=true install=true permissions=true")
