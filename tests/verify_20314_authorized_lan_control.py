from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'app/src/main/java/ps/hakim/phoneagent'
BUILD=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
SURVEY=(APP/'HakimLanSurvey.kt').read_text(encoding='utf-8')
CONTROL=(APP/'HakimAuthorizedLanControl.kt').read_text(encoding='utf-8')
ADB=(APP/'HakimAdbConnectionManager.kt').read_text(encoding='utf-8')
RELAY=(APP/'HakimUnifiedRelay.kt').read_text(encoding='utf-8')
WORKFLOW=(ROOT/'.github/workflows/android.yml').read_text(encoding='utf-8')

def req(v, reason):
    if not v:
        raise SystemExit('AUTHORIZED_LAN_CONTROL_20314=FAIL reason='+reason)

m=re.search(r'versionCode\s+(\d+)',BUILD)
req(m is not None and int(m.group(1))>=20314,'version_floor')
req('authorized-lan-control' in BUILD,'version_name')
req('5555' in SURVEY and 'adb_candidates' in SURVEY,'adb_discovery')
req('SHA-256' in CONTROL and 'ip_local_only' in CONTROL,'pseudonymous_device_ids')
req('raw_ip_exposed' in CONTROL and '.put("raw_ip_exposed", false)' in CONTROL,'raw_ip_guard')
req('device_not_authorized' in CONTROL,'authorization_gate')
req('device_mapping_stale' in CONTROL,'stale_mapping_guard')
req('network_devices' in RELAY,'network_read_op')
req('network_authorize' in RELAY and 'network_control' in RELAY,'network_write_ops')
req('showApproval(context, requestId, op)' in RELAY,'write_approval_gate')
req('fun probeRemoteIdentity(' in ADB,'adb_identity_probe')
req('HAKIM-ADB-TARGET-v1' in ADB and 'ro.build.fingerprint' in ADB,'adb_identity_fingerprint')
req('setThrowOnUnauthorised(true)' in ADB,'adb_auth_rejection_detection')
req('ADB_TARGET_IDENTITY_CHANGED' in ADB,'identity_change_guard')
req('fun executeRemoteAction(' in ADB,'restricted_adb_executor')
req('connect(host, port)' in ADB and 'openStream("shell:$command")' in ADB,'remote_adb_path')
req('probeRemoteIdentity(ip, 5555)' in CONTROL,'authorization_handshake_missing')
authorize_block=CONTROL.split('fun authorize(',1)[1].split('fun control(',1)[0]
req(authorize_block.index('probeRemoteIdentity(ip, 5555)') < authorize_block.index('putBoolean("$id.authorized.adb", true)'), 'authorization_before_handshake')
req('adb_identity_sha256' in authorize_block,'identity_not_bound_at_authorization')
req('target_approval_required' in authorize_block,'target_approval_state_missing')
control_block=CONTROL.split('fun control(',1)[1].split('fun status(',1)[0]
req('expectedIdentity' in control_block and 'ADB_TARGET_IDENTITY_CHANGED' in control_block,'identity_not_checked_before_effect')
req('authorization_revoked' in control_block,'identity_change_does_not_revoke')
req('fun remote(context: Context)' in ADB,'isolated_remote_manager')

for token in [
    '"home"','"back"','"up"','"down"','"left"','"right"','"enter"',
    '"play_pause"','"volume_up"','"volume_down"','"mute"',
    '"open_url"','"launch_package"'
]:
    req(token in CONTROL,'missing_action:'+token)

for forbidden in [
    '"reboot"','"power"','"factory_reset"',
    'payload.optString("command")','payload.getString("command")'
]:
    req(forbidden not in CONTROL,'unsafe_control_surface:'+forbidden)

req('python3 tests/verify_20314_authorized_lan_control.py' in WORKFLOW,'workflow_gate')
print('AUTHORIZED_LAN_CONTROL_20314=PASS pseudonymous=true approval_gated=true arbitrary_shell=false')
