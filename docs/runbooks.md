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

## Продакшен

Заполняется в S41: деплой, бэкапы (pg_dump + WAL, шифрование age/gpg),
restore-процедура, мониторинг /healthz, откат на предыдущий образ.
