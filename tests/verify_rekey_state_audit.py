from pathlib import Path
import re

p = Path('scripts/hakim-rekey-state-audit.sh')
assert p.exists(), 'P0: مدقق حفظ الحالة مفقود'
s = p.read_text(encoding='utf-8')

required = [
    'EXPECTED_SIGNER=',
    'get-state',
    'shell dumpsys package',
    'hakim-field-preflight.sh',
    'private_state_not_readable_without_original_signer_or_app_export',
    'IN_APP_PLAINTEXT_RULE_EXPORT_OR_PROVEN_EQUIVALENT_REQUIRED_BEFORE_UNINSTALL',
]
for item in required:
    assert item in s, f'P0: حاجز الترحيل مفقود: {item}'

forbidden = [
    r'\badb\s+uninstall\b',
    r'\bpm\s+clear\b',
    r'\binstall\s+-r\b',
    r'\brm\s+-rf\b',
    r'run-as[^\n]*\bcat\b',
    r'run-as[^\n]*\bcp\b',
]
for pattern in forbidden:
    assert not re.search(pattern, s), f'P0: فعل هدام/كاشف داخل مدقق القراءة فقط: {pattern}'

assert 'signer=F4' in s
assert 'run_as=no' in s
assert 'run_as=yes' in s
print('HAKIM_REKEY_STATE_AUDIT_CONTRACT=PASS')
