package by.gusto.ai.repository;

import by.gusto.ai.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    List<Recipe> findAllByActiveTrueOrderBySortOrderAsc();
}
