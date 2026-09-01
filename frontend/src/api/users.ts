import { apiListRequest, apiRequest } from "./client";
import {
  createMockUser,
  deleteMockUser,
  getMockUser,
  isMockEnabled,
  listMockUsers,
  resetMockPassword,
  updateMockUser,
} from "./adminMocks";
import type {
  CreateUserRequest,
  PagedResponse,
  ResetPasswordResult,
  UpdateUserRequest,
  User,
  UserFilters,
} from "../types/admin";
import { useAuthStore } from "../store/authStore";

function buildQuery(filters: Record<string, unknown>): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === "" || value === null) return;
    params.set(key, String(value));
  });
  const query = params.toString();
  return query ? `?${query}` : "";
}

export function listUsers(filters: UserFilters): Promise<PagedResponse<User>> {
  if (isMockEnabled()) return Promise.resolve(listMockUsers(filters));
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiListRequest<User>(`/admin/users${buildQuery(filters as Record<string, unknown>)}`, {
    method: "GET",
    token,
  });
}

export function getUser(id: string): Promise<User> {
  if (isMockEnabled()) {
    const user = getMockUser(id);
    if (!user) return Promise.reject({ code: "NOT_FOUND", message: "Пользователь не найден" });
    return Promise.resolve(user);
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<User>(`/admin/users/${id}`, { method: "GET", token });
}

export function createUser(body: CreateUserRequest): Promise<User> {
  if (isMockEnabled()) return Promise.resolve(createMockUser(body));
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<User>("/admin/users", { method: "POST", token, body });
}

export function updateUser(id: string, body: UpdateUserRequest): Promise<User> {
  if (isMockEnabled()) {
    try {
      return Promise.resolve(updateMockUser(id, body));
    } catch {
      return Promise.reject({ code: "NOT_FOUND", message: "Пользователь не найден" });
    }
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<User>(`/admin/users/${id}`, { method: "PUT", token, body });
}

export function deleteUser(id: string): Promise<void> {
  if (isMockEnabled()) {
    try {
      deleteMockUser(id);
      return Promise.resolve();
    } catch {
      return Promise.reject({ code: "NOT_FOUND", message: "Пользователь не найден" });
    }
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<void>(`/admin/users/${id}`, { method: "DELETE", token });
}

export async function resetUserPassword(id: string): Promise<ResetPasswordResult> {
  if (isMockEnabled()) {
    try {
      return resetMockPassword(id);
    } catch {
      return Promise.reject({ code: "NOT_FOUND", message: "Пользователь не найден" });
    }
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  const result = await apiRequest<{ userId: string; temporaryPassword: string }>(
    `/admin/users/${id}/reset-password`,
    { method: "POST", token },
  );
  return { temporaryPassword: result.temporaryPassword };
}
