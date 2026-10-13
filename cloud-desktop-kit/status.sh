#!/usr/bin/env bash
set -u
DEST="${HOME}/hakim-cloud-desktop"
echo '=== حالة سطح المكتب ==='
if command -v docker >/dev/null 2>&1; then
  sudo docker ps --filter name='^/hakim-cloud-desktop$' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  echo 'استجابة HTTP المحلية (200 أو 401 متوقعة بحسب المصادقة):'
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 7 http://127.0.0.1:3000 || true
else
  echo 'Docker غير متوفر.'
fi
echo '=== الاتصال الخاص ==='
if command -v tailscale >/dev/null 2>&1; then
  tailscale status || true
  tailscale serve status || true
else
  echo 'Tailscale غير متوفر.'
fi
[[ -d "${DEST}" ]] && echo 'مجلد البيانات محفوظ خارج الحاوية.'
