#!/usr/bin/env bash
# Read-only Codespaces readiness check. Does not create, stop, or configure cloud resources.
set -Eeuo pipefail
printf '=== كمبيوتر التطوير السحابي: فحص للقراءة فقط ===\n'
if [[ -n "${CODESPACE_NAME:-}" ]]; then
  echo 'Codespaces: اكتُشف اسم جلسة؛ لا يعني أنها دائمة أو خالية من التكلفة.'
else
  echo 'تنبيه: ليست هناك علامة موثوقة على وجود جلسة Codespaces الآن.'
fi
printf 'المعمارية: '; uname -m
printf 'النظام: '; uname -s
printf 'الموارد:\n'
if command -v free >/dev/null; then free -h | head -n 3; else echo 'free غير متوفر'; fi
if command -v df >/dev/null; then df -h . | tail -n 1; fi
printf 'الأدوات:\n'
for c in git python3 node java docker; do
  if command -v "$c" >/dev/null 2>&1; then printf '  %s: موجود\n' "$c"; else printf '  %s: غير متوفر\n' "$c"; fi
done
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  printf 'الفرع: '; git branch --show-current || true
  printf 'حالة العمل:\n'; git status --short | head -n 20
else
  echo 'لم أعثر على مستودع Git في الدليل الحالي.'
fi
if [[ -f cloud-desktop-kit/bootstrap.sh ]]; then
  echo 'اختبار صياغة سكربتات التثبيت (دون تنفيذ):'
  bash -n cloud-desktop-kit/*.sh && echo 'صياغة Bash: صحيحة'
fi
cat <<'END'
هذا فحص موارد محلي فقط، لا يثبت استمرارية Codespaces ولا يضمن عدم نفاد الرصيد.
لا تشغّل bootstrap.sh هنا؛ فهو مخصص لخادم Ubuntu جديد ومستقل.
END
