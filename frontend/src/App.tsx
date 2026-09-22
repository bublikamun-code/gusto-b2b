import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthInit } from "./components/auth/AuthInit";
import { AdminIndexRedirect, AuthRedirect, ProtectedRoute, RoleGuard } from "./components/auth/AuthGuards";
import { AdminLayout } from "./components/admin/AdminLayout";
import { WarehouseLayout } from "./components/warehouse/WarehouseLayout";
import { PublicLayout } from "./components/public/PublicLayout";
import { CabinetLayout } from "./components/cabinet/CabinetLayout";
import { ManagerLayout } from "./components/manager/ManagerLayout";
import NotFoundPage from "./pages/NotFoundPage";
import UiKitPage from "./pages/UiKitPage";
import LoginPage from "./pages/LoginPage";
import RequestPasswordResetPage from "./pages/RequestPasswordResetPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import ConfirmEmailPage from "./pages/ConfirmEmailPage";
import RegisterPage from "./pages/RegisterPage";
import CabinetCartPage from "./pages/cabinet/CabinetCartPage";
import CabinetProfilePage from "./pages/cabinet/CabinetProfilePage";
import CabinetDocumentsPage from "./pages/cabinet/CabinetDocumentsPage";
import DocumentsPage from "./pages/documents/DocumentsPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminCompaniesPage from "./pages/admin/AdminCompaniesPage";
import AdminProductsPage from "./pages/admin/AdminProductsPage";
import AdminCmsPage from "./pages/admin/AdminCmsPage";
import AdminSettingsPage from "./pages/admin/AdminSettingsPage";
import AdminAuditPage from "./pages/admin/AdminAuditPage";
import AdminIntegrationPage from "./pages/admin/AdminIntegrationPage";
import ManagerOrdersPage from "./pages/manager/ManagerOrdersPage";
import ManagerOrderCreatePage from "./pages/manager/ManagerOrderCreatePage";
import CrmInboxPage from "./pages/manager/CrmInboxPage";
import CrmLeadsPage from "./pages/manager/CrmLeadsPage";
import CrmClientsPage from "./pages/manager/CrmClientsPage";
import CrmDashboardPage from "./pages/manager/CrmDashboardPage";
import CabinetDashboardPage from "./pages/cabinet/CabinetDashboardPage";
import CabinetCatalogPage from "./pages/cabinet/CabinetCatalogPage";
import CabinetOrdersPage from "./pages/cabinet/CabinetOrdersPage";
import HomePage from "./pages/public/HomePage";
import CatalogPage from "./pages/public/CatalogPage";
import ProductPage from "./pages/public/ProductPage";
import ContactsPage from "./pages/public/ContactsPage";
import PrivacyPage from "./pages/public/PrivacyPage";
import BecomeClientPage from "./pages/public/BecomeClientPage";
import CmsPageView from "./pages/public/CmsPageView";
import { initChat } from "./chat";
import WarehouseBalancePage from "./pages/warehouse/WarehouseBalancePage";
import WarehouseDocumentsPage from "./pages/warehouse/WarehouseDocumentsPage";
import WarehouseSuppliersPage from "./pages/warehouse/WarehouseSuppliersPage";
import WarehousePurchaseOrdersPage from "./pages/warehouse/WarehousePurchaseOrdersPage";
import WarehouseReportsPage from "./pages/warehouse/WarehouseReportsPage";

