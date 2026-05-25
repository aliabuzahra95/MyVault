const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type AiAction = "quick_summary" | "deep_summary" | "study_tutor" | "deep_analysis" | "ask" | "explain_note" | "general_ask" | "organise" | "format_note";
type AiModel = "fast" | "smart";

type AiRequest = {
  action?: AiAction;
  model?: AiModel;
  title?: string;
  body?: string;
  question?: string;
  systemInstruction?: string;
  prompt?: string;
  temperature?: number;
  maxOutputTokens?: number;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return json({ error: "Use POST." }, 405);
  }

  try {
    const auth = request.headers.get("authorization") ?? "";
    if (!isAuthenticatedUser(auth)) {
      return json({ error: "Sign in to My Vault before using ChatGPT." }, 401);
    }

    const openAiKey = Deno.env.get("OPENAI_API_KEY");
    if (!openAiKey) {
      return json({ error: "OPENAI_API_KEY is not set in Supabase secrets." }, 500);
    }

    const payload = (await request.json()) as AiRequest;
    const action = payload.action ?? "ask";
    const title = (payload.title ?? "Untitled note").trim() || "Untitled note";
    const body = (payload.body ?? "").trim();
    const question = (payload.question ?? "").trim();
    const modelKind = payload.model === "smart" ? "smart" : "fast";
    const model = modelKind === "smart"
      ? Deno.env.get("OPENAI_SMART_MODEL") ?? "gpt-5.5"
      : Deno.env.get("OPENAI_FAST_MODEL") ?? "gpt-5-mini";

    if (action !== "general_ask" && !body && !title) {
      return json({ error: "This note is empty." }, 400);
    }
    if ((action === "ask" || action === "general_ask") && !question) {
      return json({ error: "Type a question first." }, 400);
    }

    const prompt = typeof payload.prompt === "string" && payload.prompt.trim()
      ? payload.prompt.trim()
      : buildPrompt({ action, title, body, question });

    const requestedMaxOutputTokens = typeof payload.maxOutputTokens === "number"
      ? payload.maxOutputTokens
      : maxOutputTokensFor(action, modelKind);
    const requestBody: Record<string, unknown> = {
      model,
      instructions: payload.systemInstruction?.trim() || undefined,
      input: prompt,
      max_output_tokens: clampMaxOutputTokens(requestedMaxOutputTokens, action, modelKind),
    };

    if (isGpt5Family(model)) {
      requestBody.reasoning = { effort: reasoningEffortFor(action, modelKind) };
      requestBody.text = { verbosity: verbosityFor(action, modelKind) };
    } else {
      requestBody.temperature = clampTemperature(payload.temperature, action, modelKind);
    }

    const openAiEndpoint = "https://api.openai.com/v1/responses";
    guardOpenAiRequest({ endpoint: openAiEndpoint, model, feature: "NoteAiSupabaseFunction" });
    const response = await fetch(openAiEndpoint, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openAiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
    });

    const data = await response.json();
    if (!response.ok) {
      return json({ error: data?.error?.message ?? "OpenAI request failed." }, response.status);
    }

    return json({
      text: extractText(data),
      model,
      modelKind,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown AI function error.";
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

function buildPrompt(input: Required<Pick<AiRequest, "action" | "title" | "body" | "question">>): string {
  const note = `<note>\n<title>${input.title}</title>\n<body>\n${input.body}\n</body>\n</note>`;

  if (input.action === "quick_summary") {
    return `You are My AI, a private assistant inside a note-taking app.
Create a quick intelligent summary of the current note.

Rules:
- Use only the note content inside <note>.
- Do not add outside facts.
- Do not simply repeat the note.
- Identify the main idea and the most important points.
- Preserve important names, numbers, dates, measurements, tasks, or decisions.
- Stay under 250 words unless the note requires slightly more.

${note}`;
  }

  if (input.action === "deep_summary") {
    return `You are My AI, a private assistant inside a note-taking app.
Create a comprehensive intelligent summary of the current note.

Rules:
- Use only the note content inside <note>.
- Do not add outside facts.
- Do not merely compress or repeat the note.
- Infer the note's structure, priorities, and meaning.
- Preserve important names, numbers, dates, measurements, tasks, and decisions.
- Use useful sections such as Main idea, Key points, Important details, Action items, Open questions, and Final takeaway.
- Omit sections that do not apply.

${note}`;
  }

  if (input.action === "study_tutor") {
    return `You are My Vault AI, an assistant built into a private notes and study app.
Selected mode: Study Tutor
The current note is the primary context. You may use wider relevant knowledge to explain concepts, background, terminology, implications, and reasoning.
Clearly distinguish "From the note" from "Additional explanation". Do not invent quotes or claim specific source references unless provided.

${note}`;
  }

  if (input.action === "deep_analysis") {
    return `You are My Vault AI, an assistant built into a private notes and study app.
Selected mode: Deep Analysis
Use the note as the anchor. You may use wider relevant knowledge.
Analyse underlying assumptions, logical structure, implications, objections, possible responses, and relation to broader debates.
Clearly mark what is directly in the note versus broader analysis. Do not fabricate citations.

${note}`;
  }

  if (input.action === "explain_note") {
    return `You are My Vault Islamic Study AI, a specialist study assistant built into a private Islamic notes app.
Selected mode: Explain This Note
Explain the note in simpler, clearer language. Use the note as the base, and use wider explanation only to clarify.

${note}`;
  }

  if (input.action === "organise" || input.action === "format_note") {
    return `You are My AI, a careful note-cleaning assistant inside a note-taking app.
${input.action === "format_note" ? "Format the note without changing meaning significantly." : "Organise and structure the current note comprehensively."}

Rules:
- Keep the original meaning.
- Group related ideas together.
- Add useful headings and subheadings.
- Use bullet points where helpful.
- Separate action items, key ideas, questions, references, or decisions when present.
- Do not add new facts, advice, or interpretation.
- Return simple HTML only.
- Do not use markdown symbols like **, #, or backticks.
- Use only these tags: <h1>, <h2>, <h3>, <p>, <strong>, <em>, <u>, <ul>, <ol>, <li>, <blockquote>, <br>.

${note}`;
  }

  if (input.action === "general_ask") {
    return `You are My Vault AI, an assistant built into a private notes and study app.
Selected mode: General Ask
Answer the user's question normally. Use the note if relevant, otherwise answer generally.

${note}

<question>
${input.question}
</question>`;
  }

  return `You are My Vault AI, an advanced analytical assistant inside a note-taking app.
Answer the user's specific question about the current note.

Core rules:
- Use the note inside <note> as the user's personal context and source data.
- Do not merely rephrase, restate, or summarize the note unless the user asks for a summary.
- Answer the question directly first.
- You may use general knowledge to interpret, explain, compare, or reason about the note.
- Clearly distinguish what is written in the note from your interpretation or general guidance.
- If the note lacks enough information, say what is missing and give the best careful answer possible.
- For medical, legal, financial, or other sensitive topics, be helpful but careful and suggest checking with a qualified professional when appropriate.

${note}

<question>
${input.question}
</question>`;
}

function isGpt5Family(model: string): boolean {
  return /^gpt-5(?:\.|-|$)/i.test(model);
}

function temperatureFor(action: AiAction, modelKind: AiModel): number {
  if (modelKind === "fast") {
    if (action === "ask" || action === "general_ask") return 0.35;
    if (action === "study_tutor" || action === "deep_analysis") return 0.35;
    if (action === "organise" || action === "format_note") return 0.18;
    return 0.25;
  }
  if (action === "ask" || action === "general_ask") return 0.4;
  if (action === "study_tutor" || action === "deep_analysis") return 0.4;
  if (action === "organise" || action === "format_note") return 0.2;
  return 0.28;
}

function clampTemperature(requested: number | undefined, action: AiAction, modelKind: AiModel): number {
  const fallback = temperatureFor(action, modelKind);
  const value = typeof requested === "number" && Number.isFinite(requested) ? requested : fallback;
  return Math.min(Math.max(value, 0), 1);
}

function maxOutputTokensFor(action: AiAction, modelKind: AiModel): number {
  if (modelKind === "fast") {
    if (action === "quick_summary") return 800;
    if (action === "ask" || action === "general_ask") return 1800;
    if (action === "study_tutor" || action === "explain_note") return 2600;
    if (action === "deep_analysis") return 3000;
    if (action === "organise") return 3600;
    if (action === "format_note") return 2200;
    return 1800;
  }
  if (action === "quick_summary") return 1200;
  if (action === "ask" || action === "general_ask") return 4000;
  if (action === "study_tutor" || action === "explain_note") return 5500;
  if (action === "deep_analysis") return 7000;
  if (action === "organise") return 8000;
  if (action === "format_note") return 4000;
  return 3600;
}

function clampMaxOutputTokens(requested: number, action: AiAction, modelKind: AiModel): number {
  const fallback = maxOutputTokensFor(action, modelKind);
  const safeRequested = Number.isFinite(requested) && requested > 0 ? requested : fallback;
  const ceiling = modelKind === "smart" ? 12000 : 4500;
  return Math.min(Math.max(Math.round(safeRequested), 500), ceiling);
}

function reasoningEffortFor(action: AiAction, modelKind: AiModel): "low" | "medium" | "high" {
  if (modelKind === "fast") return action === "deep_analysis" || action === "organise" ? "medium" : "low";
  if (action === "quick_summary") return "low";
  if (action === "ask" || action === "general_ask" || action === "deep_summary") return "medium";
  return "high";
}

function verbosityFor(action: AiAction, modelKind: AiModel): "low" | "medium" | "high" {
  if (action === "quick_summary") return "low";
  if (modelKind === "fast") return action === "organise" ? "medium" : "low";
  if (action === "deep_analysis" || action === "study_tutor" || action === "organise") return "high";
  return "medium";
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

  if (!text) {
    throw new Error("OpenAI did not return text.");
  }
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
  console.info("OpenAI request", {
    endpoint: request.endpoint,
    model: request.model,
    feature: request.feature,
    timestamp: Date.now(),
  });
}
