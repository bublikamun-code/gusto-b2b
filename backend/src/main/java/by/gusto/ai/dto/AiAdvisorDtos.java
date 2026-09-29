package by.gusto.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Контракт ИИ-советника (S45). Наружу отдаём только то, что уже есть в каталоге:
 * ингредиенты рецепта enrich-ятся данными товара, а ответы модели проходят через
 * allowlist SKU — модель не может сослаться на то, чего магазин не продаёт.
 */
public final class AiAdvisorDtos {

    private AiAdvisorDtos() {
    }

    /** Реплика пользователя. История приходит с клиента и режется по historyLimit. */
    public record ChatRequest(
            @NotBlank(message = "Введите вопрос")
            @Size(max = 1000, message = "Слишком длинный вопрос")
            String message,

            @Size(max = 10, message = "Слишком длинная история диалога")
            List<ChatTurn> history) {
    }

    /**
     * @param role только user/assistant — значение из тела запроса нормализуется,
     *             иначе в промпт модели можно подставить служебную роль.
     */
    public record ChatTurn(String role, String content) {
    }

    public record ChatResponse(
            String reply,
            /** true — ответ сгенерирован моделью, false — детерминированный советник. */
            boolean fromModel,
            List<RecipeSummary> recipes,
            List<String> suggestedQuestions) {
    }

    public record RecipeSummary(
            String slug,
            String title,
            String summary,
            Integer cookingMinutes,
            String difficulty,
            List<String> tags,
            List<RecipeProduct> products) {
    }

    /** Ингредиент рецепта, обогащённый данными товара каталога. */
    public record RecipeProduct(
            String sku,
            String name,
            String quantity,
            String unit,
            String retailPrice,
            String stockStatus,
            String productUrl) {
    }
}
