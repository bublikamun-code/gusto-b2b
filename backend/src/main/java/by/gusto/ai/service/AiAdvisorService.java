package by.gusto.ai.service;

import by.gusto.ai.config.AiProperties;
import by.gusto.ai.dto.AiAdvisorDtos.ChatRequest;
import by.gusto.ai.dto.AiAdvisorDtos.ChatResponse;
import by.gusto.ai.dto.AiAdvisorDtos.ChatTurn;
import by.gusto.ai.dto.AiAdvisorDtos.RecipeProduct;
import by.gusto.ai.dto.AiAdvisorDtos.RecipeSummary;
import by.gusto.ai.entity.Recipe;
import by.gusto.ai.repository.RecipeRepository;
import by.gusto.catalog.dto.CatalogProductResponse;
import by.gusto.catalog.service.CatalogService;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.dto.StockStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ИИ-советник лендинга (S45): диалог и блок рецептов, собранных из того, что
 * реально продаётся.
 *
 * Два режима у блока рецептов и у чата:
 * — рецепты подбираются детерминированно (теги + наличие SKU), поэтому блок
 *   одинаков при любом состоянии внешнего API и не выдумывает состав;
 * — текст ответа даёт модель, но ей в промпт уходит срез каталога, и ответ
 *   клиенту сопровождается только теми рецептами, чьи SKU есть в ассортименте.
 *
 * Без ключа модели (или при её недоступности) чат отвечает сам — витрина не
 * остаётся пустой на стенде, где ключа нет.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdvisorService {

    /** Префикс ключей лимитера в Redis; используется и в интеграционных тестах. */
    public static final String RATE_KEY = "rate:ai-advisor";
    private static final int DEFAULT_RECIPE_LIMIT = 6;
    private static final int MAX_RECIPE_LIMIT = 12;
    private static final int CATALOG_CONTEXT_LIMIT = 60;

    private static final List<String> SUGGESTED_QUESTIONS = List.of(
            "Что приготовить на гриле?",
            "Что быстро сделать на завтрак?",
            "Посоветуй блюдо из курицы",
            "Что взять к пиву",
            "Чем заменить свинину в котлетах",
            "Какие позиции есть в наличии сейчас?");

    private final RecipeRepository recipeRepository;
    private final RecipeContentParser parser;
    private final CatalogService catalogService;
    private final AiChatClient chatClient;
    private final AiProperties properties;
    private final StringRedisTemplate redisTemplate;

    // --- блок рекомендованных рецептов -------------------------------------------

    @Transactional(readOnly = true)
    public List<RecipeSummary> recommendRecipes(Integer limit) {
        int max = limit == null || limit <= 0 ? DEFAULT_RECIPE_LIMIT : Math.min(limit, MAX_RECIPE_LIMIT);
        List<Recipe> recipes = recipeRepository.findAllByActiveTrueOrderBySortOrderAsc();
        Map<String, CatalogProductResponse> products = catalogBySku(allSkus(recipes));

        return recipes.stream()
                .map(recipe -> new Scored(recipe, productsOf(recipe, products)))
                .filter(scored -> !scored.products().isEmpty())
                // сперва то, что можно приготовить сегодня, потом — редакционный порядок
                .sorted(Comparator.comparingInt(Scored::inStock).reversed()
                        .thenComparingInt(scored -> scored.recipe().getSortOrder()))
                .limit(max)
                .map(scored -> toSummary(scored.recipe(), scored.products()))
                .toList();
    }

    // --- диалог --------------------------------------------------------------------

    /**
     * Диалог. Специально БЕЗ @Transactional: чтения ниже живут в своих коротких
     * транзакциях (репозиторий и CatalogService), а вызов модели не должен
     * удерживать соединение пула на время HTTP к LLM (до таймаута).
     */
    public ChatResponse chat(ChatRequest request, String clientIp) {
        enforceRateLimit(clientIp);

        String message = request.message().trim();
        List<Recipe> recipes = recipeRepository.findAllByActiveTrueOrderBySortOrderAsc();
        Map<String, CatalogProductResponse> products = catalogBySku(allSkus(recipes));

        List<Scored> matched = matchByMessage(recipes, message, products, 3);
        // без совпадений показываем то, что готово к отправке — пустой блок хуже
        List<Scored> shown = matched.isEmpty() ? bestAvailable(recipes, products, 2) : matched;

        String reply = chatClient.complete(systemPrompt(products), history(request.history()), message);
        boolean fromModel = reply != null;
        return new ChatResponse(
                fromModel ? reply : fallbackReply(shown),
                fromModel,
                shown.stream().map(scored -> toSummary(scored.recipe(), scored.products())).toList(),
                SUGGESTED_QUESTIONS);
    }

    /** Каталог в промпт: модель советует только тем, что есть в ассортименте. */
    private String systemPrompt(Map<String, CatalogProductResponse> products) {
        StringBuilder catalog = new StringBuilder();
        products.values().stream()
                .limit(CATALOG_CONTEXT_LIMIT)
                .forEach(product -> catalog.append("- ")
                        .append(product.getName())
                        .append(" | sku: ").append(product.getSku())
                        .append(" | категория: ")
                        .append(product.getCategory() == null ? "—" : product.getCategory().getName())
                        .append(" | цена: ")
                        .append(product.getRetailPrice() == null ? "по запросу" : product.getRetailPrice() + " руб./" + product.getUnit())
                        .append(" | наличие: ")
                        .append(product.getStockStatus() == StockStatus.IN_STOCK ? "в наличии" : "под заказ")
                        .append('\n'));

        return """
                Ты — ИИ-советник мясного гастронома «Густо». Отвечаешь на русском, кратко и
                по делу, для покупателя-юрлица или шеф-повара: 2–4 предложения.

                ЖЁСТКИЕ ПРАВИЛА:
                1. Рекомендуй только позиции из списка ассортимента ниже. Ничего другого
                   в магазине нет — не упоминай свинину, птицу, сыры или специи, которых в списке нет.
                2. Цены и наличие бери из списка. Не выдумывай скидки, бренды и сроки.
                3. Если позиции нет в наличии, скажи, что она под заказ, и предложи замену из списка.
                4. Не повторяй вопрос клиента. Не задавай больше одного уточняющего вопроса.
                5. Без разметки: ответ — обычный текст, список оформляй дефисом.

                АССОРТИМЕНТ:
                """ + catalog;
    }

    /**
     * Ответ без модели: собирается из подходящих рецептов, поэтому тоже остаётся
     * правдой о составе и ценах.
     */
    private String fallbackReply(List<Scored> shown) {
        if (shown.isEmpty()) {
            return "Под этот запрос подходящих рецептов пока нет. Посмотрите каталог — подскажу,"
                    + " что можно приготовить из того, что есть.";
        }
        StringBuilder reply = new StringBuilder("Под ваш запрос подходят рецепты из нашего ассортимента:\n");
        for (Scored scored : shown) {
            Recipe recipe = scored.recipe();
            reply.append("\n• ").append(recipe.getTitle()).append(" — ");
            reply.append(scored.products().stream()
                    .map(product -> {
                        String quantity = quantityOf(recipe, product.sku());
                        return quantity.isEmpty() ? product.name() : product.name() + " (" + quantity + ")";
                    })
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
            if (recipe.getCookingMinutes() != null) {
                reply.append(". ").append(recipe.getCookingMinutes()).append(" мин");
            }
            reply.append('.');
        }
        reply.append("\n\nВсе ингредиенты есть в магазине — можно сразу заказать.");
        return reply.toString();
    }

    // --- подбор рецептов ------------------------------------------------------------

    private List<Scored> matchByMessage(List<Recipe> recipes, String message,
                                        Map<String, CatalogProductResponse> products, int limit) {
        String normalized = message.toLowerCase(Locale.ROOT);
        Set<String> keywords = RecipeContentParser.keywords(normalized);

        return recipes.stream()
                .map(recipe -> {
                    List<RecipeProduct> available = productsOf(recipe, products);
                    return new Scored(recipe, available, scoreByMessage(recipe, normalized, keywords, available));
                })
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparingInt(scored -> scored.recipe().getSortOrder()))
                .limit(limit)
                .toList();
    }

    /** Совпадение по тегам весит больше совпадения в тексте; наличие — как бонус. */
    private int scoreByMessage(Recipe recipe, String normalizedMessage, Set<String> keywords,
                               List<RecipeProduct> available) {
        // рецепт, где из ингредиентов ничего не продаётся, не показываем
        if (available.isEmpty()) {
            return 0;
        }
        int score = inStockCount(available);
        for (String tag : parser.tags(recipe)) {
            if (RecipeContentParser.matchesTag(tag, normalizedMessage)) {
                score += 3;
            }
        }
        String haystack = (recipe.getTitle() + " " + recipe.getSummary()).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                score += 2;
            }
        }
        return score;
    }

    private List<Scored> bestAvailable(List<Recipe> recipes, Map<String, CatalogProductResponse> products, int limit) {
        return recipes.stream()
                .map(recipe -> new Scored(recipe, productsOf(recipe, products)))
                .filter(scored -> !scored.products().isEmpty())
                .sorted(Comparator.comparingInt(Scored::inStock).reversed()
                        .thenComparingInt(scored -> scored.recipe().getSortOrder()))
                .limit(limit)
                .toList();
    }

    private int inStockCount(List<RecipeProduct> products) {
        return (int) products.stream().filter(p -> StockStatus.IN_STOCK.name().equals(p.stockStatus())).count();
    }

    private List<RecipeProduct> productsOf(Recipe recipe, Map<String, CatalogProductResponse> products) {
        List<RecipeProduct> result = new ArrayList<>();
        for (RecipeContentParser.Ingredient ingredient : parser.ingredients(recipe)) {
            CatalogProductResponse product = products.get(ingredient.sku());
            if (product == null) {
                // SKU удалён из ассортимента — молча выкидываем ингредиент,
                // чтобы в рецепте не осталось ссылки на то, чего не продают
                continue;
            }
            result.add(new RecipeProduct(
                    product.getSku(),
                    product.getName(),
                    ingredient.quantity(),
                    product.getUnit(),
                    product.getRetailPrice() == null ? null : product.getRetailPrice().toPlainString(),
                    product.getStockStatus().name(),
                    "/products/" + product.getSku()));
        }
        return result;
    }

    private String quantityOf(Recipe recipe, String sku) {
        return parser.ingredients(recipe).stream()
                .filter(ingredient -> sku.equals(ingredient.sku()))
                .map(RecipeContentParser.Ingredient::quantity)
                .filter(quantity -> quantity != null && !quantity.isBlank())
                .findFirst()
                .orElse("");
    }

    private RecipeSummary toSummary(Recipe recipe, List<RecipeProduct> products) {
        return new RecipeSummary(
                recipe.getSlug(),
                recipe.getTitle(),
                recipe.getSummary(),
                recipe.getCookingMinutes(),
                recipe.getDifficulty(),
                parser.tags(recipe),
                products);
    }

    // --- каталог ---------------------------------------------------------------------

    private Set<String> allSkus(List<Recipe> recipes) {
        Set<String> skus = new LinkedHashSet<>();
        for (Recipe recipe : recipes) {
            for (RecipeContentParser.Ingredient ingredient : parser.ingredients(recipe)) {
                if (ingredient.sku() != null && !ingredient.sku().isBlank()) {
                    skus.add(ingredient.sku());
                }
            }
        }
        return skus;
    }

    private Map<String, CatalogProductResponse> catalogBySku(Set<String> skus) {
        if (skus.isEmpty()) {
            return Map.of();
        }
        Map<String, CatalogProductResponse> bySku = new LinkedHashMap<>();
        for (CatalogProductResponse product : catalogService.getProductsBySku(List.copyOf(skus))) {
            bySku.put(product.getSku(), product);
        }
        return bySku;
    }

    // --- история диалога ----------------------------------------------------------------

    /** Роль из тела запроса не доверяем: пропускаем только user/assistant. */
    private List<ChatTurn> history(List<ChatTurn> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> normalized = new ArrayList<>();
        for (ChatTurn turn : raw) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            String role = turn.role() == null ? "" : turn.role().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) {
                continue;
            }
            normalized.add(new ChatTurn(role, turn.content().substring(0,
                    Math.min(turn.content().length(), properties.getMaxMessageLength()))));
        }
        return normalized.subList(Math.max(0, normalized.size() - properties.getHistoryLimit()), normalized.size());
    }

    // --- rate limit -----------------------------------------------------------------------

    /**
     * Публичный чат — прямой способ потратить деньги на чужой API: лимит по IP.
     * Счётчик инкрементится до проверки (проверка и запись атомарны), TTL
     * ставится по первому инкременту и больше не перезаписывается. Redis
     * недоступен — вопрос пропускаем: лимитер не должен ронять витрину.
     */
    private void enforceRateLimit(String clientIp) {
        try {
            Long current = redisTemplate.opsForValue().increment(rateKey(clientIp));
            if (current == null) {
                return;
            }
            if (current == 1) {
                redisTemplate.expire(rateKey(clientIp), Duration.ofHours(1));
            }
            if (current > properties.getRequestsPerHour()) {
                throw new GustoException(ErrorCode.RATE_LIMITED,
                        "Слишком много вопросов. Попробуйте через час.");
            }
        } catch (GustoException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI_ADVISOR rate limit недоступен, вопрос пропущен без лимита: {}", e.getMessage());
        }
    }

    private String rateKey(String clientIp) {
        return RATE_KEY + ":" + (clientIp == null ? "unknown" : clientIp);
    }

    private record Scored(Recipe recipe, List<RecipeProduct> products, int score) {

        Scored(Recipe recipe, List<RecipeProduct> products) {
            this(recipe, products, 0);
        }

        int inStock() {
            return (int) products.stream()
                    .filter(product -> StockStatus.IN_STOCK.name().equals(product.stockStatus()))
                    .count();
        }
    }
}
