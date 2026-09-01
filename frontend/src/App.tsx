import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthInit } from "./components/auth/AuthInit";
import { AuthRedirect, ProtectedRoute, RoleGuard } from "./components/auth/AuthGuards";
import { AdminLayout } from "./components/admin/AdminLayout";
import { PublicLayout } from "./components/public/PublicLayout";
import NotFoundPage from "./pages/NotFoundPage";
import UiKitPage from "./pages/UiKitPage";
import LoginPage from "./pages/LoginPage";
import RequestPasswordResetPage from "./pages/RequestPasswordResetPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminCompaniesPage from "./pages/admin/AdminCompaniesPage";
import AdminProductsPage from "./pages/admin/AdminProductsPage";
import ManagerDashboardPage from "./pages/manager/ManagerDashboardPage";
import CabinetDashboardPage from "./pages/cabinet/CabinetDashboardPage";
import CabinetCatalogPage from "./pages/cabinet/CabinetCatalogPage";
import HomePage from "./pages/public/HomePage";
import CatalogPage from "./pages/public/CatalogPage";
import ProductPage from "./pages/public/ProductPage";
import DeliveryPage from "./pages/public/DeliveryPage";
import AboutPage from "./pages/public/AboutPage";

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

          <Route element={<RoleGuard allowed={["ADMIN", "ACCOUNTANT"]} />}>
            <Route element={<AdminLayout />}>
              <Route path="/admin" element={<Navigate to="/admin/users" replace />} />
              <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
              <Route path="/admin/users" element={<AdminUsersPage />} />
              <Route path="/admin/companies" element={<AdminCompaniesPage />} />
              <Route path="/admin/products" element={<AdminProductsPage />} />
            </Route>
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route path="/manager" element={<ManagerDashboardPage />} />
            <Route path="/cabinet" element={<CabinetDashboardPage />} />
            <Route path="/cabinet/catalog" element={<CabinetCatalogPage />} />
          </Route>

          <Route element={<PublicLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/catalog" element={<CatalogPage />} />
            <Route path="/products/:sku" element={<ProductPage />} />
            <Route path="/delivery" element={<DeliveryPage />} />
            <Route path="/about" element={<AboutPage />} />
          </Route>

          <Route path="/ui-kit" element={<UiKitPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthInit>
    </BrowserRouter>
  );
}
