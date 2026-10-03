from pathlib import Path
s=Path("scripts/hakim-install-exact-20334-via-adb.sh").read_text()
for x in ['EXPECTED_VERSION_CODE="20334"','EXPECTED_MIN_CURRENT_VERSION_CODE="20317"','EXPECTED_SOURCE_HEAD="a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"','EXPECTED_CI_RUN="1521"','EXPECTED_UNSIGNED_SHA256="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"','current_field_signer_mismatch','install -r --no-streaming','installed_same_artifact_sha_mismatch','package_uid_changed','first_install_time_changed','installed_field_signer_mismatch','same_artifact=true','uid_preserved=true','first_install_time_preserved=true','data_clear=false','uninstall=false']: assert x in s,x
for x in ['adb uninstall','pm clear','install -d','--downgrade','su ','root ']: assert x not in s,x
print("FIELD_INSTALL_HANDOFF_20334=PASS")
