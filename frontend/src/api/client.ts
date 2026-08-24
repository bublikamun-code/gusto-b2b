import type { ApiEnvelope, ApiError } from "../types/auth";

const API_BASE = "/api/v1";

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  token?: string;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
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

  return envelope.data as T;
}
