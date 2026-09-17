import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import { AdminIndexRedirect, AuthRedirect, ProtectedRoute, RoleGuard } from "./AuthGuards";
import { useAuthStore } from "../../store/authStore";
import type { User } from "../../types/auth";

const user = (role: User["role"]): User => ({
  id: "u-1",
  email: "user@test.by",
  fullName: "Тест Тестов",
  role,
  companyId: null,
  isActive: true,
});

/**
 * Гварды-«layout» (ProtectedRoute/RoleGuard/AdminIndexRedirect) рендерят <Outlet/>,
 * поэтому проверяем через вложенный index-роут — как в App.tsx.
 */
function renderAt(path: string, guard: ReactNode) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login-page</div>} />
        <Route path="/" element={<div>home-page</div>} />
        <Route path="/admin/users" element={<div>admin-users</div>} />
        <Route path="/admin/dashboard" element={<div>admin-dashboard</div>} />
        <Route path="/manager" element={<div>manager-page</div>} />
        <Route path="/cabinet" element={<div>cabinet-page</div>} />
        <Route path="/guarded" element={guard}>
          <Route index element={<div>guarded-content</div>} />
        </Route>
        <Route
          path="/auth-redirect"
          element={<AuthRedirect>guarded-content</AuthRedirect>}
        />
        <Route path="*" element={<div>not-found</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  useAuthStore.setState({ user: null, isLoading: true });
});

describe("ProtectedRoute", () => {
  it("redirects anonymous user to /login", () => {
    useAuthStore.setState({ user: null, isLoading: false });
    const { queryByText } = renderAt("/guarded", <ProtectedRoute />);
    expect(queryByText("login-page")).toBeInTheDocument();
    expect(queryByText("guarded-content")).not.toBeInTheDocument();
  });

  it("renders nested content for authenticated user", () => {
    useAuthStore.setState({ user: user("CUSTOMER_LEGAL"), isLoading: false });
    const { queryByText } = renderAt("/guarded", <ProtectedRoute />);
    expect(queryByText("guarded-content")).toBeInTheDocument();
  });
});

describe("RoleGuard", () => {
  it("lets allowed role through", () => {
    useAuthStore.setState({ user: user("ADMIN"), isLoading: false });
    const { queryByText } = renderAt(
      "/guarded",
      <RoleGuard allowed={["ADMIN", "ACCOUNTANT"]} />,
    );
    expect(queryByText("guarded-content")).toBeInTheDocument();
  });

  it("redirects disallowed role to home", () => {
    useAuthStore.setState({ user: user("ACCOUNTANT"), isLoading: false });
    const { queryByText } = renderAt("/guarded", <RoleGuard allowed={["ADMIN"]} />);
    expect(queryByText("home-page")).toBeInTheDocument();
    expect(queryByText("guarded-content")).not.toBeInTheDocument();
  });

  it("redirects anonymous user to /login", () => {
    useAuthStore.setState({ user: null, isLoading: false });
    const { queryByText } = renderAt("/guarded", <RoleGuard allowed={["ADMIN"]} />);
    expect(queryByText("login-page")).toBeInTheDocument();
  });
});

describe("AuthRedirect (children, не layout)", () => {
  it("shows children for anonymous user", () => {
    useAuthStore.setState({ user: null, isLoading: false });
    const { queryByText } = renderAt("/auth-redirect", <span data-x="" />);
    expect(queryByText("guarded-content")).toBeInTheDocument();
  });

  it("sends authenticated user to the role dashboard", () => {
    useAuthStore.setState({ user: user("MANAGER"), isLoading: false });
    const { queryByText } = renderAt("/auth-redirect", <span data-x="" />);
    expect(queryByText("manager-page")).toBeInTheDocument();
    expect(queryByText("guarded-content")).not.toBeInTheDocument();
  });

  it("sends CUSTOMER_INDIVIDUAL to /cabinet", () => {
    useAuthStore.setState({ user: user("CUSTOMER_INDIVIDUAL"), isLoading: false });
    const { queryByText } = renderAt("/auth-redirect", <span data-x="" />);
    expect(queryByText("cabinet-page")).toBeInTheDocument();
  });
});

describe("AdminIndexRedirect", () => {
  it("sends ADMIN to /admin/users", () => {
    useAuthStore.setState({ user: user("ADMIN"), isLoading: false });
    const { queryByText } = renderAt("/guarded", <AdminIndexRedirect />);
    expect(queryByText("admin-users")).toBeInTheDocument();
  });

  it("sends ACCOUNTANT to /admin/dashboard (fixed by S12 audit)", () => {
    useAuthStore.setState({ user: user("ACCOUNTANT"), isLoading: false });
    const { queryByText } = renderAt("/guarded", <AdminIndexRedirect />);
    expect(queryByText("admin-dashboard")).toBeInTheDocument();
  });

  it("redirects anonymous user to /login", () => {
    useAuthStore.setState({ user: null, isLoading: false });
    const { queryByText } = renderAt("/guarded", <AdminIndexRedirect />);
    expect(queryByText("login-page")).toBeInTheDocument();
  });
});
