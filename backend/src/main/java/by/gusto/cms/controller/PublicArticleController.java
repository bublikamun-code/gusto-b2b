package by.gusto.cms.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.cms.entity.ArticleEntity;
import by.gusto.cms.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Публичные CMS-страницы (S37): только PUBLISHED.
 *
 * <p>Ответ оборачивается в конверт ApiResponse: фронт читает {@code envelope.data}
 * ({@code api/client.ts}), и «сырые» Map/List давали {@code undefined} — страницы
 * «О нас» и «Доставка» рендерились одним заголовком без тела, а правки лендинга из
 * админки не доходили до витрины (аудит 2026-09-30, P1-12).
 */
@RestController
@RequestMapping("/api/v1/cms")
@RequiredArgsConstructor
public class PublicArticleController {

    private final ArticleService articleService;

    @GetMapping("/pages/{slug}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> page(@PathVariable String slug) {
        ArticleEntity article = articleService.publishedBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "slug", article.getSlug(),
                "title", article.getTitle(),
                "body", article.getBody(),
                "publishedAt", article.getPublishedAt())));
    }

    @GetMapping("/pages")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pages() {
        return ResponseEntity.ok(ApiResponse.success(articleService.publishedAll().stream()
                .map(a -> Map.<String, Object>of(
                        "slug", a.getSlug(),
                        "title", a.getTitle(),
                        "publishedAt", a.getPublishedAt()))
                .toList()));
    }
}
