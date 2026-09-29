import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listRecipes } from "../../api/aiAdvisor";
import { AiChat } from "./AiChat";
import { RecipeCard } from "./RecipeCard";
import styles from "./AiAdvisorSection.module.scss";

const RECIPES_LIMIT = 6;

/**
 * Раздел «ИИ-советник» на лендинге (S45): диалог плюс блок рецептов,
 * собранных из позиций, которые реально продаются.
 */
export function AiAdvisorSection() {
  const { data: recipes = [], isError, isPending } = useQuery({
    queryKey: ["ai", "recipes"],
    queryFn: () => listRecipes(RECIPES_LIMIT),
    staleTime: 5 * 60_000,
  });

  return (
    <section className={styles.section} id="ai-advisor">
      <div className={styles.section__header}>
        <span className={styles.eyebrow}>ИИ-советник</span>
        <h2 className={styles.title}>Что приготовить из нашего мяса</h2>
        <p className={styles.subtitle}>
          Опишите задачу — блюдо, смену, повод. Советник ответит по тем позициям,
          что есть в продаже, и покажет рецепт с нормой закупки.
        </p>
      </div>

      <div className={styles.layout}>
        <div className={styles.chatColumn}>
          <AiChat />
        </div>

        <div className={styles.recipesColumn}>
          <div className={styles.recipesHeader}>
            <h3 className={styles.recipesTitle}>Рекомендуем сейчас</h3>
            <Link to="/catalog" className={styles.recipesLink}>
              Весь каталог →
            </Link>
          </div>

          {isPending && <p className={styles.status}>Загружаем рецепты…</p>}
          {isError && <p className={styles.status}>Не удалось загрузить рецепты.</p>}
          {!isPending && !isError && recipes.length === 0 && (
            <p className={styles.status}>Рецепты скоро появятся.</p>
          )}

          <div className={styles.recipesGrid}>
            {recipes.map((recipe) => (
              <RecipeCard key={recipe.slug} recipe={recipe} />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
