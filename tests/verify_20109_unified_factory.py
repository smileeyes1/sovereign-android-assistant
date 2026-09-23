from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
factory = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimMaterialFactory.kt").read_text(encoding="utf-8")
intent = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")
self_check = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("UNIFIED_FACTORY_GATE=FAIL reason=" + reason)

req("MATERIAL-FACTORY-2026-09-23-v2" in factory, "factory_version")
for token in [
    "FABRICATED_UNVERIFIED", "MATERIAL_VERIFIED", "sameArtifact", "acceptancePassed",
    "التصميم≠التصنيع", "لا يُعلن المنتج المادي", "أبسط مقياس تصنيع يحقق الغاية"
]:
    req(token in factory, "factory:" + token)

req("target == State.MATERIAL_VERIFIED" in factory, "verified_transition_gate")
req("proof.fabricated && proof.measured && proof.acceptancePassed && proof.sameArtifact" in factory,
    "field_evidence_quorum")
req("HakimMaterialFactory.matches(effectiveText)" in intent, "intent_route_missing")
req('"material_factory" -> "تصنيع/منتج مادي"' in intent, "intent_label_missing")
req("HakimMaterialFactory.governedContext(context, raw)" in intent, "governed_context_missing")
req("HakimMaterialFactory.status(context)" in self_check, "self_check_missing")
req('.put("material_factory", materialFactory)' in self_check, "self_check_report_missing")

builder = intent.find('listOf("اصنع تطبيق", "أنشئ تطبيق", "ابن تطبيق"')
material = intent.find("HakimMaterialFactory.matches(effectiveText)")
req(builder >= 0 and material >= 0 and builder < material, "builder_must_precede_material_route")
req("versionCode 20109" in gradle, "candidate_version")

mutant = factory.replace(" && proof.sameArtifact", "", 1)
req("proof.fabricated && proof.measured && proof.acceptancePassed && proof.sameArtifact" not in mutant,
    "known_failure_not_detected")

print("UNIFIED_FACTORY_GATE=PASS")
