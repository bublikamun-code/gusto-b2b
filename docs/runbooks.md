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

## Продакшен

Заполняется в S41: деплой, бэкапы (pg_dump + WAL, шифрование age/gpg),
restore-процедура, мониторинг /healthz, откат на предыдущий образ.
