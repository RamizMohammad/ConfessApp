/**
 * The only way the web app talks to the server.
 *
 *  - Session = two HttpOnly cookies the server sets. This file never sees a token:
 *    there is nothing in JavaScript worth stealing.
 *  - Every call sends `X-Confess-Web: 1` and `credentials: "include"`; the server only
 *    accepts the cookie together with that header from our exact origin (CSRF guard).
 *  - 401 → one silent refresh (shared by all callers), then the call is retried once.
 *    If the refresh fails the user is signed out.
 *  - 403 REAUTH_REQUIRED → ask the UI for a Face ID check, then retry once.
 *  - No caching anywhere (`cache: "no-store"`), no referrer.
 */
import { config } from "../config.ts";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: { field: string; msg: string }[];

  constructor(status: number, code: string, message: string, fields: { field: string; msg: string }[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

export interface SessionInfo {
  expiresIn: number;
  sessionId: string;
}

type Hooks = {
  /** Session is gone for good (refresh failed). */
  onSignedOut?: () => void;
  /** Server asked for a fresh Face ID / Google check. Resolve true when done, false if cancelled. */
  onReauthRequired?: () => Promise<boolean>;
  /** A new access cookie arrived; lets the app schedule the next refresh. */
  onSession?: (info: SessionInfo) => void;
};

const hooks: Hooks = {};
export function setApiHooks(h: Hooks): void {
  Object.assign(hooks, h);
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  /** Don't try refresh / reauth on failure (used by the auth endpoints themselves). */
  raw?: boolean;
  signal?: AbortSignal;
}

let fetchImpl: typeof fetch = (...a) => fetch(...a);
/** Tests only. */
export function _setFetch(f: typeof fetch): void {
  fetchImpl = f;
}

function url(path: string, query?: RequestOptions["query"]): string {
  const u = new URL(`${config.apiBase}/api/v2${path}`);
  for (const [k, v] of Object.entries(query ?? {})) if (v !== undefined) u.searchParams.set(k, String(v));
  return u.toString();
}

async function send<T>(path: string, opts: RequestOptions): Promise<T> {
  const headers: Record<string, string> = { "X-Confess-Web": "1", Accept: "application/json" };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  let res: Response;
  try {
    res = await fetchImpl(url(path, opts.query), {
      method: opts.method ?? "GET",
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      credentials: "include",
      cache: "no-store",
      referrerPolicy: "no-referrer",
      redirect: "error",
      signal: opts.signal,
    });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(0, "OFFLINE", "Can't reach Confess right now. Check your connection.");
  }
  let payload: { success?: boolean; data?: T; error?: { code?: string; message?: string; fields?: [] } } = {};
  try {
    payload = await res.json();
  } catch {
    /* empty or non-JSON body */
  }
  if (!res.ok || payload.success === false) {
    const e = payload.error ?? {};
    throw new ApiError(res.status, e.code ?? (res.status === 429 ? "RATE_LIMITED" : "ERROR"),
      e.message ?? "Something went wrong. Try again.", e.fields ?? []);
  }
  return payload.data as T;
}

let refreshing: Promise<boolean> | null = null;

/** Rotate the session cookies. Concurrent callers share one request. */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = send<SessionInfo>("/auth/web/refresh", { method: "POST", raw: true })
      .then((info) => {
        hooks.onSession?.(info);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  try {
    return await send<T>(path, opts);
  } catch (e) {
    if (!(e instanceof ApiError) || opts.raw) throw e;
    if (e.status === 401) {
      if (await refreshSession()) return send<T>(path, opts);
      hooks.onSignedOut?.();
      throw e;
    }
    if (e.status === 403 && e.code === "REAUTH_REQUIRED" && hooks.onReauthRequired) {
      if (await hooks.onReauthRequired()) return send<T>(path, opts);
    }
    throw e;
  }
}
