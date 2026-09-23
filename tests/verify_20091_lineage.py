from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
BASELINE = (ROOT / "governance/LAST_VERIFIED_BASELINE.md").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("LINEAGE_20091_GATE=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None, "version_code_missing")
req(int(m.group(1)) == 20091, "candidate_version_not_20091")
req("versionName '2.0.91-multimodel-router-v1'" in BUILD, "candidate_version_name_mismatch")
req("c84359e422dad0aa205f59a153a1f29d7f4c4e81" in BASELINE, "cloud_baseline_missing")
req("20087" in BASELINE and "Historical last promoted field baseline" in BASELINE, "field_baseline_missing")
req("20090" in BASELINE and "NOT PROMOTED" in BASELINE, "nonpromoted_20090_missing")
req("d3ae6946b2d63e402a1b597dbc93ab5d58efa12f" in BASELINE, "modern_source_base_missing")
req("NOT PROVEN" in BASELINE, "source_apk_uncertainty_not_recorded")
req("CI success" in BASELINE, "ci_not_separated_from_field_acceptance")

print("LINEAGE_20091_GATE=PASS candidate=20091 historical_field=20087 cloud=c84359e")
