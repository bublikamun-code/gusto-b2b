#!/usr/bin/env bash
# Выпуск первого сертификата Let's Encrypt (S41). Запускается один раз на VPS
# ДО старта nginx (standalone занимает порт 80). Продление — контейнер certbot
# в docker-compose.prod.yml.
#
# Использование: DOMAIN=gustomeat.by EMAIL=admin@gustomeat.by bash infra/scripts/init-letsencrypt.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

DOMAIN="${DOMAIN:?установите DOMAIN}"
EMAIL="${EMAIL:?установите EMAIL}"

docker network inspect "${COMPOSE_PROJECT_NAME:-gusto-b2b-prod}_default" >/dev/null 2>&1 || true

docker run --rm -p 80:80 \
  -v gusto-b2b-prod_certbot-certs:/etc/letsencrypt \
  certbot/certbot certonly --standalone \
  -d "$DOMAIN" -d "www.$DOMAIN" \
  --email "$EMAIL" --agree-tos --no-eff-email \
  --keep-until-expiring

echo "Сертификат выпущен. Запускайте: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d"
