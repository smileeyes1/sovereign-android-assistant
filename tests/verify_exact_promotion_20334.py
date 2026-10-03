from pathlib import Path
s=Path("scripts/hakim-promote-exact-20334.sh").read_text()
for x in ['EXPECTED_SHA256="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"','--install','--sign-only','hakim-sign-exact-20334-ci1521.sh','hakim-install-exact-20334-via-adb.sh','stage=d1_signed_ready_not_installed','stage=field_installed_and_same_artifact_verified','$HOME/.hakim/releases']: assert x in s,x
assert 'MODE="sign"' in s
for x in ['adb uninstall','pm clear','--downgrade','curl ','wget ','git clone']: assert x not in s,x
print("EXACT_PROMOTION_20334=PASS")
