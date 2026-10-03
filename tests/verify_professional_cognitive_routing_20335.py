#!/usr/bin/env python3
from pathlib import Path
import json
import re

root=Path(__file__).resolve().parents[1]
policy=(root/"app/src/main/java/ps/hakim/phoneagent/HakimCognitivePolicy.kt").read_text(encoding="utf-8")
wisdom=(root/"app/src/main/java/ps/hakim/phoneagent/HakimWisdomMatrix.kt").read_text(encoding="utf-8")
telemetry=(root/"app/src/main/java/ps/hakim/phoneagent/HakimEngineTelemetry.kt").read_text(encoding="utf-8")
intent=(root/"app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")
relay=(root/"app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
selfcheck=(root/"app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
capability=(root/"app/src/main/java/ps/hakim/phoneagent/HakimCapabilityKernel.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
state=json.loads((root/"governance/HAKIM_20335_COGNITIVE_CANDIDATE.json").read_text(encoding="utf-8"))

required_policy=[
    "object HakimCognitivePolicy",
    "enum class Depth { QUICK, STANDARD, DEEP, CRITICAL }",
    "fun analyze(prompt: String, attachmentCount: Int = 0)",
    "fun directive(profile: TaskProfile)",
    "HakimFreePolicy.allows",
    "HakimResiliencePolicy.isAvailable",
    "HakimEngineRegistry.attachmentsSupported",
    "deterministic_given_same_state",
    "provider_quality_static_ranking",
    "evidence_driven_routing",
]
for token in required_policy:
    assert token in policy, token

# Router must no longer claim intrinsic vendor quality with hard-coded profiles.
for forbidden in [
    "quality = 94",
    "quality = 82",
    "zeroCostCertainty",
    "private val profiles = mapOf",
]:
    assert forbidden not in wisdom, forbidden
assert "HakimCognitivePolicy.rank" in wisdom

# Sparse telemetry must not become false certainty after one outcome.
for token in [
    "(successes + 7.0) / (attempts + 10.0)",
    "attempts.toDouble() / (attempts + 8.0)",
    "_consecutive_failures",
    "_last_outcome_at",
]:
    assert token in telemetry, token

assert "HakimCognitivePolicy.analyze(goal, attachmentCount)" in intent
assert "HakimCognitivePolicy.directive(cognitive)" in intent
assert '.put("cognitive_policy", HakimCognitivePolicy.status(context))' in relay
assert '.put("capability_kernel", HakimCapabilityKernel.status(context))' in relay
for token in [
    "data class Probe",
    "fun probe(context: Context, capabilityId: String, target: String = \"\")",
    "fun runtimeMatrix(context: Context)",
    "capability_is_not_availability",
    "availability_is_not_authorization",
    "self_installer_disabled",
]:
    assert token in capability, token
for token in [
    "سياسة الإدراك الاحترافية مفعّلة",
    "التوجيه المعرفي قائم على الدليل",
    "عمق التفكير متكيف مع المهمة",
    "لا ترتيب ثابت لجودة المزودين",
]:
    assert token in selfcheck, token

assert re.search(r"versionCode\s+20335\b", gradle)
assert "professional-cognitive-routing-r26" in gradle
assert state["candidate_version_code"] == 20335
assert state["base_signed_candidate"]["version_code"] == 20334
assert state["base_signed_candidate"]["d1_verified"] is True
assert state["base_signed_candidate"]["field_install_verified"] is False
assert state["change"]["provider_static_quality_ranking_removed"] is True
assert state["field_verified"] is False
assert state["promoted"] is False

print("PROFESSIONAL_COGNITIVE_ROUTING_20335=PASS")
