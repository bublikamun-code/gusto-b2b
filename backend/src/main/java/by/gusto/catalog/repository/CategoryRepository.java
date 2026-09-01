package by.gusto.catalog.repository;

import by.gusto.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByActiveTrueOrderBySortAsc();

    List<Category> findAllByParentIdIsNullAndActiveTrueOrderBySortAsc();

    List<Category> findAllByParentIdAndActiveTrueOrderBySortAsc(UUID parentId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
