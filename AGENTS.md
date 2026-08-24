# AGENTS.md — правила для AI-ассистентов

**Единственный источник истины по проекту — план:** `docs/plan/GUSTO_B2B_Final_Checklist.md`.
Этот файл его НЕ дублирует: при любом расхождении верь плану. Здесь — только минимум,
нужный в каждой сессии.

## Проект (одной строкой)

B2B-портал «Густо» (мясной гастроном): React 18 + Vite / Spring Boot 3 (Java 21) /
PostgreSQL 16 / Redis 7 / Docker Compose.

Запуск: `cp .env.example .env` (один раз), далее `make up`.
Проверка живости: `curl localhost:8080/healthz`, фронт: `http://localhost:5173`.
Логи: `make logs`. Полная пересборка с чистой базой: `make clean && make up`.

## Ритуал каждой сессии (подробно — план, Часть 6.3)

1. **Начало:** `git fetch origin && git status` — сверка незакоммиченных изменений с прошлой
   сессии (нашёл — показать владельцу, оформить отдельным коммитом) →
   `git checkout develop && git pull` → `make up && make logs` (миграции применились).
2. **Работа:** ветка `feature/<dev>-s<номер>-<кратко>` от свежего `develop`.
3. **Конец:** `git status --short` (мусор и `.env` не коммитим) → тесты зелёные →
   commit → push → PR → ревью → merge → отметить прогресс в плане тем же PR.

## Железные правила

- Коммиты: Conventional Commits + метка `[S<номер>]`. В `main`/`develop` — только через PR.
- **Миграции Flyway пишет только Dev B.** Существующие миграции никогда не переписываются —
  только новые файлы `V<N>__<имя>.sql` (иначе у коллеги падает checksum).
- Демо-данные — только сид-миграциями, не руками в базу. База у каждого локальная,
  её содержимое между машинами не синхронизируется.
- `shared/openapi.yaml` обновляется в той же сессии, где меняется эндпоинт.
- Прогресс: под заголовком сессии в плане — `> ✅ Выполнено ГГГГ-ММ-ДД [A|B|AB] — <что сделано>`.
- Тесты перед пушем: `mvn -B verify -f backend/pom.xml` (backend) /
  `npm --prefix frontend run build` (frontend).
- ESLint + Prettier — первым коммитом Dev A; Lombok + MapStruct — первым коммитом Dev B.

## CI

GitHub Actions на каждый PR и push. Красный CI мержить нельзя — чинить.

Статус сборок (если на этой машине есть токен `~/.config/gusto-b2b/token`):

```bash
curl -s -H "Authorization: Bearer $(cat ~/.config/gusto-b2b/token)" \
  "https://api.github.com/repos/bublikamun-code/gusto-b2b/actions/runs?per_page=3"
```
