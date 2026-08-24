# GUSTO B2B

B2B-портал оптовых продаж мясного гастронома «Густо»: публичный каталог с розничными
ценами, личные кабинеты (юрлицо, менеджер, бухгалтер, руководитель), склад, документы
(счёт/ТН/ТТН), CRM и обмен с 1С через .xlsx.

- **План реализации:** `docs/plan/GUSTO_B2B_Final_Checklist.md` (v1.2) — в этом репозитории, единственный экземпляр; прогресс отмечается в нём же (план, Часть 6.4)
- **Настройка окружения:** `GUSTO_Git_Docker_Setup.md` (вне репозитория)
- **Решения по архитектуре:** чек-лист, Часть 1.6 «Зафиксированные решения»

## Стек

| Слой | Технология |
|---|---|
| Frontend | React 18 + TypeScript + Vite + SCSS Modules |
| Backend | Java 21 + Spring Boot 3 + Spring Security + JPA + Flyway |
| БД / кэш | PostgreSQL 16, Redis 7 |
| Инфра | Docker Compose, Nginx, GitHub Actions |

## Быстрый старт (нужен только Docker)

```bash
cp .env.example .env      # при необходимости поменять пароли
make up                   # собрать и поднять postgres + redis + backend + frontend
```

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/healthz

Гибридный режим (приложения на хосте, только БД в контейнерах): `make infra`.

## Команды Makefile

| Команда | Действие |
|---|---|
| `make up` | собрать и поднять всё в контейнерах |
| `make infra` | только postgres + redis |
| `make down` | остановить (данные остаются) |
| `make clean` | остановить и удалить данные (чистая БД) |
| `make logs` | логи backend |
| `make psql` | psql внутри контейнера postgres |

## Структура

```
backend/            # Spring Boot (Java 21)
frontend/           # React + Vite (TypeScript)
shared/openapi.yaml # контракт API
bruno/              # API-коллекции Bruno
infra/              # nginx (прод), прочая инфраструктура
docs/               # decisions.md, runbooks.md, brandbook/
```

## Git-процесс

Ветки `main` (стабильная) и `develop` (интеграция). Работа — от сессий чек-листа:
`feature/<dev>-s<номер>-<кратко>` → PR → ревью + зелёный CI → squash merge в `develop`.
Коммиты — Conventional Commits. Миграции БД пишет только Dev B.
