#!/usr/bin/env bash
# Read-only verification. Nonzero exit when the local desktop or Tailscale route is unverified.
set -Eeuo pipefail
problems=0
ok(){ printf '✓ %s\n' "$*"; }
bad(){ printf '✗ %s\n' "$*" >&2; problems=$((problems+1)); }
if ! command -v sudo >/dev/null 2>&1; then bad 'sudo غير متوفر'; fi
if command -v docker >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  if running="$(sudo docker ps --filter 'name=^/hakim-cloud-desktop$' --format '{{.Names}}' 2>/dev/null)" && [[ "$running" == 'hakim-cloud-desktop' ]]; then
    ok 'حاوية سطح المكتب تعمل'
    digest="$(sudo docker image inspect lscr.io/linuxserver/webtop:ubuntu-xfce --format '{{index .RepoDigests 0}}' 2>/dev/null || true)"
    if [[ -n "$digest" ]]; then printf 'صورة الحاوية: %s\n' "$digest"; else printf 'بصمة صورة الحاوية: غير متاحة\n'; fi
  else bad 'الحاوية لا تعمل أو لم يمكن فحصها'; fi
else bad 'Docker أو sudo غير متوفر'; fi
if command -v curl >/dev/null 2>&1; then
  httpcode="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 7 http://127.0.0.1:3000 2>/dev/null || true)"
  case "$httpcode" in
    401) ok "الخدمة المحلية تتطلب المصادقة (HTTP 401)";;
    200|301|302|303|307|308) bad "الخدمة المحلية تردّ لكن شرط المصادقة غير مثبت (HTTP $httpcode)";;
    *) bad "لا توجد استجابة HTTP محلية مع مصادقة مثبتة (الرمز: ${httpcode:-لا شيء})";;
  esac
else bad 'curl غير متوفر'; fi
if command -v tailscale >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  if sudo tailscale ip -4 2>/dev/null | grep -qE '^100\.'; then ok 'Tailscale مسجّل على الخادم'; else bad 'Tailscale غير متصل'; fi
  if serve="$(sudo tailscale serve status 2>/dev/null)" && [[ "$serve" == *'http://127.0.0.1:3000'* ]]; then
    ok 'Serve يحتوي مسار سطح المكتب الخاص'
  else bad 'Serve لا يعرض المسار المتوقع'; fi
else bad 'Tailscale أو sudo غير متوفر'; fi
if [[ "$problems" -eq 0 ]]; then
  echo 'مؤشرات الخادم المحلية جيدة. الوصول من الهاتف والأمان الطرفي ما زالا يحتاجان اختبارًا حقيقيًا.'
  exit 0
fi
printf 'فشل %s شرط/شروط؛ لم يتغيّر شيء على الخادم.\n' "$problems" >&2
exit 1
