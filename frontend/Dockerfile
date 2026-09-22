FROM node:20

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

EXPOSE 5173
# Анонимный том /app/node_modules засеивается из bind-монта хоста (мак-бинарники),
# из-за чего rollup падает с MODULE_NOT_FOUND на linux-arm64. Если нативный модуль
# чужой платформы — переустанавливаем зависимости в том перед стартом vite.
CMD ["sh", "-c", "node -e 'require(\"rollup/dist/native.js\")' 2>/dev/null || { rm -rf node_modules && npm ci --no-audit --no-fund; }; exec npm run dev -- --host"]
