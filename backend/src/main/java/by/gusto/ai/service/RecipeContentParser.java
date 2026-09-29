package by.gusto.ai.service;

import by.gusto.ai.entity.Recipe;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Разбор JSON-колонок рецепта (S45). Данные редакционные, но править их будут
 * руками через SQL, поэтому битый JSON не должен ронять витрину: парсер
 * возвращает пустой список и пишет в лог, карточка рецепта остаётся рабочей.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeContentParser {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Ingredient>> INGREDIENTS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public List<String> tags(Recipe recipe) {
        List<String> tags = readList(recipe.getSlug(), "tags", recipe.getTags(), STRING_LIST);
        // теги сравниваются с репликой пользователя — нормализуем один раз здесь
        List<String> normalized = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    public List<Ingredient> ingredients(Recipe recipe) {
        List<Ingredient> parsed = readList(recipe.getSlug(), "ingredients", recipe.getIngredients(), INGREDIENTS);
        List<Ingredient> withSku = new ArrayList<>(parsed.size());
        for (Ingredient ingredient : parsed) {
            if (ingredient != null && ingredient.sku() != null && !ingredient.sku().isBlank()) {
                withSku.add(ingredient);
            }
        }
        return withSku;
    }

    private <T> List<T> readList(String slug, String field, String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> parsed = objectMapper.readValue(json, type);
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            log.warn("AI_ADVISOR рецепт {}: поле {} не разобрано ({})", slug, field, e.getMessage());
            return List.of();
        }
    }

    /** Совпадение тега с репликой: тег входит в текст как отдельное слово. */
    public static boolean matchesTag(String tag, String normalizedMessage) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        int from = 0;
        while (true) {
            int at = normalizedMessage.indexOf(tag, from);
            if (at < 0) {
                return false;
            }
            boolean leftBoundary = at == 0 || !isWordChar(normalizedMessage.charAt(at - 1));
            int end = at + tag.length();
            boolean rightBoundary = end == normalizedMessage.length()
                    || !isWordChar(normalizedMessage.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** Ингредиент рецепта: что показывать в карточке и какой SKU подставить. */
    public record Ingredient(String name, String sku, String quantity) {
    }

    /** Ключевые слова реплики — по ним подбираем рецепты без модели. */
    public static Set<String> keywords(String message) {
        Set<String> words = new LinkedHashSet<>();
        if (message == null) {
            return words;
        }
        for (String word : message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3) {
                words.add(word);
            }
        }
        return words;
    }
}
