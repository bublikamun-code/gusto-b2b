import { apiRequest } from "./client";
import { useAuthStore } from "../store/authStore";
import type {
  LoginPayload,
  LoginRequest,
  PasswordResetConfirm,
  PasswordResetRequest,
  TotpRecoveryPayload,
  TotpSetupPayload,
  TotpVerifyRequest,
  User,
} from "../types/auth";

export function login(body: LoginRequest): Promise<LoginPayload> {
  return apiRequest<LoginPayload>("/auth/login", { method: "POST", body });
}

export function refresh(): Promise<{ accessToken: string; expiresIn: number }> {
  return apiRequest<{ accessToken: string; expiresIn: number }>("/auth/refresh", {
    method: "POST",
  });
}

export function logout(): Promise<void> {
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<void>("/auth/logout", { method: "POST", token });
}

export function me(): Promise<User> {
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<User>("/auth/me", { method: "GET", token });
}

export function requestPasswordReset(body: PasswordResetRequest): Promise<void> {
  return apiRequest<void>("/auth/password-reset/request", { method: "POST", body });
}

export function confirmPasswordReset(body: PasswordResetConfirm): Promise<void> {
  return apiRequest<void>("/auth/password-reset/confirm", { method: "POST", body });
}

export function enable2FA(): Promise<TotpSetupPayload> {
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<TotpSetupPayload>("/auth/2fa/enable", { method: "POST", token });
}

export function verify2FA(body: TotpVerifyRequest): Promise<TotpRecoveryPayload> {
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<TotpRecoveryPayload>("/auth/2fa/verify", { method: "POST", token, body });
}
