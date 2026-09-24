import { LinkButton } from "../../components/ui";
import { useAuthStore } from "../../store/authStore";

export default function CabinetDashboardPage() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="page">
      <main className="card">
        <h1>Личный кабинет</h1>
        <p>Пользователь: {user?.fullName ?? user?.email}</p>
        <div className="actions">
          <LinkButton to="/cabinet/orders">Мои заказы</LinkButton>
          {/* Документы — только юрлицам, как в шапке CabinetLayout:
              для физлица маршрут /cabinet/documents редиректит на витрину */}
          {user?.role === "CUSTOMER_LEGAL" && (
            <LinkButton to="/cabinet/documents">Документы</LinkButton>
          )}
          <LinkButton to="/cabinet/catalog">Перейти в каталог</LinkButton>
        </div>
      </main>
    </div>
  );
}
