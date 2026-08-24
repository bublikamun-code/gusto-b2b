import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthInit } from "./components/auth/AuthInit";
import { AuthRedirect, ProtectedRoute } from "./components/auth/AuthGuards";
import NotFoundPage from "./pages/NotFoundPage";
import StubPage from "./pages/StubPage";
import UiKitPage from "./pages/UiKitPage";
import LoginPage from "./pages/LoginPage";
import RequestPasswordResetPage from "./pages/RequestPasswordResetPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import ManagerDashboardPage from "./pages/manager/ManagerDashboardPage";
import CabinetDashboardPage from "./pages/cabinet/CabinetDashboardPage";

export default function App() {
  return (
    <BrowserRouter>
      <AuthInit>
        <Routes>
          <Route
            path="/login"
            element={
              <AuthRedirect>
                <LoginPage />
              </AuthRedirect>
            }
          />
          <Route path="/request-password-reset" element={<RequestPasswordResetPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          <Route element={<ProtectedRoute />}>
            <Route path="/admin" element={<AdminDashboardPage />} />
            <Route path="/manager" element={<ManagerDashboardPage />} />
            <Route path="/cabinet" element={<CabinetDashboardPage />} />
          </Route>

          <Route path="/" element={<StubPage />} />
          <Route path="/ui-kit" element={<UiKitPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthInit>
    </BrowserRouter>
  );
}
