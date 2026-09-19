import http from "k6/http";
import { check } from "k6";

/**
 * Нагрузочный тест каталога (S39): 100 RPS, приёмка p95 < 300 мс.
 * Запуск: make load-catalog  (k6 в Docker, цель — backend :8080)
 * Публичный эндпоинт, авторизация не нужна.
 */

const BASE = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    // разогрев: JIT (C2) успевает скомпилировать горячие пути,
    // иначе замеряем компиляцию, а не приложение
    warmup: {
      executor: "constant-arrival-rate",
      rate: 20,
      timeUnit: "1s",
      duration: "90s",
      preAllocatedVUs: 20,
      maxVUs: 30,
    },
    catalog: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 100), // запросов в секунду
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: 60,
      maxVUs: 100,
      startTime: "100s", // после разогрева
    },
  },
  thresholds: {
    // приёмка S39: p95 каталога < 300 мс (на разогретом JVM)
    "http_req_duration{scenario:catalog}": ["p(95)<300"],
    "http_req_failed{scenario:catalog}": ["rate<0.01"],
  },
};

export default function () {
  const res = http.get(`${BASE}/api/v1/catalog/products?page=0&size=20`);
  check(res, {
    "status 200": (r) => r.status === 200,
    "есть товары": (r) => (r.json("data") ?? []).length > 0,
  });
}
