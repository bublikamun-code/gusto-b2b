up:
	docker compose up -d --build --renew-anon-volumes

infra:
	docker compose up -d postgres redis

down:
	docker compose down

clean:
	docker compose down -v

logs:
	docker compose logs -f backend

psql:
	docker compose exec postgres psql -U $${POSTGRES_USER:-gusto} -d $${POSTGRES_DB:-gusto}

# E2E полного цикла (S39): нужен поднятый стек (make up).
# Флеш Redis перед прогоном сбрасывает rate limit логина (5/15 мин на email).
e2e:
	docker compose exec redis redis-cli flushall || true
	cd e2e && npm ci && npx playwright test

# Нагрузочные тесты (S39): против прод-подобного бэкенда на :8081
# (dev-контейнер работает с урезанным JIT, его p95 недостоверен).
# Требуется установленный k6 (brew install k6); приёмка: p95 каталога < 300 мс.
load-catalog:
	bash k6/prod-backend.sh up
	BASE_URL=http://localhost:8081 k6 run k6/catalog.js

load-orders:
	bash k6/prod-backend.sh up
	BASE_URL=http://localhost:8081 k6 run k6/orders.js

load-down:
	bash k6/prod-backend.sh down

.PHONY: up infra down clean logs psql e2e load-catalog load-orders load-down
