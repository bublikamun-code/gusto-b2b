import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AiAdvisorSection } from "./AiAdvisorSection";
import type { Recipe } from "../../api/aiAdvisor";

const ribeye: Recipe = {
  slug: "steyk-ribay-na-grile",
  title: "Стейк рибай на гриле",
  summary: "Классическая прожарка рибайа.",
  cookingMinutes: 25,
  difficulty: "Средняя",
  tags: ["гриль"],
  products: [
    {
      sku: "steyk-ribay",
      name: "Стейк рибай",
      quantity: "250 г на порцию",
      unit: "кг",
      retailPrice: "42.50",
      stockStatus: "IN_STOCK",
      productUrl: "/products/steyk-ribay",
    },
  ],
};

function renderSection() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AiAdvisorSection />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function mockFetch(recipes: Recipe[], chat?: unknown) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((input) => {
    const url = String(input);
    const body = url.includes("/ai/chat")
      ? (chat ?? {
          data: {
            reply: "Возьмите стейк рибай — он есть в наличии.",
            fromModel: false,
            recipes: [ribeye],
            suggestedQuestions: ["Что приготовить на гриле?"],
          },
        })
      : { data: recipes, meta: {} };
    return Promise.resolve(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
  });
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("AiAdvisorSection", () => {
  it("показывает рецепты с товарами каталога", async () => {
    mockFetch([ribeye]);
    renderSection();

    expect(await screen.findByText("Стейк рибай на гриле")).toBeInTheDocument();
    expect(screen.getByText("Стейк рибай")).toBeInTheDocument();
    // товар из рецепта ведёт на витрину, а не на строку поиска
    expect(screen.getByRole("link", { name: "Стейк рибай" })).toHaveAttribute(
      "href",
      "/products/steyk-ribay",
    );
  });

  it("устойчив к ошибке загрузки рецептов", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ error: { code: "INTERNAL", message: "упало" } }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }),
    );
    renderSection();

    expect(await screen.findByText("Не удалось загрузить рецепты.")).toBeInTheDocument();
  });

  it("отправляет вопрос и показывает ответ с рецептами", async () => {
    mockFetch([], undefined);
    renderSection();

    const input = await screen.findByLabelText("Ваш вопрос");
    await userEvent.type(input, "Что приготовить на гриле?");
    await userEvent.click(screen.getByRole("button", { name: "Спросить" }));

    expect(await screen.findByText(/Возьмите стейк рибай/)).toBeInTheDocument();
    // ответ без модели помечен честно — иначе посетитель примет его за живой ИИ
    expect(screen.getByText(/собран из каталога рецептов/)).toBeInTheDocument();

    const chatCall = (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.find(
      (call) => String(call[0]).includes("/ai/chat"),
    );
    expect(JSON.parse(chatCall![1].body as string).message).toBe("Что приготовить на гриле?");
  });

  it("объясняет лимит запросов вместо сырой ошибки", async () => {
    mockFetch([], { error: { code: "RATE_LIMITED", message: "лимит" } });
    renderSection();

    const input = await screen.findByLabelText("Ваш вопрос");
    await userEvent.type(input, "Что быстро сделать?");
    await userEvent.click(screen.getByRole("button", { name: "Спросить" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/Лимит вопросов на час/);
  });

  it("помечает позиции под заказ", async () => {
    mockFetch([
      {
        ...ribeye,
        products: [{ ...ribeye.products![0], stockStatus: "PREORDER" }],
      },
    ]);
    renderSection();

    await waitFor(() => expect(screen.getByText(/под заказ/)).toBeInTheDocument());
  });
});
