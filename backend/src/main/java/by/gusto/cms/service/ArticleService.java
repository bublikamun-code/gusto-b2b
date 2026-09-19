package by.gusto.cms.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.User;
import by.gusto.cms.entity.ArticleEntity;
import by.gusto.cms.entity.ArticleEntity.Status;
import by.gusto.cms.repository.ArticleRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CMS-статьи (S37): CRUD в админке (ADMIN), публикация делает страницу видимой.
 * Slug — идентификатор публичной страницы (about/delivery/...).
 */
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final AuditService auditService;

    /** Публично: опубликованная статья по slug. */
    @Transactional(readOnly = true)
    public ArticleEntity publishedBySlug(String slug) {
        return articleRepository.findBySlugAndStatus(slug, Status.PUBLISHED)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Страница не найдена"));
    }

    @Transactional(readOnly = true)
    public List<ArticleEntity> publishedAll() {
        return articleRepository.findAllByStatusOrderByPublishedAtDesc(Status.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<ArticleEntity> all() {
        return articleRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional
    public ArticleEntity create(String slug, String title, String body, User actor) {
        if (articleRepository.findBySlugAndStatus(slug, Status.PUBLISHED).isPresent()
                || articleRepository.findAll().stream().anyMatch(a -> a.getSlug().equals(slug))) {
            throw new GustoException(ErrorCode.CONFLICT, "Статья с таким slug уже существует");
        }
        ArticleEntity article = articleRepository.save(ArticleEntity.builder()
                .slug(slug)
                .title(title)
                .body(body)
                .createdBy(actor.getId())
                .build());
        auditService.append(actor.getId(), "ARTICLE_CREATE", "article", article.getId(),
                null, Map.of("slug", slug));
        return article;
    }

    @Transactional
    public ArticleEntity update(UUID id, String title, String body, User actor) {
        ArticleEntity article = articleRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Статья не найдена"));
        if (title != null) article.setTitle(title);
        if (body != null) article.setBody(body);
        article = articleRepository.save(article);
        auditService.append(actor.getId(), "ARTICLE_UPDATE", "article", id,
                null, Map.of("slug", article.getSlug()));
        return article;
    }

    @Transactional
    public ArticleEntity publish(UUID id, User actor) {
        ArticleEntity article = articleRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Статья не найдена"));
        if (article.getStatus() == Status.PUBLISHED) {
            throw new GustoException(ErrorCode.CONFLICT, "Статья уже опубликована");
        }
        Status before = article.getStatus();
        article.setStatus(Status.PUBLISHED);
        article.setPublishedAt(Instant.now());
        article = articleRepository.save(article);
        auditService.append(actor.getId(), "ARTICLE_PUBLISH", "article", id,
                Map.of("status", before.name()), Map.of("status", Status.PUBLISHED.name()));
        return article;
    }

    @Transactional
    public ArticleEntity archive(UUID id, User actor) {
        ArticleEntity article = articleRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Статья не найдена"));
        article.setStatus(Status.ARCHIVED);
        article = articleRepository.save(article);
        auditService.append(actor.getId(), "ARTICLE_ARCHIVE", "article", id,
                null, Map.of("status", Status.ARCHIVED.name()));
        return article;
    }
}
