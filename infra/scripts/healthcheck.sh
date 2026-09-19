#!/usr/bin/env bash
# Проверка живости /healthz (S41): для cron/uptime-монитора.
# Опционально шлёт пинг в healthchecks.io (HEALTHCHECK_URL), чтобы получать
# алерт, когда сайт перестал отвечать.
#
# Крон: */5 * * * * /srv/gusto/infra/scripts/healthcheck.sh
set -euo pipefail

URL="${HEALTHCHECK_URL:-https://gustomeat.by/healthz}"
NOTIFY="${HEALTHCHECKS_IO_URL:-}"

if curl -sf --max-time 10 "$URL" | grep -q '"status":"UP"'; then
  echo "$(date '+%F %T') OK $URL"
  [ -n "$NOTIFY" ] && curl -sf --max-time 10 "$NOTIFY" >/dev/null
else
  echo "$(date '+%F %T') FAIL $URL" >&2
  exit 1
fi
