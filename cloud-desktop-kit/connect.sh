#!/usr/bin/env bash
set -Eeuo pipefail
command -v tailscale >/dev/null || { echo 'Tailscale غير مثبّت.'; exit 1; }
if ! tailscale ip -4 2>/dev/null | grep -qE '^100\.'; then
  echo 'يلزم تسجيل الدخول إلى Tailscale أولًا: sudo tailscale up'
  exit 2
fi
# تجنُّب تعديل خدمة Tailscale Serve سابقة في حال وجود إعداد مختلف.
if ! existing="$(tailscale serve status 2>&1)"; then
  echo 'تعذّر قراءة إعداد Tailscale Serve؛ لم أغير الإعدادات حمايةً للخدمات الموجودة.' >&2
  exit 3
fi
if [[ -n "$existing" ]] && [[ "$existing" != *'No serve config'* ]] && [[ "$existing" != *'No serve configuration'* ]] && [[ "$existing" != *'http://127.0.0.1:3000'* ]]; then
  echo 'يوجد إعداد Serve سابق مختلف؛ تُرك كما هو منعًا للتعارض.'
  exit 3
fi
if [[ "$existing" != *'http://127.0.0.1:3000'* ]]; then
  tailscale serve --bg http://127.0.0.1:3000
fi
echo 'عنوان الدخول الخاص داخل Tailscale:'
tailscale serve status
