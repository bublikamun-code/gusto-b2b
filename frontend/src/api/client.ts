import type { ApiEnvelope, ApiError, ApiMeta } from "../types/auth";
import { useAuthStore } from "../store/authStore";

const API_BASE = "/api/v1";

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  token?: string;
}

type InternalOptions = RequestOptions & { __retried?: boolean };

// Single-flight refresh: параллельные 401 не плодят refresh-запросы
let refreshPromise: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { Accept: "application/json" },
      credentials: "include",
    });
    if (!response.ok) return null;
    const envelope = (await response.json()) as ApiEnvelope<{ accessToken: string }>;
    const token = envelope.data?.accessToken;
    if (!token) return null;
    useAuthStore.setState({ accessToken: token });
    return token;
  } catch {
    return null;
  }
}

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = performRefresh();
    void refreshPromise.then(
      () => {
        refreshPromise = null;
      },
      () => {
        refreshPromise = null;
      },
    );
  }
  return refreshPromise;
}

export interface ListResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

async function apiRawRequest<T>(path: string, options: InternalOptions = {}): Promise<ApiEnvelope<T>> {
  const { token, body, __retried, ...rest } = options;
  const url = `${API_BASE}${path}`;
  const headers = new Headers(rest.headers);
  headers.set("Accept", "application/json");
  const isFormData = body instanceof FormData;
  if (body !== undefined && !isFormData) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...rest,
    headers,
    credentials: "include",
    body: body !== undefined ? (isFormData ? body : JSON.stringify(body)) : undefined,
  });

  // Access token истёк: один refresh и один повтор запроса
  if (response.status === 401 && token && !__retried) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      return apiRawRequest<T>(path, { ...options, token: newToken, __retried: true });
    }
    useAuthStore.getState().clearAuth();
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;

  if (!response.ok || envelope.error) {
    throw (envelope.error ?? {
      code: "INTERNAL",
      message: "Неизвестная ошибка сервера",
    }) as ApiError;
  }

  return envelope;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const envelope = await apiRawRequest<T>(path, options);
  return envelope.data as T;
}

export async function apiListRequest<T>(path: string, options: RequestOptions = {}): Promise<ListResponse<T>> {
  const envelope = await apiRawRequest<T[]>(path, options);
  const meta: ApiMeta = envelope.meta ?? {};
  const items = (envelope.data ?? []) as T[];
  return {
    items,
    page: meta.page ?? 0,
    size: meta.size ?? 20,
    total: meta.total ?? items.length,
  };
}
