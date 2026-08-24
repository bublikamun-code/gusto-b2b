import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiTarget = process.env.VITE_API_PROXY ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": { target: apiTarget, changeOrigin: true },
      "/healthz": { target: apiTarget, changeOrigin: true },
      "/readyz": { target: apiTarget, changeOrigin: true },
    },
  },
});
