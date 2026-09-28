from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

CATALOG = (APP / "HakimGovernanceCatalog.kt").read_text(encoding="utf-8")
CONSTITUTION = (APP / "HakimConstitution.kt").read_text(encoding="utf-8")
DIRECTOR = (APP / "HakimIntentDirector.kt").read_text(encoding="utf-8")
INTENT = (APP / "HakimIntentEngine.kt").read_text(encoding="utf-8")
ARTIFACT = (APP / "HakimArtifactPipeline.kt").read_text(encoding="utf-8")
SELF = (APP / "HakimSelfCheck.kt").read_text(encoding="utf-8")
RELAY = (APP / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
HEALTH = (APP / "HakimHealthBeacon.kt").read_text(encoding="utf-8")
DOC = (ROOT / "governance/HAKIM_INTERNAL_SOVEREIGN_CONSTITUTION_FULL.md").read_text(encoding="utf-8")
CUSTOM = (ROOT / "governance/HAKIM_CUSTOM_INSTRUCTIONS_8000.txt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def req(ok: bool, reason: str) -> None:
    if not ok:
        raise SystemExit("INTERNAL_GOVERNANCE=FAIL reason=" + reason)


# Full internal constitution must be independent of the external custom-instruction limit.
for token in [
    'const val VERSION = "FULL-SOVEREIGN-GOVERNANCE-2026-09-28-v1"',
    '"not_bound_to_custom_8000_limit", true',
    '"adaptive_rule_selection", true',
    '"full_constitution_retained", true',
    "fun fullText(): String",
    "fun fullSha256(): String",
    "fun adaptiveContext(",
    "fun canonicalJson(): JSONObject",
]:
    req(token in CATALOG, "catalog:" + token)

rule_ids = re.findall(r'Rule\("([A-Z]+-\d+)"', CATALOG)
req(len(rule_ids) >= 50, f"rule_count:{len(rule_ids)}")
req(len(rule_ids) == len(set(rule_ids)), "duplicate_rule_ids")

for domain in [
    "CORE", "AUTHORITY", "EVIDENCE", "EXECUTION", "CLOSURE", "FAILURE",
    "SECURITY", "RESOURCE", "ARTIFACT", "EDUCATION", "SOFTWARE", "WEB",
    "DEVICE", "RESEARCH", "COMMUNICATION", "MEMORY", "HEALTH", "MEDIA",
]:
    req(f"Domain.{domain}" in CATALOG, "domain:" + domain)

# Core governance remains universal; task modules are selected adaptively.
for token in [
    'Rule("CORE-001"',
    'Rule("AUTH-001"',
    'Rule("EVID-001"',
    'Rule("EXEC-001"',
    'Rule("CLOSE-001"',
    'Rule("FAIL-001"',
    'Rule("SEC-001"',
]:
    req(token in CATALOG, "core_rule:" + token)

req(".filter { !it.always && it.domain in domains }" in CATALOG, "adaptive_modules_not_scoped")
req('val selected = linkedSetOf(Domain.RESOURCE)' in CATALOG, "resource_base_module_missing")
req("vagueContinuation" in CATALOG, "vague_continuation_context_missing")
req('takeLast(4_000)' in CATALOG, "bounded_recent_context_missing")
req('"last_goal_sha256"' in CATALOG, "goal_hash_missing")
req('"last_selection_used_recent_context"' in CATALOG, "selection_context_observability_missing")

# No raw goal/conversation text may be persisted by the governance selector.
for forbidden in [
    '.putString("last_goal", goal',
    '.putString("last_goal", normalizedGoal',
    '.putString("recent_context"',
    '.putString("conversation"',
]:
    req(forbidden not in CATALOG, "selector_raw_persistence:" + forbidden)

# Runtime constitution must embed the full catalog and expose adaptive context.
for token in [
    "const val INTERNAL_GOVERNANCE = HakimGovernanceCatalog.VERSION",
    '"internal_governance_unbounded_by_custom_limit", true',
    '"adaptive_governance_context", true',
    "HakimGovernanceCatalog.fullSha256()",
    "fun taskContext(",
    "HakimGovernanceCatalog.adaptiveContext(",
    '.put("internal_governance", HakimGovernanceCatalog.canonicalJson())',
    '.put("governance_catalog", HakimGovernanceCatalog.status(context))',
]:
    req(token in CONSTITUTION, "constitution:" + token)

# The internal constitution must not be clipped to the custom-instruction limit.
req("}.take(7200)" not in CONSTITUTION, "legacy_7200_prompt_cap")
for source, name in [(DIRECTOR, "director"), (INTENT, "intent"), (ARTIFACT, "artifact")]:
    req(".take(12_000)" not in source, name + "_legacy_12000_cap")
req("}.take(6_000)" not in ARTIFACT, "artifact_legacy_6000_repair_cap")

# Every main model-facing route must request task-specific governance.
req(
    "HakimConstitution.taskContext(context, goal, acceptance, attachmentCount)" in DIRECTOR,
    "director_task_context_missing",
)
req(
    "HakimConstitution.taskContext(context, plan.goal, plan.completion.joinToString" in INTENT,
    "intent_task_context_missing",
)
req(
    ARTIFACT.count("HakimConstitution.taskContext(") >= 2,
    "artifact_generation_repair_task_context_missing",
)

# Runtime observability and self-check.
for token in [
    "الدستور الداخلي الكامل مثبت",
    "الدستور الداخلي غير مقيد بحد ٨٠٠٠",
    "الاستدعاء التكيفي للقواعد مفعل",
    "الدستور الكامل محفوظ",
    "كتالوج الحاكمية غير مبتور",
]:
    req(token in SELF, "self_check:" + token)

req(
    '.put("governance_catalog", HakimGovernanceCatalog.status(context))' in RELAY,
    "relay_catalog_observability_missing",
)
req(
    '.put("governance_catalog", HakimGovernanceCatalog.status(context))' in HEALTH,
    "health_catalog_observability_missing",
)

# Human-readable full constitution and compact external profile must remain distinct.
for token in [
    "الدستور الداخلي الكامل",
    "لا يخضع لحد ٨٠٠٠",
    "الاستدعاء التكيفي",
    "ARTIFACT",
    "EDUCATION",
    "SOFTWARE",
    "WEB",
    "DEVICE",
    "RESEARCH",
    "MEMORY",
    "HEALTH",
    "MEDIA",
    "HakimAcceptanceGate",
]:
    req(token in DOC, "doc:" + token)

req(len(CUSTOM.rstrip("\n")) <= 8000, "custom_profile_limit_regression")
req("HAKIM_CUSTOM_INSTRUCTIONS_8000.txt" in DOC, "compact_profile_relationship_missing")

# Fault-injection sentinels.
mutant = DIRECTOR.replace(
    "HakimConstitution.taskContext(context, goal, acceptance, attachmentCount)",
    '""',
    1,
)
req(
    "HakimConstitution.taskContext(context, goal, acceptance, attachmentCount)" not in mutant,
    "fault_director_setup",
)
mutant2 = CATALOG.replace('"not_bound_to_custom_8000_limit", true', '"not_bound_to_custom_8000_limit", false', 1)
req('"not_bound_to_custom_8000_limit", true' not in mutant2, "fault_limit_setup")

req(
    "python3 tests/verify_internal_governance_catalog.py" in WORKFLOW,
    "workflow_gate_missing",
)

print(
    f"INTERNAL_GOVERNANCE=PASS rules={len(rule_ids)} "
    "full=true adaptive=true custom_limit_separate=true raw_goal_persistence=false"
)
