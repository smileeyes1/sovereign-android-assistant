#!/usr/bin/env bash
# Private Tailscale Serve setup. Never changes an unrecognized existing configuration.
set -Eeuo pipefail
command -v tailscale >/dev/null || { echo 'Tailscale غير مثبّت.' >&2; exit 1; }
command -v sudo >/dev/null || { echo 'sudo مطلوب لإدارة Tailscale على لينكس.' >&2; exit 1; }
# tailscaled generally requires root/operator permission; sudo does NOT grant a persistent operator role.
if ! sudo tailscale ip -4 2>/dev/null | grep -qE '^100\.'; then
  echo 'يلزم تسجيل الدخول إلى Tailscale أولًا: sudo tailscale up' >&2
  exit 2
fi
if ! existing="$(sudo tailscale serve status 2>&1)"; then
  echo 'تعذّر قراءة إعداد Serve؛ لن أغيّر إعدادات Tailscale القائمة.' >&2
  exit 3
fi
if [[ -z "$existing" ]]; then
  echo 'إعداد Serve فارغ وغير قابل لتصنيفه بأمان؛ لم أغيّر شيئًا.' >&2
  exit 3
fi
if [[ "$existing" != *'No serve config'* ]] && [[ "$existing" != *'No serve configuration'* ]]; then
  if [[ "$existing" == *'http://127.0.0.1:3000'* ]]; then
    # Do not silently claim success when there may be other routes too.
    echo 'إعداد Serve يتضمن المسار المطلوب. لم أغيره؛ افحص المسارات الإضافية عند وجودها.'
    sudo tailscale serve status
    exit 0
  fi
  echo 'يوجد إعداد Serve سابق مختلف؛ تركته كما هو منعًا للتعارض.' >&2
  exit 3
fi
# Only when we have positively identified a clean/no-config state.
sudo tailscale serve --bg http://127.0.0.1:3000
if ! confirmed="$(sudo tailscale serve status 2>&1)"; then
  echo 'أُرسِل أمر Serve لكن تعذّر تأكيد الإعداد؛ النجاح غير مثبت.' >&2
  exit 5
fi
if [[ "$confirmed" != *'http://127.0.0.1:3000'* ]]; then
  echo 'تعذّر تأكيد مسار Serve؛ النجاح غير مثبت.' >&2
  exit 5
fi
printf 'مسار Serve المحلي أصبح مسجّلًا؛ اختبار الوصول من الهاتف ما زال مطلوبًا.\n%s\n' "$confirmed"
