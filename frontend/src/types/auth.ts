export type Role =
  | "ADMIN"
  | "ACCOUNTANT"
  | "MANAGER"
  | "CUSTOMER_LEGAL"
  | "CUSTOMER_INDIVIDUAL";

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone?: string | null;
  role: Role;
  companyId?: string | null;
  isActive: boolean;
  totpEnabled?: boolean;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown> | null;
}

export interface ApiMeta {
  page?: number;
  size?: number;
  total?: number;
}

export interface ApiEnvelope<T> {
  data: T | null;
  meta: ApiMeta & Record<string, unknown>;
  error: ApiError | null;
}

export interface LoginRequest {
  email: string;
  password: string;
  totpCode?: string;
}

export interface LoginPayload {
  accessToken: string;
  expiresIn: number;
  user: User;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirm {
  token: string;
  newPassword: string;
}

export interface TotpVerifyRequest {
  code: string;
}

export interface TotpSetupPayload {
  secret: string;
  otpauthUrl: string;
}

export interface TotpRecoveryPayload {
  recoveryCodes: string[];
}