export default function App() {
  // Чат-виджет (S34): Crisp при наличии VITE_CRISP_WEBSITE_ID, иначе no-op
  initChat();
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
          <Route path="/confirm-email" element={<ConfirmEmailPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<RoleGuard allowed={["ADMIN", "ACCOUNTANT"]} />}>
            <Route element={<AdminLayout />}>
              <Route path="/admin" element={<AdminIndexRedirect />} />
              <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
              {/* Документы (S27): счета/накладные, создание из заказа — первая раздел-страница бухгалтера */}
              <Route path="/admin/documents" element={<DocumentsPage />} />
              {/* Обмен 1С (S38): мастер импорта/экспорта — ADMIN/ACCOUNTANT, как S35 */}
              <Route path="/admin/integration" element={<AdminIntegrationPage />} />
              {/* CRUD-разделы только для ADMIN: у других ролей бэкенд на /admin/** отдаёт 403 */}
              <Route element={<RoleGuard allowed={["ADMIN"]} />}>
                <Route path="/admin/users" element={<AdminUsersPage />} />
                <Route path="/admin/companies" element={<AdminCompaniesPage />} />
                <Route path="/admin/products" element={<AdminProductsPage />} />
                {/* CMS (S37): статьи draft/published */}
                <Route path="/admin/cms" element={<AdminCmsPage />} />
                {/* Операционный центр (S38): настройки и журнал аудита — ADMIN */}
                <Route path="/admin/settings" element={<AdminSettingsPage />} />
                <Route path="/admin/audit" element={<AdminAuditPage />} />
              </Route>
            </Route>
          </Route>

          {/* Кабинет клиента: единый CabinetLayout с шапкой; guards сохраняются */}
          <Route element={<ProtectedRoute />}>
            <Route element={<CabinetLayout />}>
              <Route path="/cabinet" element={<CabinetDashboardPage />} />
              <Route path="/cabinet/catalog" element={<CabinetCatalogPage />} />
              <Route path="/cabinet/cart" element={<CabinetCartPage />} />
              <Route path="/cabinet/profile" element={<CabinetProfilePage />} />
              {/* Заказы клиента (S23): только клиенты, у персонала свои разделы */}
              <Route element={<RoleGuard allowed={["CUSTOMER_LEGAL", "CUSTOMER_INDIVIDUAL"]} />}>
                <Route path="/cabinet/orders" element={<CabinetOrdersPage />} />
              </Route>
              {/* Документы юрлица (S27): счета и накладные; физлицо документов не имеет (2.1) */}
              <Route element={<RoleGuard allowed={["CUSTOMER_LEGAL"]} />}>
                <Route path="/cabinet/documents" element={<CabinetDocumentsPage />} />
              </Route>
            </Route>
          </Route>

          {/* Заказы в работе (S22/S23): лента, пул «не назначено», заказ от имени клиента.
              Роль проверяет и бэкенд (2.1) — для глубоких ссылок.
              Единый каркас бэк-офиса: то же меню, что на админке и складе. */}
          <Route element={<RoleGuard allowed={["MANAGER", "ADMIN"]} />}>
            <Route element={<ManagerLayout />}>
              <Route path="/manager/orders" element={<ManagerOrdersPage />} />
              <Route path="/manager/orders/new" element={<ManagerOrderCreatePage />} />
              <Route path="/manager/documents" element={<DocumentsPage />} />
              {/* CRM UI (S30): единое окно, воронка, клиенты, дашборд */}
              <Route path="/manager/inbox" element={<CrmInboxPage />} />
              <Route path="/manager/leads" element={<CrmLeadsPage />} />
              <Route path="/manager/clients" element={<CrmClientsPage />} />
              <Route path="/manager/dashboard" element={<CrmDashboardPage />} />
            </Route>
          </Route>

          {/* Склад (S18.4): матрица 2.1 — ADMIN/ACCOUNTANT/MANAGER; бэкенд отдаёт 403 остальным */}
          <Route element={<RoleGuard allowed={["ADMIN", "ACCOUNTANT", "MANAGER"]} />}>
            <Route path="/warehouse" element={<WarehouseLayout />}>
              <Route index element={<Navigate to="/warehouse/balance" replace />} />
              <Route path="balance" element={<WarehouseBalancePage />} />
              <Route path="documents" element={<WarehouseDocumentsPage />} />
              <Route path="suppliers" element={<WarehouseSuppliersPage />} />
              <Route path="purchase-orders" element={<WarehousePurchaseOrdersPage />} />
              <Route path="reports" element={<WarehouseReportsPage />} />
            </Route>
          </Route>

          <Route element={<PublicLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/catalog" element={<CatalogPage />} />
            <Route path="/products/:sku" element={<ProductPage />} />
            {/* CMS (S37): «О нас» и «Доставка» из БД (fallback — старые страницы) */}
            <Route path="/delivery" element={<CmsPageView slug="delivery" fallbackTitle="Доставка" />} />
            <Route path="/about" element={<CmsPageView slug="about" fallbackTitle="О нас" />} />
            <Route path="/contacts" element={<ContactsPage />} />
            <Route path="/become-client" element={<BecomeClientPage />} />
            <Route path="/privacy" element={<PrivacyPage />} />
          </Route>

          <Route path="/ui-kit" element={<UiKitPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthInit>
    </BrowserRouter>
  );
}
