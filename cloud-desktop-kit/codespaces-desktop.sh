#!/usr/bin/env bash
# User-triggered experimental graphical desktop in GitHub Codespaces. Not a perpetual VM.
set -Eeuo pipefail
umask 077
fail(){ printf 'فشل آمن: %s\n' "$*" >&2; exit 1; }
[[ -n "${CODESPACE_NAME:-}" ]] || fail 'تعمل هذه الأداة في GitHub Codespaces فقط؛ لم يُنفذ شيء.'
[[ "${EUID}" -ne 0 ]] || fail 'لا تشغّل الأداة بصلاحيات root.'
command -v docker >/dev/null 2>&1 || fail 'Docker غير متوفر؛ لا تثبيت تلقائي.'
command -v openssl >/dev/null 2>&1 || fail 'openssl غير متوفر.'
command -v curl >/dev/null 2>&1 || fail 'curl غير متوفر.'
docker info >/dev/null 2>&1 || fail 'Docker daemon غير متاح. لم يُنشأ شيء.'
docker compose version >/dev/null 2>&1 || fail 'Docker Compose غير متاح. لم يُنشأ شيء.'
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
[[ -f "${HERE}/compose.yaml" ]] || fail 'ملف الإعداد الأساسي مفقود.'
# Refuse to configure any unexpected network binding or no-auth build.
grep -Fq '127.0.0.1:3000:3000' "${HERE}/compose.yaml" || fail 'منفذ سطح المكتب ليس محليًا.'
grep -Fq 'CUSTOM_USER:' "${HERE}/compose.yaml" || fail 'ضمان اسم المستخدم مفقود.'
grep -Fq 'PASSWORD:' "${HERE}/compose.yaml" || fail 'ضمان كلمة المرور مفقود.'
HOME_DIR="${HOME}/hakim-codespaces-desktop"
if [[ -e "${HOME_DIR}/compose.yaml" ]] && ! cmp -s "${HERE}/compose.yaml" "${HOME_DIR}/compose.yaml"; then
  fail 'هناك إعداد مختلف سابق. لن يُستبدل.'
fi
if [[ -e "${HOME_DIR}/.env" ]]; then
  # Existing secrets must be complete; never generate replacement credentials silently.
  grep -Eq '^PUID=[0-9]+$' "${HOME_DIR}/.env" || fail 'إعداد هوية غير صالح.'
  grep -Eq '^PGID=[0-9]+$' "${HOME_DIR}/.env" || fail 'إعداد المجموعة غير صالح.'
  grep -Eq '^DESKTOP_USER=hakim$' "${HOME_DIR}/.env" || fail 'إعداد المستخدم السابق غير متوقع.'
  grep -Eq '^DESKTOP_PASSWORD=[a-zA-Z0-9+/=]+$' "${HOME_DIR}/.env" || fail 'كلمة المرور السابقة غير صالحة.'
else
  # Large GUI images consume GitHub Codespaces storage and compute quota.
  free_kb="$(df -Pk "${HOME}" | awk 'NR==2 {print $4}')"
  [[ "${free_kb}" =~ ^[0-9]+$ ]] || fail 'تعذر فحص مساحة القرص.'
  (( free_kb >= 6*1024*1024 )) || fail 'يلزم توفير ٦ جيجابايت فارغة على الأقل قبل تجربة السطح الرسومي.'
  mkdir -p -- "${HOME_DIR}/desktop-data"
  chmod 700 -- "${HOME_DIR}" "${HOME_DIR}/desktop-data"
  password="$(openssl rand -base64 32 | tr -d '\n')"
  { printf 'PUID=%s\n' "$(id -u)"; printf 'PGID=%s\n' "$(id -g)";
    printf 'DESKTOP_USER=hakim\nDESKTOP_PASSWORD=%s\n' "${password}"; } > "${HOME_DIR}/.env"
  chmod 600 "${HOME_DIR}/.env"
fi
if [[ ! -e "${HOME_DIR}/compose.yaml" ]]; then cp -- "${HERE}/compose.yaml" "${HOME_DIR}/compose.yaml"; fi
chmod 600 "${HOME_DIR}/compose.yaml" "${HOME_DIR}/.env"
# No privileged container, docker socket mount, public port or Tailscale changes.
docker compose --project-directory "${HOME_DIR}" --env-file "${HOME_DIR}/.env" -f "${HOME_DIR}/compose.yaml" config --quiet
printf 'بدء سطح المكتب التجريبي. قد يستهلك تنزيل الصورة حصة التخزين والوقت.\n'
docker compose --project-directory "${HOME_DIR}" --env-file "${HOME_DIR}/.env" -f "${HOME_DIR}/compose.yaml" up -d
status=''
for i in 1 2 3 4 5 6 7 8 9 10; do
  status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 5 http://127.0.0.1:3000/ 2>/dev/null || true)"
  [[ "${status}" == 401 ]] && break
  sleep 2
done
if [[ "${status}" != 401 ]]; then
  printf 'لم تثبت المصادقة المحلية (HTTP %s). سيتم إيقاف الحاوية التجريبية لا حذف ملفاتك.\n' "${status:-غير متاح}" >&2
  docker compose --project-directory "${HOME_DIR}" --env-file "${HOME_DIR}/.env" -f "${HOME_DIR}/compose.yaml" stop || true
  exit 3
fi
cat <<'MSG'
نجح فحص المصادقة المحلي: 401 دون كلمة مرور. لم نختبر متصفح الهاتف بعد.
سطح المكتب يعمل داخل الجلسة الحالية فقط، وقد يتوقف مع توقف Codespaces.
افتح تبويب PORTS في VS Code ثم المنفذ 3000، واحتفظ برؤيته Private فقط.
لا تحوّل المنفذ إلى Public.
اسم المستخدم: hakim
لكلمة المرور: اقرأ السطر DESKTOP_PASSWORD من ~/hakim-codespaces-desktop/.env داخل الجلسة فقط.
الرابط المحلي الذي يفترض أن يكتشفه Codespaces: http://localhost:3000
MSG
