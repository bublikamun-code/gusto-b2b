package by.gusto.cms.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.cms.entity.ArticleEntity;
import by.gusto.cms.service.ArticleService;
import by.gusto.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Админка статей (S37): draft/published, только ADMIN (матрица 2.1). */
@RestController
@RequestMapping("/api/v1/admin/cms/articles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminArticleController {

    private final ArticleService articleService;
    private final AuthContext authContext;

    @Data
    public static class CreateRequest {
        @NotBlank
        private String slug;
        @NotBlank
        private String title;
        @NotBlank
        private String body;
    }

    @Data
    public static class UpdateRequest {
        private String title;
        private String body;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArticleItem>>> list() {
        List<ArticleItem> items = articleService.all().stream().map(this::toItem).toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ArticleItem>> create(@jakarta.validation.Valid @RequestBody CreateRequest request) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                toItem(articleService.create(request.getSlug(), request.getTitle(), request.getBody(), actor))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleItem>> update(
            @PathVariable UUID id, @RequestBody UpdateRequest request) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                toItem(articleService.update(id, request.getTitle(), request.getBody(), actor))));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ArticleItem>> publish(@PathVariable UUID id) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(toItem(articleService.publish(id, actor))));
    }

    /** Архивирование (вместо удаления — словарь 2.8). */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleItem>> archive(@PathVariable UUID id) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(toItem(articleService.archive(id, actor))));
    }

    private ArticleItem toItem(ArticleEntity a) {
        return new ArticleItem(a.getId(), a.getSlug(), a.getTitle(), a.getBody(),
                a.getStatus().name(), a.getPublishedAt());
    }

    public record ArticleItem(UUID id, String slug, String title, String body,
                              String status, java.time.Instant publishedAt) {
    }
}
