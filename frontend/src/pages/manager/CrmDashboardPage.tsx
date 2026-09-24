import { useCallback, useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Card } from "../../components/ui";
import { getDashboard, type CrmDashboard } from "../../api/crm";
import { formatMoney } from "../../lib/format";
import styles from "./CrmDashboardPage.module.scss";

const BORDO = "#7C2D24";
const YELTOK = "#E5A33C";
const KRAFT = "#C9A875";
const GRAPHIT = "#26201C";

/** Дашборд руководителя (S30): KPI + графики Recharts по данным /crm/dashboard. */
export default function CrmDashboardPage() {
  const [dashboard, setDashboard] = useState<CrmDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    getDashboard()
      .then(setDashboard)
      .catch((err) => setError((err as Error).message));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (error) {
    return (
      <div className={styles.page}>
        <h1>Дашборд</h1>
        <Card>{error}</Card>
      </div>
    );
  }
  if (!dashboard) {
    return (
      <div className={styles.page}>
        <h1>Дашборд</h1>
        <p>Загрузка…</p>
      </div>
    );
  }

  const funnel = [
    { name: "Всего лидов", value: dashboard.leadsTotal },
    { name: "Выиграно", value: dashboard.leadsWon },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Дашборд руководителя</h1>
        <span className={styles.period}>
          период: {dashboard.from} — {dashboard.to}
        </span>
      </header>

      <div className={styles.kpis}>
        <Card className={styles.kpi}>
          <span className={styles.kpiLabel}>Выручка (COMPLETED)</span>
          <span className={styles.kpiValue}>{formatMoney(dashboard.revenue)}</span>
        </Card>
        <Card className={styles.kpi}>
          <span className={styles.kpiLabel}>Выполнено заказов</span>
          <span className={styles.kpiValue}>{dashboard.completedOrders}</span>
        </Card>
        <Card className={styles.kpi}>
          <span className={styles.kpiLabel}>Задолженность</span>
          <span className={styles.kpiValue}>{formatMoney(dashboard.debt)}</span>
        </Card>
        <Card className={styles.kpi}>
          <span className={styles.kpiLabel}>Конверсия лидов</span>
          <span className={styles.kpiValue}>{dashboard.leadConversionPercent}%</span>
        </Card>
      </div>

      <div className={styles.charts}>
        <Card className={styles.chart}>
          <h2>Топ товаров</h2>
          <div className={styles.chartBox}>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={dashboard.topProducts}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" hide />
                <YAxis />
                <Tooltip />
                <Bar dataKey="total" name="Сумма, BYN" fill={BORDO} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className={styles.chart}>
          <h2>Топ клиентов</h2>
          <div className={styles.chartBox}>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={dashboard.topCustomers}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" hide />
                <YAxis />
                <Tooltip />
                <Bar dataKey="total" name="Сумма, BYN" fill={YELTOK} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className={styles.chart}>
          <h2>Лиды</h2>
          <div className={styles.chartBox}>
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={funnel}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={90}
                >
                  <Cell fill={KRAFT} />
                  <Cell fill={GRAPHIT} />
                </Pie>
                <Legend />
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>
    </div>
  );
}
