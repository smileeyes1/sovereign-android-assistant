from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]

p = ROOT / "governance/HAKIM_QURANIC_SOVEREIGN_INVARIANTS.json"
data = json.loads(p.read_text(encoding="utf-8"))

assert data["quran"]["normative_root"] is True
assert data["quran"]["all_surahs_in_scope"] == 114
assert data["quran"]["verified_ayah_count"] == 6236
assert data["quran"]["whole_corpus_request_requires_actual_full_scan"] is True
assert data["quran"]["no_cherry_picking"] is True
assert data["quran"]["no_forced_surah_relevance"] is True
assert data["quran"]["exact_text_requires_verified_source"] is True
assert data["quran"]["text_tafsir_fiqh_worldly_layers_separated"] is True

assert data["barakah"]["religious_spiritual_meaning"] is True
assert data["barakah"]["technical_metric"] is False
assert data["barakah"]["hidden_computational_power"] is False
assert data["barakah"]["guaranteed_worldly_outcome"] is False
assert data["barakah"]["does_not_replace_lawful_means_and_verification"] is True

assert data["worldly_means"]["science"] is True
assert data["worldly_means"]["evidence"] is True
assert data["worldly_means"]["experience"] is True
assert data["worldly_means"]["expertise"] is True

assert data["sovereignty"]["core_runtime_vendor_independent"] is True
assert data["sovereignty"]["external_advanced_reasoning_optional"] is True
assert data["sovereignty"]["local_safe_degraded_mode_required"] is True
assert data["sovereignty"]["offline_advanced_model_equivalence_claimed"] is False
assert data["sovereignty"]["authority_cannot_expand_from_general_language"] is True
assert data["sovereignty"]["irreversible_actions_require_specific_gate"] is True
assert data["sovereignty"]["field_verified_requires_real_device_evidence"] is True

print("HAKIM_QURANIC_SOVEREIGN_INVARIANTS=PASS")
