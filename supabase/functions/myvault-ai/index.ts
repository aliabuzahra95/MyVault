const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type FormattingAction = "organise" | "format_note";
type FormattingModel = "fast" | "smart";

type FormattingRequest = {
  action?: FormattingAction;
  model?: FormattingModel;
  title?: string;
  body?: string;
  systemInstruction?: string;
  prompt?: string;
  temperature?: number;
  maxOutputTokens?: number;
};

const formattingActions = new Set<FormattingAction>(["organise", "format_note"]);

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return json({ error: "Use POST." }, 405);
  }

  try {
    const authorization = request.headers.get("authorization") ?? "";
    if (!isAuthenticatedUser(authorization)) {
      return json({ error: "Sign in to MyVault before using ChatGPT note formatting." }, 401);
    }

    const openAiKey = Deno.env.get("OPENAI_API_KEY");
    if (!openAiKey) {
      return json({ error: "OPENAI_API_KEY is not set in Supabase secrets." }, 500);
    }

    const payload = (await request.json()) as FormattingRequest;
    const requestedAction = payload.action ?? "format_note";
    if (!formattingActions.has(requestedAction)) {
      return json({ error: "Only note-formatting actions are supported." }, 400);
    }
    const action = requestedAction as FormattingAction;
    const title = (payload.title ?? "Untitled note").trim() || "Untitled note";
    const body = (payload.body ?? "").trim();
    if (!body) {
      return json({ error: "This note is empty." }, 400);
    }

    const modelKind: FormattingModel = payload.model === "smart" ? "smart" : "fast";
    const model = modelKind === "smart"
      ? Deno.env.get("OPENAI_SMART_MODEL") ?? "gpt-5.5"
      : Deno.env.get("OPENAI_FAST_MODEL") ?? "gpt-5-mini";
    const prompt = typeof payload.prompt === "string" && payload.prompt.trim()
      ? payload.prompt.trim()
      : buildFormattingPrompt({ action, title, body });
    const requestedMaxOutputTokens = typeof payload.maxOutputTokens === "number"
      ? payload.maxOutputTokens
      : maxOutputTokensFor(action, modelKind);

    const openAiRequest: Record<string, unknown> = {
      model,
      instructions: payload.systemInstruction?.trim() || defaultSystemInstruction(action),
      input: prompt,
      max_output_tokens: clampMaxOutputTokens(requestedMaxOutputTokens, modelKind),
    };

    if (isGpt5Family(model)) {
      openAiRequest.reasoning = { effort: reasoningEffortFor(action, modelKind) };
      openAiRequest.text = { verbosity: verbosityFor(action, modelKind) };
    } else {
      openAiRequest.temperature = clampTemperature(payload.temperature, action, modelKind);
    }

    const openAiEndpoint = "https://api.openai.com/v1/responses";
    guardOpenAiRequest({
      endpoint: openAiEndpoint,
      model,
      feature: "NoteFormattingSupabaseFunction",
    });
    const response = await fetch(openAiEndpoint, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openAiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(openAiRequest),
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      return json({ error: data?.error?.message ?? "OpenAI formatting request failed." }, response.status);
    }

    const data = await response.json();
    return json({
      text: extractText(data),
      model,
      modelKind,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown note-formatting function error.";
    return json({ error: message }, 500);
  }
});

function isAuthenticatedUser(authorization: string): boolean {
  const token = authorization.replace(/^Bearer\s+/i, "").trim();
  const payload = decodeJwtPayload(token);
  return payload?.role === "authenticated" && typeof payload?.sub === "string" && payload.sub.length > 0;
}

function decodeJwtPayload(token: string): any | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

function defaultSystemInstruction(action: FormattingAction): string {
  if (action === "format_note") {
    return "Return clean editor-safe HTML only. Preserve the note's wording and meaning.";
  }
  return "Return clean editor-safe HTML only. Organise the note without inventing facts.";
}

function buildFormattingPrompt(input: {
  action: FormattingAction;
  title: string;
  body: string;
}): string {
  const instruction = input.action === "format_note"
    ? "Format the note without changing its meaning or wording."
    : "Intelligently organise and structure the note while preserving its meaning.";
  return `${instruction}

Rules:
- Return simple editor-safe HTML only.
- Do not include markdown, code fences, commentary, or a preface.
- Use clear headings, paragraphs, lists, and blockquotes where appropriate.
- Preserve Arabic, quotations, citations, references, names, numbers, and technical terms.
- Do not add new facts, arguments, examples, advice, or conclusions.

<note>
<title>${input.title}</title>
<body>
${input.body}
</body>
</note>`;
}

function isGpt5Family(model: string): boolean {
  return /^gpt-5(?:\.|-|$)/i.test(model);
}

function temperatureFor(action: FormattingAction, modelKind: FormattingModel): number {
  if (action === "format_note") return modelKind === "smart" ? 0.2 : 0.18;
  return modelKind === "smart" ? 0.2 : 0.18;
}

function clampTemperature(
  requested: number | undefined,
  action: FormattingAction,
  modelKind: FormattingModel,
): number {
  const fallback = temperatureFor(action, modelKind);
  const value = typeof requested === "number" && Number.isFinite(requested) ? requested : fallback;
  return Math.min(Math.max(value, 0), 1);
}

function maxOutputTokensFor(action: FormattingAction, modelKind: FormattingModel): number {
  if (modelKind === "fast") return action === "organise" ? 3600 : 2200;
  return action === "organise" ? 8000 : 4000;
}

function clampMaxOutputTokens(requested: number, modelKind: FormattingModel): number {
  const safeRequested = Number.isFinite(requested) && requested > 0 ? requested : 2200;
  const ceiling = modelKind === "smart" ? 12000 : 4500;
  return Math.min(Math.max(Math.round(safeRequested), 500), ceiling);
}

function reasoningEffortFor(
  action: FormattingAction,
  modelKind: FormattingModel,
): "low" | "medium" | "high" {
  if (modelKind === "smart") return "high";
  return action === "organise" ? "medium" : "low";
}

function verbosityFor(
  action: FormattingAction,
  modelKind: FormattingModel,
): "low" | "medium" | "high" {
  if (modelKind === "smart") return action === "organise" ? "high" : "medium";
  return action === "organise" ? "medium" : "low";
}

function extractText(data: any): string {
  if (typeof data.output_text === "string" && data.output_text.trim()) {
    return data.output_text.trim();
  }
  const parts = data?.output?.flatMap((item: any) => item?.content ?? []) ?? [];
  const text = parts
    .map((part: any) => part?.text ?? "")
    .filter(Boolean)
    .join("\n")
    .trim();
  if (!text) throw new Error("OpenAI did not return formatted text.");
  return text;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function guardOpenAiRequest(request: { endpoint: string; model: string; feature: string }) {
  const endpoint = request.endpoint.toLowerCase();
  const model = request.model.toLowerCase();
  const forbiddenEndpointParts = ["realtime", "transcriptions", "translations"];
  const forbiddenModelParts = ["realtime", "whisper"];
  const endpointHit = forbiddenEndpointParts.find((part) => endpoint.includes(part));
  if (endpointHit) {
    throw new Error(`Blocked forbidden OpenAI endpoint for ${request.feature}: ${endpointHit}`);
  }
  const modelHit = forbiddenModelParts.find((part) => model.includes(part));
  if (modelHit) {
    throw new Error(`Blocked forbidden OpenAI model for ${request.feature}: ${modelHit}`);
  }
  console.info("OpenAI formatting request", {
    endpoint: request.endpoint,
    model: request.model,
    feature: request.feature,
    timestamp: Date.now(),
  });
}
