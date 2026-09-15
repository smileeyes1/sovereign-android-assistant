from pathlib import Path

script = Path('scripts/hakim-zero-burden-rescue.sh').read_text(encoding='utf-8')

def require(cond, message):
    if not cond:
        raise SystemExit(message)

require("RDC_PATTERN='@wonderwhy-er/desktop-commander.*remote'" in script, 'P0: نمط عملية RDC مفقود')
require('stop_stale_remote_maintenance' in script, 'P0: إنعاش عملية RDC القديمة مفقود')
require('fresh_remote_log' in script, 'P0: فصل سجل المحاولة الحالية مفقود')
require('RDC_PREV_LOG' in script, 'P0: حفظ تشخيص المحاولة السابقة مفقود')
require(script.index('fresh_remote_log') < script.index('nohup npx --yes'), 'P0: يجب فصل السجل قبل بدء RDC')
require("REMOTE_MAINTENANCE=ONLINE" in script, 'P0: حالة الاتصال المثبت مفقودة')
require("REMOTE_MAINTENANCE=PAIRING_REQUIRED" in script, 'P0: حالة التحقق المحلي مفقودة')
require("REMOTE_MAINTENANCE=WAITING_UNPROVEN" in script, 'P0: حالة عدم الإثبات مفقودة')
require('Device ready|Device marked as online|Channel subscribed' in script, 'P0: دليل الاتصال الفعلي مفقود')
require('Code expires in [0-9]+ minutes' in script, 'P0: كشف رمز التحقق الحديث مفقود')
require("pgrep -f '$RDC_PATTERN' >/dev/null 2>&1; then\n    return 0" not in script, 'P0: عاد نجاح PID الوهمي')
for forbidden in ('rm -rf "$HOME/.desktop-commander', 'pm clear', 'uninstall', 'settings put', 'appops set'):
    require(forbidden not in script, f'P0: إجراء هدّام/موسع غير مسموح في rescue: {forbidden}')

print('ZERO_BURDEN_RESCUE_GATE=PASS')
