import type { ApiEnvelope, ApiError, ApiMeta } from "../types/auth";

const API_BASE = "/api/v1";

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  token?: string;
}

export interface ListResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

async function apiRawRequest<T>(path: string, options: RequestOptions = {}): Promise<ApiEnvelope<T>> {
  const { token, body, ...rest } = options;
  const url = `${API_BASE}${path}`;
  const headers = new Headers(rest.headers);
  headers.set("Accept", "application/json");
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...rest,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

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
  return {
    items: (envelope.data ?? []) as T[],
    page: meta.page ?? 0,
    size: meta.size ?? 20,
    total: meta.total ?? 0,
  };
}
