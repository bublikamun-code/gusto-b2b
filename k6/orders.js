import http from "k6/http";
import { check, sleep } from "k6";

/**
 * Нагрузочный тест создания заказа (S39): 20 RPS. Авторизованный админ
 * оформляет заказы от имени демо-компании с товарами со склада; в setup()
 * заводится компания и приход на склад, чтобы остаток не кончился посреди прогона.
 * Запуск: make load-orders  (k6 в Docker, цель — backend :8080)
 */

const BASE = __ENV.BASE_URL || "http://host.docker.internal:8080";
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || "admin@gustomeat.by";
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || "change-me";
const LOCATION_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"; // Основной склад (V9)

export const options = {
  scenarios: {
    warmup: {
      executor: "constant-arrival-rate",
      rate: 5,
      timeUnit: "1s",
      duration: "60s",
      preAllocatedVUs: 5,
      maxVUs: 10,
    },
    orders: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 20), // заказов в секунду
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: 10,
      maxVUs: 30,
      startTime: "70s", // после разогрева
    },
  },
  thresholds: {
    "http_req_duration{scenario:orders}": ["p(95)<1000"],
    "http_req_failed{scenario:orders}": ["rate<0.01"],
  },
};

function api(method, path, token, body, idempotencyKey) {
  const params = {
    headers: { "Content-Type": "application/json" },
  };
  if (token) params.headers.Authorization = `Bearer ${token}`;
  if (idempotencyKey) params.headers["Idempotency-Key"] = idempotencyKey;
  return http.request(method, `${BASE}/api/v1${path}`, body ? JSON.stringify(body) : null, params);
}

function envelope(res) {
  try {
    return res.json();
  } catch {
    return {};
  }
}

export function setup() {
  const login = api("POST", "/auth/login", null, {
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  });
  check(login, { "логин 200": (r) => r.status === 200 });
  const token = envelope(login).data.accessToken;

  // компания для заказов
  const suffix = `${Date.now()}`;
  const company = api("POST", "/admin/companies", token, {
    name: `ООО «k6 ${suffix}»`,
    unp: String(199000000 + (Date.now() % 999999)),
    legalAddress: "г. Минск, k6",
    status: "ACTIVE",
  });
  check(company, { "компания создана": (r) => r.status === 201 || r.status === 200 });
  const companyId = envelope(company).data.id;

  // товары каталога (первые 5)
  const products = api("GET", "/catalog/products?page=0&size=5", token);
  const items = envelope(products).data || [];
  if (items.length === 0) {
    throw new Error("Каталог пуст — наполните его перед нагрузочным тестом");
  }

  // приход на склад: по 1000 единиц каждому товару, чтобы остаток не кончился
  const incoming = api("POST", "/warehouse/documents", token, {
    type: "INCOMING",
    locationToId: LOCATION_ID,
    note: "k6 подготовка",
    items: items.map((p) => ({ productId: p.id, quantity: 1000 })),
  });
  check(incoming, { "приход создан": (r) => r.status === 201 || r.status === 200 });
  const documentId = envelope(incoming).data.id;
  const confirmed = api("POST", `/warehouse/documents/${documentId}/confirm`, token, {});
  check(confirmed, { "приход подтверждён": (r) => r.status === 200 });

  return { token, companyId, products: items };
}

export default function (data) {
  const product = data.products[Math.floor(Math.random() * data.products.length)];
  const res = api(
    "POST",
    "/orders",
    data.token,
    {
      customerCompanyId: data.companyId,
      deliveryType: "PICKUP",
      recipientName: "k6",
      recipientPhone: "+375290000000",
      items: [{ productId: product.id, quantity: 1 }],
    },
    __VU + "-" + __ITER + "-" + Date.now(),
  );
  check(res, {
    "заказ создан": (r) => r.status === 201 || r.status === 200,
  });
  sleep(0.1);
}
