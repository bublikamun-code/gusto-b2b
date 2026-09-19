#!/usr/bin/env bash
# Прод-подобный бэкенд для нагрузочных тестов (S39).
#
# Dev-контейнер backend работает через `mvn spring-boot:run` с урезанным JIT
# (-XX:TieredStopAtLevel=1) — p95 против него недостоверен. Скрипт собирает
# обычный jar и поднимает его рядом со стеком на порту 8081.
#
# Использование:
#   bash k6/prod-backend.sh up    # собрать (если нужно) и запустить
#   bash k6/prod-backend.sh down  # остановить и удалить контейнер
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER=gusto-backend-prod
PORT=8081
MVN_IMAGE=maven:3.9-eclipse-temurin-21

# Сеть compose-стека (postgres/redis должны быть доступны по именам сервисов)
NETWORK=$(docker inspect gusto-b2b-postgres-1 --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)
if [ -z "${NETWORK:-}" ]; then
  echo "Сеть compose-стека не найдена — поднимите стек: make up" >&2
  exit 1
fi

# JWT_SECRET обязателен приложению (application.yml), берём из .env
set -a
source .env

build_jar() {
  docker run --rm -v "$(pwd)/backend":/app -v gusto-maven-cache:/root/.m2 -w /app \
    "$MVN_IMAGE" mvn -B -q -DskipTests package
}

case "${1:-}" in
  up)
    if docker ps --format '{{.Names}}' | grep -q "^$CONTAINER$"; then
      echo "$CONTAINER уже запущен"
      exit 0
    fi
    JAR=$(ls backend/target/gusto-b2b-backend-*.jar 2>/dev/null | head -1 || true)
    if [ -z "$JAR" ]; then
      echo "Сборка jar..."
      build_jar
    fi
    JAR_NAME=$(basename "$JAR")
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    docker run -d --name "$CONTAINER" --network "$NETWORK" -p "$PORT:8080" \
      -v "$(pwd)/backend/target":/app/target \
      -w /app \
      -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/${POSTGRES_DB:-gusto}" \
      -e SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-gusto}" \
      -e SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-gusto}" \
      -e SPRING_DATA_REDIS_HOST=redis \
      -e JWT_SECRET="$JWT_SECRET" \
      -e ADMIN_EMAIL="${ADMIN_EMAIL:-admin@gustomeat.by}" \
      -e ADMIN_PASSWORD="${ADMIN_PASSWORD:-change-me}" \
      -e APP_BASE_URL="${APP_BASE_URL:-http://localhost:5173}" \
      "$MVN_IMAGE" java -jar "target/$JAR_NAME"
    echo "Ждём /healthz на :$PORT..."
    for _ in $(seq 1 60); do
      if curl -sf "http://localhost:$PORT/healthz" >/dev/null; then
        echo "$CONTAINER готов: http://localhost:$PORT"
        exit 0
      fi
      sleep 2
    done
    echo "$CONTAINER не поднялся — логи: docker logs $CONTAINER" >&2
    exit 1
    ;;
  down)
    docker rm -f "$CONTAINER" >/dev/null 2>&1 && echo "$CONTAINER остановлен" || echo "$CONTAINER не запущен"
    ;;
  *)
    echo "Использование: $0 {up|down}" >&2
    exit 1
    ;;
esac
