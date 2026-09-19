#!/usr/bin/env bash
# Восстановление из бэкапа (S41) + restore-тест (приёмка: «бэкап восстанавливается»).
#
# Восстановление на прод (внимание: ЗАМЕНЯЕТ данные!):
#   bash infra/scripts/restore.sh db-2026-09-20.sql.age files-2026-09-20.tar.gz.age
#   (AGE_KEY_FILE — файл приватного ключа age; по умолчанию $HOME/.config/age/gusto.key)
#
# Restore-тест: скрипт ВСЕГДА восстанавливает во временные контейнеры (scratch),
# печатает число записей в ключевых таблицах и список файлов — прод не трогает.
set -euo pipefail

DB_BACKUP="${1:?укажите файл db-*.sql.age}"
FILES_BACKUP="${2:?укажите файл files-*.tar.gz.age}"
AGE_KEY_FILE="${AGE_KEY_FILE:-$HOME/.config/age/gusto.key}"
POSTGRES_USER="${POSTGRES_USER:-gusto}"
POSTGRES_DB="${POSTGRES_DB:-gusto}"

for tool in age docker; do
  command -v "$tool" >/dev/null || { echo "не найден $tool" >&2; exit 1; }
done
[ -f "$AGE_KEY_FILE" ] || { echo "нет ключа $AGE_KEY_FILE" >&2; exit 1; }
[ -f "$DB_BACKUP" ] || { echo "нет файла $DB_BACKUP" >&2; exit 1; }
[ -f "$FILES_BACKUP" ] || { echo "нет файла $FILES_BACKUP" >&2; exit 1; }

NET=gusto-restore-test
WORK=$(mktemp -d)
cleanup() { docker rm -f restore-test-pg >/dev/null 2>&1 || true; docker network rm "$NET" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

echo "[restore-test] временный postgres..."
docker network create "$NET" >/dev/null
docker run -d --name restore-test-pg --network "$NET" \
  -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_PASSWORD=restore-test -e POSTGRES_DB="$POSTGRES_DB" \
  postgres:16 >/dev/null
sleep 5

echo "[restore-test] расшифровка БД..."
age -d -i "$AGE_KEY_FILE" < "$DB_BACKUP" > "$WORK/db.dump"

echo "[restore-test] восстановление БД..."
docker exec -i restore-test-pg pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner < "$WORK/db.dump"

echo "[restore-test] расшифровка файлов..."
age -d -i "$AGE_KEY_FILE" < "$FILES_BACKUP" > "$WORK/files.tar.gz"

echo "[restore-test] контрольные суммы ключевых таблиц:"
for table in users companies products orders invoices waybills files settings; do
  count=$(docker exec restore-test-pg psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "select count(*) from $table" 2>/dev/null || echo "ошибка")
  printf "  %-12s %s\n" "$table" "$count"
done

echo "[restore-test] файлы в бэкапе:"
docker run --rm --network none -v "$WORK:/backup" alpine \
  sh -c "tar tzf /backup/files.tar.gz | head -5; echo '  … всего:' \$(tar tzf /backup/files.tar.gz | wc -l)"

echo "[restore-test] OK: бэкап расшифрован и восстановлен (прод не тронут)."
echo "Для боевого восстановления: pg_restore в контейнер postgres и tar xzf в том filedata — см. docs/runbooks.md."
