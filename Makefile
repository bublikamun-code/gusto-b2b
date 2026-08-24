up:
	docker compose up -d --build

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

.PHONY: up infra down clean logs psql
