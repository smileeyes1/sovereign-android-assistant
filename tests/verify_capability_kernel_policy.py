from pathlib import Path

p=Path("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityKernel.kt")
s=p.read_text(encoding="utf-8")
required=[
    'unknown_capability_denied',
    '"deny", "unknown_capability"',
    '"gate", "sensitive_material"',
    'high_impact_or_irreversible',
    'detail_sha256',
    'Capability("financial_action"',
    'Capability("grant_permission"',
    'Capability("build_candidate"',
]
missing=[x for x in required if x not in s]
assert not missing, f"missing capability-kernel invariants: {missing}"
# Known-failure guard: broad default allow must never appear.
forbidden=['?: "allow"', 'else "allow" // unknown', 'unknown_capability_allowed']
assert not any(x in s for x in forbidden)
print("CAPABILITY_KERNEL_POLICY=PASS")
