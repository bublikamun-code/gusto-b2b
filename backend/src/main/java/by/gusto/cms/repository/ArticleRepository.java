package by.gusto.cms.repository;

import by.gusto.cms.entity.ArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<ArticleEntity, UUID> {

    Optional<ArticleEntity> findBySlugAndStatus(String slug, ArticleEntity.Status status);

    List<ArticleEntity> findAllByStatusOrderByPublishedAtDesc(ArticleEntity.Status status);

    List<ArticleEntity> findAllByOrderByUpdatedAtDesc();
}
