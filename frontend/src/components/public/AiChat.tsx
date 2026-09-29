import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { askAiAdvisor, type ChatTurn, type Recipe } from "../../api/aiAdvisor";
import { Button } from "../ui";
import { RecipeCard } from "./RecipeCard";
import styles from "./AiChat.module.scss";

const MAX_INPUT = 1000;
/** Столько последних реплик уходит на сервер — столько же он принимает в history. */
const HISTORY_LIMIT = 8;

/** Пока сервер не ответил ни разу, подсказки для старта берём из константы. */
const DEFAULT_QUESTIONS = [
  "Что приготовить на гриле?",
  "Что быстро сделать на завтрак?",
  "Посоветуй блюдо из курицы",
  "Что взять к пиву",
];

const GREETING =
  "Спросите, что приготовить из нашего мяса, птицы и колбас — отвечаю по тем позициям, что реально есть в продаже.";

interface Answer {
  id: number;
  text: string;
  fromModel: boolean;
  recipes: Recipe[];
}

function errorMessage(error: unknown): string {
  const code = (error as { code?: string } | null)?.code;
  if (code === "RATE_LIMITED") {
    return "Лимит вопросов на час исчерпан. Попробуйте позже — рецепты справа всегда доступны.";
  }
  if (code === "NOT_FOUND") {
    return "Советник сейчас выключен. Посмотрите рецепты справа — они из актуального ассортимента.";
  }
  return "Не получилось получить ответ. Попробуйте ещё раз или посмотрите рецепты.";
}

export function AiChat() {
  const [input, setInput] = useState("");
  const [history, setHistory] = useState<ChatTurn[]>([]);
  const [answers, setAnswers] = useState<Answer[]>([]);
  const [questions, setQuestions] = useState<string[]>(DEFAULT_QUESTIONS);
  const [asked, setAsked] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const nextId = useRef(0);
  const logRef = useRef<HTMLDivElement>(null);

  const mutation = useMutation({
    mutationFn: askAiAdvisor,
    onSuccess: (data, variables) => {
      setAnswers((prev) => [
        ...prev,
        {
          id: nextId.current++,
          text: data.reply,
          fromModel: data.fromModel,
          recipes: data.recipes ?? [],
        },
      ]);
      // обе реплики дописываем одним куском, иначе в историю уедут
      // две ревизии состояния и порядок user/assistant может разъехаться
      setHistory((prev) =>
        [
          ...prev,
          { role: "user" as const, content: variables.message },
          { role: "assistant" as const, content: data.reply },
        ].slice(-HISTORY_LIMIT),
      );
      if (data.suggestedQuestions?.length) {
        setQuestions(data.suggestedQuestions);
      }
      setFailed(null);
    },
    onError: (error) => setFailed(errorMessage(error)),
  });

  // переносим к последнему ответу — иначе длинный диалог уезжает за экран
  useEffect(() => {
    const log = logRef.current;
    if (log) log.scrollTop = log.scrollHeight;
  }, [answers, mutation.isPending]);

  const send = (text: string) => {
    const question = text.trim().slice(0, MAX_INPUT);
    if (!question || mutation.isPending) return;

    setAsked(true);
    setFailed(null);
    setInput("");
    mutation.mutate({ message: question, history });
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    send(input);
  };

  return (
    <div className={styles.chat}>
      <div className={styles.log} ref={logRef} role="log" aria-live="polite" aria-label="Диалог с ИИ-советником">
        <div className={styles.bubble}>
          <p className={styles.bubbleText}>{GREETING}</p>
        </div>

        {answers.map((answer) => (
          <div key={answer.id} className={styles.answer}>
            <div className={styles.bubble}>
              <p className={styles.bubbleText}>{answer.text}</p>
            </div>
            {answer.recipes.length > 0 && (
              <div className={styles.answerRecipes}>
                {answer.recipes.map((recipe) => (
                  <RecipeCard key={recipe.slug} recipe={recipe} compact />
                ))}
              </div>
            )}
            {!answer.fromModel && (
              <p className={styles.note}>
                Ответ собран из каталога рецептов — языковая модель сейчас не подключена.
              </p>
            )}
          </div>
        ))}

        {mutation.isPending && (
          <div className={styles.bubble} data-testid="advisor-pending">
            <p className={styles.bubbleText}>Подбираю рецепт…</p>
          </div>
        )}

        {failed && (
          <p className={styles.error} role="alert">
            {failed}
          </p>
        )}
      </div>

      {!asked && (
        <div className={styles.suggestions}>
          {questions.map((question) => (
            <button
              key={question}
              type="button"
              className={styles.suggestion}
              onClick={() => send(question)}
              disabled={mutation.isPending}
            >
              {question}
            </button>
          ))}
        </div>
      )}

      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.srOnly} htmlFor="ai-advisor-input">
          Ваш вопрос
        </label>
        <textarea
          id="ai-advisor-input"
          className={styles.input}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send(input);
            }
          }}
          placeholder="Например: что приготовить на гриле?"
          rows={2}
          maxLength={MAX_INPUT}
          disabled={mutation.isPending}
        />
        <Button
          type="submit"
          variant="accent"
          size="md"
          loading={mutation.isPending}
          disabled={!input.trim()}
        >
          Спросить
        </Button>
      </form>
    </div>
  );
}
