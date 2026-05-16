export interface Env {
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  SYNC_SHARED_SECRET: string;
}

type SyncTable =
  | "folders"
  | "notes"
  | "blocks"
  | "tags"
  | "note_tags"
  | "note_tables"
  | "attachments";

const TABLES: SyncTable[] = ["folders", "notes", "blocks", "tags", "note_tags", "note_tables", "attachments"];

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    if (url.pathname === "/health") {
      return json({ ok: true });
    }

    if (!isAuthorized(request, env)) {
      return json({ error: "Unauthorized" }, 401);
    }

    if (url.pathname === "/sync/pull" && request.method === "GET") {
      return pullSnapshot(env);
    }

    if (url.pathname === "/sync/push" && request.method === "POST") {
      const snapshot = await request.json<Record<string, unknown[]>>();
      return pushSnapshot(env, snapshot);
    }

    return json({ error: "Not found" }, 404);
  },
};

function isAuthorized(request: Request, env: Env): boolean {
  const expected = env.SYNC_SHARED_SECRET;
  if (!expected) return false;
  const header = request.headers.get("authorization") ?? "";
  return header === `Bearer ${expected}`;
}

async function pullSnapshot(env: Env): Promise<Response> {
  const payload: Record<string, unknown> = {};

  for (const table of TABLES) {
    const response = await supabase(env, `/rest/v1/${table}?select=*`, { method: "GET" });
    if (!response.ok) return proxyError(response, table);
    payload[table] = await response.json();
  }

  return json({ serverTime: Date.now(), data: payload });
}

async function pushSnapshot(env: Env, snapshot: Record<string, unknown[]>): Promise<Response> {
  for (const table of TABLES) {
    const rows = snapshot[table];
    if (!Array.isArray(rows) || rows.length === 0) continue;

    const response = await supabase(env, `/rest/v1/${table}`, {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates" },
      body: JSON.stringify(rows),
    });
    if (!response.ok) return proxyError(response, table);
  }

  return json({ ok: true, serverTime: Date.now() });
}

async function supabase(env: Env, path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("apikey", env.SUPABASE_SERVICE_ROLE_KEY);
  headers.set("authorization", `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`);
  headers.set("content-type", "application/json");

  return fetch(`${env.SUPABASE_URL}${path}`, {
    ...init,
    headers,
  });
}

async function proxyError(response: Response, table: string): Promise<Response> {
  const body = await response.text();
  return json({ error: "Supabase request failed", table, status: response.status, body }, 502);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      ...corsHeaders(),
    },
  });
}

function corsHeaders(): Record<string, string> {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type,authorization",
  };
}
