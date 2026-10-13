#!/usr/bin/env bash
# فحص للقراءة فقط؛ لا يثبّت أندرويد ولا يحمّل وحدات النواة.
set -u
printf '=== اختبار أهلية ReDroid (قراءة فقط) ===\n'
printf 'المعمارية: %s\n' "$(uname -m)"
pg="$(getconf PAGESIZE 2>/dev/null || true)"
printf 'حجم صفحة الذاكرة: %s بايت\n' "${pg:-مجهول}"
[[ "$pg" == 4096 ]] || echo 'تنبيه: يتطلب المسار القياسي صفحات ٤ كيلوبايت.'
if [[ -e /dev/binder ]] || [[ -e /dev/binderfs/binder ]]; then
  echo 'Binder device: متوفر'
else
  echo 'Binder device: غير موجود/غير مثبت'
fi
if grep -q binder /proc/filesystems 2>/dev/null; then
  echo 'Binder filesystem: مُعلن عنه'
else
  echo 'Binder filesystem: غير مُعلن عنه'
fi
if [[ -f "/boot/config-$(uname -r)" ]]; then
  grep -E '^CONFIG_(ANDROID_BINDER_IPC|ANDROID_BINDERFS|DMABUF_HEAPS)=' "/boot/config-$(uname -r)" || true
else
  echo 'تكوين النواة غير متاح؛ لا يمكن إثبات جميع المتطلبات.'
fi
printf 'Docker: '
if command -v docker >/dev/null 2>&1; then echo 'موجود'; else echo 'غير موجود'; fi
cat <<'END'
الحالة: تقرير شروط أولية فقط، وليست شهادة توافق أو تشغيل.
ممنوع تعريض منفذ ADB رقم 5555 للإنترنت العام.
لا تثبّت أو تشغّل ReDroid بصلاحيات عالية قبل تحقق مستقل من التوافق والنسخ الاحتياطية.
END
