from pathlib import Path
s=Path("scripts/hakim-sign-exact-20334-ci1521.sh").read_text()
for x in ['EXPECTED_HEAD="a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"','EXPECTED_CI_RUN="1521"','EXPECTED_UNSIGNED_SHA256="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"','EXPECTED_VERSION_CODE="20334"','--v2-signing-enabled true','--v3-signing-enabled true','verify-field-signer.sh','verify_hakim_d1_v2_structure.py','HAKIM_D1_EXACT_SIGN=PASS']: assert x in s,x
for x in ['curl ','wget ','git clone','adb uninstall','pm clear','HAKIM_FIELD_KEYSTORE_B64=','base64 --decode']: assert x not in s,x
print("D1_EXACT_SIGN_HANDOFF_20334=PASS")
