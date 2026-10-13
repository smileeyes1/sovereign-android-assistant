#!/usr/bin/env bash
# حكيم: تثبيت بيئة سطح مكتب خاصة على خادم Ubuntu مخصص جديد.
set -Eeuo pipefail
umask 077
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEST="${HOME}/hakim-cloud-desktop"

fail(){ printf 'خطأ: %s\n' "$*" >&2; exit 1; }
[[ "${EUID}" -ne 0 ]] || fail 'شغّل البرنامج بحساب المستخدم العادي، دون sudo؛ سيطلب sudo عند الحاجة.'
[[ -r /etc/os-release ]] || fail 'لم أتعرف على نظام التشغيل.'
# shellcheck disable=SC1091
. /etc/os-release
[[ "${ID:-}" == "ubuntu" ]] || fail 'هذا الإصدار مُعدّ لأوبونتو فقط.'
case "$(uname -m)" in aarch64|x86_64) ;; *) fail 'نوع المعالج غير معتمد في هذه الحزمة.';; esac
command -v sudo >/dev/null || fail 'sudo مطلوب.'
command -v curl >/dev/null || fail 'curl مطلوب.'
command -v openssl >/dev/null || fail 'openssl مطلوب.'
[[ -f "${HERE}/compose.yaml" ]] || fail 'ملف compose.yaml مفقود.'

if [[ -e "${DEST}/compose.yaml" ]] && ! cmp -s "${DEST}/compose.yaml" "${HERE}/compose.yaml"; then
  fail "يوجد إعداد سابق مختلف في ${DEST}؛ لا أستبدله حفاظًا على البيئة السابقة."
fi
mkdir -p -- "${DEST}/desktop-data"
chmod 700 "${DEST}" "${DEST}/desktop-data"
if [[ ! -e "${DEST}/compose.yaml" ]]; then
  cp -- "${HERE}/compose.yaml" "${DEST}/compose.yaml"
fi
if [[ ! -e "${DEST}/.env" ]]; then
  password="$(openssl rand -base64 32 | tr -d '\n')"
  { printf 'PUID=%s\n' "$(id -u)"; printf 'PGID=%s\n' "$(id -g)";
    printf 'DESKTOP_USER=hakim\nDESKTOP_PASSWORD=%s\n' "${password}"; } >"${DEST}/.env"
  chmod 600 "${DEST}/.env"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo 'تثبيت Docker من مستودعات Ubuntu الرسمية...'
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io
fi
if ! sudo docker compose version >/dev/null 2>&1; then
  echo 'تثبيت Docker Compose...'
  sudo apt-get update
  if ! sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker-compose-v2; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker-compose
  fi
fi
if sudo docker compose version >/dev/null 2>&1; then
  COMPOSE=(sudo docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(sudo docker-compose)
else
  fail 'Docker Compose غير متوفر.'
fi
sudo systemctl enable --now docker
cd -- "${DEST}"
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d

# لا نفتح أي منفذ للعالم؛ الاتصال عبر Tailscale Serve داخل الشبكة الخاصة فقط.
if ! command -v tailscale >/dev/null 2>&1; then
  echo 'تثبيت Tailscale من المصدر الرسمي...'
  installer="$(mktemp)"
  trap 'rm -f -- "${installer:-}"' EXIT
  curl --proto '=https' --tlsv1.2 -fsSL https://tailscale.com/install.sh -o "${installer}"
  sudo sh "${installer}"
  rm -f -- "${installer}"
  trap - EXIT
fi
sudo systemctl enable --now tailscaled
if ! tailscale ip -4 2>/dev/null | grep -qE '^100\.'; then
  echo 'المتبقي: افتح رابط المصادقة الذي ستطبعه Tailscale، ثم شغّل connect.sh.'
  if ! sudo tailscale up --hostname=hakim-cloud; then
    echo 'سطح المكتب مثبت محليًا، لكن ربط Tailscale لم ينجح؛ أوقفنا إكمال الربط.' >&2
    exit 4
  fi
fi
if ! "${HERE}/connect.sh"; then
  echo 'سطح المكتب مثبت محليًا، لكن الاتصال الخاص غير مُثبت. راجع سبب الفشل أعلاه.' >&2
  exit 5
fi
printf '\nتم تشغيل المكونات محليًا وربط Tailscale وفق مؤشرات الأوامر (لم يُختبر الاتصال من هاتف فعلي).\n'
printf 'ملفاتك في: %s\n' "${DEST}"
printf 'لإظهار حالة الاتصال: %s/status.sh\n' "${HERE}"
printf 'اسم مستخدم سطح المكتب: hakim\n'
printf 'قراءة كلمة المرور داخل الخادم فقط: grep ^DESKTOP_PASSWORD= "%s/.env"\n' "${DEST}"
