#!/data/data/com.termux/files/usr/bin/bash
set -u
export PATH="$HOME/bin:/data/data/com.termux/files/usr/bin:/system/bin"
PKG="ps.hakim.stable"
BASE="$HOME/.hakim/boot-handoff"
STATE="$BASE/state"
BOOT_FILE="$BASE/boot_id"
NOTICE_ID=20060
mkdir -p "$BASE"; chmod 700 "$BASE" 2>/dev/null || true
running(){ pidof "$PKG" >/dev/null 2>&1; }
installed(){ /system/bin/pm path "$PKG" >/dev/null 2>&1; }
log(){ printf '%s %s\n' "$(date -Iseconds)" "$*" >> "$BASE/handoff.log"; }
current_boot=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null || printf unknown)
printf '%s\n' "$current_boot" > "$BOOT_FILE"
if ! installed; then printf 'package_missing\n' > "$STATE"; log 'package_missing'; exit 20; fi
sleep "${HAKIM_BOOT_HANDOFF_GRACE:-45}"
if running; then
  printf 'auto_started\n' > "$STATE"
  termux-notification-remove "$NOTICE_ID" >/dev/null 2>&1 || true
  log 'auto_started_no_handoff_needed'
  exit 0
fi
if ! command -v termux-notification >/dev/null 2>&1 || ! command -v termux-open-url >/dev/null 2>&1; then
  printf 'blocked_termux_api_missing\n' > "$STATE"; log 'termux_api_missing'; exit 21
fi
termux-notification --id "$NOTICE_ID" \
  --title 'حكيم — استعادة ما بعد الإقلاع' \
  --content 'منع Android التشغيل الصامت. اضغط لفتح حكيم واستعادة الاستمرارية.' \
  --ongoing --alert-once --priority low \
  --action "termux-open-url 'hakim://open'" \
  --button1 'فتح حكيم' --button1-action "termux-open-url 'hakim://open'" >/dev/null 2>&1
rc=$?
if [ "$rc" -eq 0 ]; then printf 'waiting_user_wake\n' > "$STATE"; log 'user_handoff_notification_posted'; exit 0; fi
printf 'notification_failed\n' > "$STATE"; log "notification_failed rc=$rc"; exit 22
