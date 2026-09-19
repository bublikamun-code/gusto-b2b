#!/usr/bin/env bash
# Ежедневный бэкап (S41): pg_dump БД + тар файлового тома (фото товаров,
# PDF документов) → шифрование age → каталог бэкапов. Ретенция BACKUP_KEEP_DAYS.
#
# Крон (root): 30 3 * * * /srv/gusto/infra/scripts/backup.sh >> /var/log/gusto-backup.log 2>&1
# Восстановление: infra/scripts/restore.sh <файл .age>
set -euo pipefail

: "${BACKUP_DIR:=/srv/backups}"
: "${BACKUP_AGE_PUBLIC_KEY:?'установите BACKUP_AGE_PUBLIC_KEY (age-keygen) в .env.prod'}"
: "${BACKUP_KEEP_DAYS:=14}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
STAMP=$(date +%Y-%m-%d_%H%M%S)
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

mkdir -p "$BACKUP_DIR"

echo "[backup] начало $STAMP"

# --- БД ---
$COMPOSE exec -T postgres pg_dump -U "${POSTGRES_USER:?}" "${POSTGRES_DB:?}" \
  --format=custom --file="$WORKDIR/db.dump"

# --- Файлы (том filedata-prod: фото товаров, PDF документов) ---
BACKEND_CONTAINER=$($COMPOSE ps -q backend | head -1)
if [ -z "$BACKEND_CONTAINER" ]; then
  echo "backend не запущен — файловый том недоступен" >&2
  exit 1
fi
docker run --rm --volumes-from "$BACKEND_CONTAINER" -v "$WORKDIR:/backup" alpine \
  tar czf /backup/files.tar.gz -C /app/data files

# --- Шифрование age ---
cat "$WORKDIR/db.dump" | age -r "$BACKUP_AGE_PUBLIC_KEY" > "$BACKUP_DIR/db-$STAMP.sql.age"
cat "$WORKDIR/files.tar.gz" | age -r "$BACKUP_AGE_PUBLIC_KEY" > "$BACKUP_DIR/files-$STAMP.tar.gz.age"

# --- Ретенция ---
find "$BACKUP_DIR" -name "db-*.age" -mtime "+$BACKUP_KEEP_DAYS" -delete
find "$BACKUP_DIR" -name "files-*.age" -mtime "+$BACKUP_KEEP_DAYS" -delete

echo "[backup] готово: $BACKUP_DIR/{db,files}-$STAMP.*.age"

# Сюда можно добавить отправку на внешнее хранилище (rclone/S3/SFTP):
# rclone copy "$BACKUP_DIR/db-$STAMP.sql.age" remote:gusto-backups/
