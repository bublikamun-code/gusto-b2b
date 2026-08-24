# GUSTO B2B

B2B-портал оптовых продаж мясного гастронома «Густо»: публичный каталог с розничными
ценами, личные кабинеты (юрлицо, менеджер, бухгалтер, руководитель), склад, документы
(счёт/ТН/ТТН), CRM и обмен с 1С через .xlsx.

- **План реализации:** `docs/plan/GUSTO_B2B_Final_Checklist.md` (v1.3) — в этом репозитории, единственный экземпляр; прогресс отмечается в нём же (план, Часть 6.4)
- **Правила для AI-ассистентов:** `AGENTS.md` — входная точка каждой сессии
- **Настройка окружения:** `GUSTO_Git_Docker_Setup.md` (вне репозитория)
- **Решения по архитектуре:** чек-лист, Часть 1.6 «Зафиксированные решения»; хронология ADR — `docs/decisions.md`

## Текущий статус (обновлено 2026-08-24)

- ✅ S01 — репозиторий, ветки `main`/`develop`, CI (gitleaks + backend + frontend), бренд-бук в `docs/brandbook/`
- ✅ S02 — backend-скелет: Spring Boot 3.5 / Java 21, envelope ответов, `/healthz` и `/readyz`, Dockerfile
- ✅ S03 — frontend-скелет: Vite + React 18 + TypeScript strict, SCSS-токены бренд-бука, ESLint + Prettier, прокси `/api` → backend
- ✅ S04 — Docker Compose (postgres 16, redis 7, backend, frontend) + Makefile + `.env.example`
- ✅ S06 — UI-kit: 11 компонентов, страница `/ui-kit`
- ⏭ Дальше по плану: **S05** (baseline-миграция БД) и **S07** (OpenAPI + Bruno-коллекция)

Lombok/MapStruct (backend) и Vitest (frontend) пока не подключены — добавляются в своих сессиях по плану.

## Стек

| Слой | Технология |
|---|---|
| Frontend | React 18 + TypeScript + Vite + SCSS Modules (ESLint + Prettier) |
| Backend | Java 21 + Spring Boot 3 + Spring Security + JPA + Flyway |
| БД / кэш | PostgreSQL 16, Redis 7 |
| Инфра | Docker Compose, Nginx, GitHub Actions |
| CI | gitleaks + backend `mvn verify` + frontend lint/build — на каждый PR и push в `main`/`develop` |

## Быстрый старт (нужен только Docker)

```bash
cp .env.example .env      # при необходимости поменять пароли
make up                   # собрать и поднять postgres + redis + backend + frontend
```

- Frontend: http://localhost:5173 (UI-kit: http://localhost:5173/ui-kit)
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

## Проверка перед пушем

```bash
mvn -B verify -f backend/pom.xml                                   # backend
npm --prefix frontend run lint && npm --prefix frontend run build  # frontend
```

Полный git-ритуал сессии — план, Часть 6.

## Структура

```
backend/             # Spring Boot (Java 21)
frontend/            # React + Vite (TypeScript, SCSS Modules)
shared/openapi.yaml  # контракт API
bruno/               # API-коллекции Bruno (наполняется с S07)
infra/               # nginx (прод)
docs/
├── plan/            # чек-лист реализации — источник истины по проекту
├── brandbook/       # бренд-бук (6 страниц JPEG)
├── decisions.md     # хронология архитектурных решений (ADR)
└── runbooks.md
AGENTS.md            # входная точка для AI-ассистентов
.github/workflows/   # CI (GitHub Actions)
```

## Git-процесс

Ветки: `develop` — интеграция и **ветка по умолчанию на GitHub** (новые PR автоматически
целятся в неё), `main` — стабильная. Работа — от сессий чек-листа:
`feature/<dev>-s<номер>-<кратко>` → PR → ревью + зелёный CI → squash merge в `develop`.
Коммиты — Conventional Commits. Миграции БД пишет только Dev B.
