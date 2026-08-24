import { create } from "zustand";
import type { User } from "../types/auth";

interface AuthState {
  accessToken: string | null;
  user: User | null;
  isLoading: boolean;
  setAuth: (token: string, user: User) => void;
  setUser: (user: User) => void;
  setLoading: (loading: boolean) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isLoading: true,
  setAuth: (accessToken, user) => set({ accessToken, user, isLoading: false }),
  setUser: (user) => set({ user }),
  setLoading: (isLoading) => set({ isLoading }),
  clearAuth: () => set({ accessToken: null, user: null, isLoading: false }),
}));
