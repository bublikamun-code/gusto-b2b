import { apiRequest } from "./client";
import type { components } from "./schema";

export type Recipe = components["schemas"]["Recipe"];
export type RecipeProduct = components["schemas"]["RecipeProduct"];
export type AiChatResponse = components["schemas"]["AiChatResponse"];

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface ChatInput {
  message: string;
  history: ChatTurn[];
}

/** Блок рекомендованных рецептов лендинга (S45). */
export function listRecipes(limit?: number): Promise<Recipe[]> {
  const query = limit ? `?limit=${limit}` : "";
  return apiRequest<Recipe[]>(`/ai/recipes${query}`);
}

export function askAiAdvisor(input: ChatInput): Promise<AiChatResponse> {
  return apiRequest<AiChatResponse>("/ai/chat", { method: "POST", body: input });
}
