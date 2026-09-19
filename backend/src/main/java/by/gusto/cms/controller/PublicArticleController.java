package by.gusto.cms.controller;

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

/** Публичные CMS-страницы (S37): только PUBLISHED. */
@RestController
@RequestMapping("/api/v1/cms")
@RequiredArgsConstructor
public class PublicArticleController {

    private final ArticleService articleService;
    private final by.gusto.common.settings.SettingsService settingsService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Тексты лендинга (S38): правятся в админке без коммита. */
    @org.springframework.web.bind.annotation.GetMapping("/landing")
    public ResponseEntity<Map<String, Object>> landing() {
        Map<String, Object> landing = new java.util.LinkedHashMap<>();
        landing.put("hero", readSetting("landing.hero", Map.of(
                "title", "Свежая поставка каждое утро",
                "subtitle", "Мясной гастроном ГУСТО")));
        landing.put("delivery", readSetting("landing.delivery", Map.of(
                "title", "Как мы доставляем",
                "steps", java.util.List.of("Заявка", "Свежесть с утра", "Доставка к вам"))));
        return ResponseEntity.ok(landing);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSetting(String key, Map<String, Object> fallback) {
        String json = settingsService.getString(key);
        if (json == null) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    @GetMapping("/pages/{slug}")
    public ResponseEntity<Map<String, Object>> page(@PathVariable String slug) {
        ArticleEntity article = articleService.publishedBySlug(slug);
        return ResponseEntity.ok(Map.of(
                "slug", article.getSlug(),
                "title", article.getTitle(),
                "body", article.getBody(),
                "publishedAt", article.getPublishedAt()));
    }

    @GetMapping("/pages")
    public ResponseEntity<List<Map<String, Object>>> pages() {
        return ResponseEntity.ok(articleService.publishedAll().stream()
                .map(a -> Map.<String, Object>of(
                        "slug", a.getSlug(),
                        "title", a.getTitle(),
                        "publishedAt", a.getPublishedAt()))
                .toList());
    }
}
