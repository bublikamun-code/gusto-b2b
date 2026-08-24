import { useEffect, useState } from "react";

/**
 * Заглушка S03: проверка связки frontend → backend (приёмка S04).
 * Реальные страницы — с S06/S10 по чек-листу.
 */
export default function App() {
  const [apiStatus, setApiStatus] = useState<string>("проверяем…");

  useEffect(() => {
    fetch("/healthz")
      .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
      .then((body) => setApiStatus(body?.data?.status ?? "UP"))
      .catch(() => setApiStatus("недоступен"));
  }, []);

  return (
    <div className="page">
      <header className="header">
        <span className="logo">ГУСТО</span>
        <span className="header__sub">мясной гастроном</span>
      </header>

      <main className="card">
        <h1>Скелет проекта поднят</h1>
        <p>
          Backend: <strong>{apiStatus}</strong>
        </p>
        <p className="card__note">
          Это заглушка S03. Дизайн-система и страницы — по чек-листу (S06+).
        </p>
      </main>

      <footer className="footer">gustomeat.by · B2B-портал</footer>
    </div>
  );
}
