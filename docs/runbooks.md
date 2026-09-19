# Runbooks

Операционные инструкции (заполняются по мере инфраструктуры).

## Локальная разработка

```bash
cp .env.example .env
make up          # всё в Docker: postgres, redis, backend, frontend
make infra       # гибрид: только postgres + redis
make logs        # логи backend
make clean       # остановить и удалить данные (Flyway накатит схему заново)
```

## E2E и нагрузочные тесты (S39)

```bash
make up           # стек: postgres, redis, backend, frontend
make e2e          # Playwright: полный цикл + витрина (десктоп и 375px)
make load-catalog # k6 100 RPS: приёмка p95 < 300 мс
make load-orders  # k6 20 RPS: p95 < 1000 мс
make load-down    # остановить прод-подобный бэкенд
```

Особенности:

- E2E требует `npx playwright install chromium` (один раз) и поднятого стека.
- Один логин отзывает прежние refresh-токены пользователя (одна активная
  сессия, S08). E2E-сценарии учитывают это: пароль клиента берётся из модалки
  «Сбросить пароль», API-логин админа — только после его UI-шагов.
- Нагрузка идёт против прод-подобного бэкенда (`k6/prod-backend.sh`, jar на
  :8081): dev-контейнер работает через `spring-boot:run` с урезанным JIT
  (TieredStopAtLevel=1) и его p95 недостоверен. Первый прогон — прогрев JVM
  (в скриптах k6 ступень warmup), затем замер.
- На ноутбучной Docker-VM (4 ГБ, общий CPU) замеряйте при остановленном
  dev-бэкенде: `docker compose stop backend` и верните его после
  (`docker compose up -d backend`). p95 выше нормы — сначала проверьте
  `docker stats` и своп VM.

## Продакшен (S41: инфраструктура готова, деплой — по доступе к VPS)

Состав: `docker-compose.prod.yml` (postgres, redis, backend из прод-jar,
frontend → nginx со статикой и прокси `/api`), `backend/Dockerfile.prod`,
`frontend/Dockerfile.prod`, `infra/nginx/gustomeat.conf` (TLS, HSTS/CSP,
gzip), скрипты `infra/scripts/*`, секреты — `.env.prod` (шаблон:
`.env.prod.example`).

### Первый деплой на VPS (Ubuntu LTS)

```bash
# 1. На сервере: репозиторий + секреты
git clone git@github.com:bublikamun-code/gusto-b2b.git /srv/gusto && cd /srv/gusto
cp .env.prod.example .env.prod   # заполнить секретами (chmod 600)

# 2. Сертификат (один раз, standalone занимает порт 80)
DOMAIN=gustomeat.by EMAIL=<админ-почта> bash infra/scripts/init-letsencrypt.sh

# 3. Стек
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
# Flyway накатит схему при старте backend; seed-админ — из V2 (пароль из .env.prod)

# 4. Проверки
curl -s https://gustomeat.by/healthz        # {"status":"UP"}
bash infra/scripts/healthcheck.sh           # для крона */5
```

### Бэкапы и restore-тест

```bash
# крон (root): ежедневно в 03:30
30 3 * * * cd /srv/gusto && infra/scripts/backup.sh >> /var/log/gusto-backup.log 2>&1

# restore-тест (безопасен: восстанавливает во временные контейнеры)
AGE_KEY_FILE=~/.config/age/gusto.key bash infra/scripts/restore.sh \
  $BACKUP_DIR/db-<дата>.sql.age $BACKUP_DIR/files-<дата>.tar.gz.age
```

Приватный ключ age (`age-keygen`) хранится офлайн (менеджер паролей),
на сервере — только публичный. `BACKUP_KEEP_DAYS` (по умолчанию 14) чистит
старые копии; строку `rclone copy` в backup.sh раскомментировать при
подключении внешнего хранилища (S3/SFTP).

### Боевое восстановление (авария)

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend
age -d -i age.key < db-<дата>.sql.age > db.dump
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres \
  pg_restore -U $POSTGRES_USER -d $POSTGRES_DB --clean --if-exists < db.dump
age -d -i age.key < files-<дата>.tar.gz.age > files.tar.gz
docker run --rm --volumes-from gusto-b2b-prod-backend-1 -v $(pwd):/backup alpine \
  sh -c "rm -rf /app/data/files && tar xzf /backup/files.tar.gz -C /app/data"
docker compose -f docker-compose.prod.yml --env-file .env.prod start backend
```

### Мониторинг

- `/healthz` — живость (см. `infra/scripts/healthcheck.sh`).
- Внешний пинг: healthchecks.io/UptimeRobot на `https://gustomeat.by/healthz`
  (алерт, когда сайт недоступен) — подключается при деплое.
- Логи: `docker compose -f docker-compose.prod.yml logs -f backend`.

