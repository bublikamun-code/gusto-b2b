import { Outlet } from "react-router-dom";
import { PublicHeader } from "./PublicHeader";
import { PublicFooter } from "./PublicFooter";
import styles from "./PublicLayout.module.scss";

export function PublicLayout() {
  return (
    <div className={styles.layout}>
      <PublicHeader />
      <main className={styles.main}>
        <Outlet />
      </main>
      <PublicFooter />
    </div>
  );
}
