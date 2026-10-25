import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { ApiError, _setFetch, api, setApiHooks } from "./api.ts";

type Call = { url: string; init: RequestInit };
let calls: Call[] = [];
let script: ((c: Call) => Response | Promise<Response>) = () => new Response("{}");

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const okBody = (data: unknown) => ({ success: true, data, error: null });
const errBody = (code: string) => ({ success: false, data: null, error: { code, message: code } });

beforeEach(() => {
  calls = [];
  _setFetch(async (input, init) => {
    const c = { url: String(input), init: init ?? {} };
    calls.push(c);
    return script(c);
  });
  setApiHooks({ onSignedOut: undefined, onReauthRequired: undefined, onSession: undefined });
});

test("every call: web header, cookies, no token, no cache, no referrer", async () => {
  script = () => json(200, okBody({ alias: "x" }));
  await api("/users/me");
  const h = calls[0]!.init.headers as Record<string, string>;
  assert.equal(h["X-Confess-Web"], "1");
  assert.equal(h.Authorization, undefined);
  assert.equal(calls[0]!.init.credentials, "include");
  assert.equal(calls[0]!.init.cache, "no-store");
  assert.equal(calls[0]!.init.referrerPolicy, "no-referrer");
  assert.match(calls[0]!.url, /\/api\/v2\/users\/me$/);
});

test("401 → one shared refresh → retry", async () => {
  let me = 0;
  script = (c) => {
    if (c.url.endsWith("/auth/web/refresh")) return json(200, okBody({ expiresIn: 900, sessionId: "s" }));
    return ++me <= 2 ? json(401, errBody("UNAUTHORIZED")) : json(200, okBody({ ok: true }));
  };
  let sessions = 0;
  setApiHooks({ onSession: () => { sessions++; } });
  const [a, b] = await Promise.all([api("/users/me"), api("/users/me")]);
  assert.deepEqual(a, { ok: true });
  assert.deepEqual(b, { ok: true });
  assert.equal(calls.filter((c) => c.url.endsWith("/auth/web/refresh")).length, 1);
  assert.equal(sessions, 1);
});

test("refresh fails → signed out", async () => {
  script = (c) => c.url.endsWith("/auth/web/refresh") ? json(401, errBody("REFRESH_INVALID")) : json(401, errBody("UNAUTHORIZED"));
  let out = false;
  setApiHooks({ onSignedOut: () => { out = true; } });
  await assert.rejects(api("/users/me"), (e: ApiError) => e.status === 401);
  assert.equal(out, true);
});

test("REAUTH_REQUIRED → Face ID check → retried once", async () => {
  let n = 0;
  script = () => (++n === 1 ? json(403, errBody("REAUTH_REQUIRED")) : json(200, okBody({ removed: true })));
  let asked = 0;
  setApiHooks({ onReauthRequired: async () => { asked++; return true; } });
  assert.deepEqual(await api("/auth/passkey/x", { method: "DELETE" }), { removed: true });
  assert.equal(asked, 1);
});

test("REAUTH cancelled → error surfaces", async () => {
  script = () => json(403, errBody("REAUTH_REQUIRED"));
  setApiHooks({ onReauthRequired: async () => false });
  await assert.rejects(api("/auth/passkey/x", { method: "DELETE" }), (e: ApiError) => e.code === "REAUTH_REQUIRED");
});

test("raw calls never refresh", async () => {
  script = () => json(401, errBody("UNAUTHORIZED"));
  await assert.rejects(api("/auth/web/login", { method: "POST", body: {}, raw: true }));
  assert.equal(calls.length, 1);
});

test("offline → friendly error", async () => {
  _setFetch(async () => { throw new TypeError("Failed to fetch"); });
  await assert.rejects(api("/users/me"), (e: ApiError) => e.code === "OFFLINE");
});
