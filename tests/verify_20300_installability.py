from pathlib import Path
import json, re

ROOT=Path(__file__).resolve().parents[1]
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
STATE=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
SIGN=(ROOT/"governance/HAKIM_FIELD_SIGNING_IDENTITY.json").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("INSTALLABILITY_20300=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None,"version_missing")
code=int(m.group(1))
req(code>=20300,"version_below_installable_floor")
req(code>20207,"must_exceed_all_known_20207_builds")
req("applicationId 'ps.hakim.stable'" in BUILD,"package_changed")
req("minSdk 26" in BUILD,"min_sdk_changed")
req("targetSdk 35" in BUILD,"target_sdk_changed")
req('android:allowBackup="false"' in MANIFEST,"backup_policy_changed")
req('android:usesCleartextTraffic="false"' in MANIFEST,"cleartext_policy_changed")
req(STATE["android"]["candidate"]["version_code"]==code,"state_candidate")
req(STATE["productization"]["candidate_version"]==code,"product_candidate")
req("D1" in SIGN or "d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3" in SIGN.lower(),"d1_anchor_missing")
relation=STATE["productization"]["install_relation"]
req(relation in {f"UPGRADE_FROM_ANY_KNOWN_202XX_BUILD_TO_{code}", f"UPGRADE_FROM_VERIFIED_20306_TO_{code}", f"UPGRADE_FROM_VERIFIED_20308_TO_{code}", f"UPGRADE_FROM_VERIFIED_20309_TO_{code}", f"UPGRADE_FROM_VERIFIED_20310_TO_{code}"},"install_relation")
print(f"INSTALLABILITY_20300=PASS package=ps.hakim.stable version={code} floor=20207 signer=D1")
