import { test } from "node:test";
import assert from "node:assert/strict";
import { checkCallback, googleAuthUrl } from "./google.ts";
import { toBase64url } from "./base64url.ts";

const CID = "123-abc.apps.googleusercontent.com";
const now = 1_800_000_000;
const stored = { state: "S".repeat(43), nonce: "N".repeat(43), at: now * 1000 - 5_000 };

function jwt(claims: Record<string, unknown>): string {
  const enc = (o: unknown) => toBase64url(new TextEncoder().encode(JSON.stringify(o)));
  return `${enc({ alg: "RS256" })}.${enc(claims)}.sig`;
}
const good = { aud: CID, nonce: stored.nonce, exp: now + 300, email: "a@x.com" };
const hash = (token: string, state = stored.state) => `#id_token=${token}&state=${state}`;

test("accepts a matching token", () => {
  const r = checkCallback(hash(jwt(good)), stored, CID, now);
  assert.equal(r.kind, "token");
});

test("rejects wrong state (CSRF / injected callback)", () => {
  assert.equal(checkCallback(hash(jwt(good), "other"), stored, CID, now).kind, "error");
});

test("rejects a token minted for another nonce (replayed token)", () => {
  assert.equal(checkCallback(hash(jwt({ ...good, nonce: "x" })), stored, CID, now).kind, "error");
});

test("rejects another app's token", () => {
  assert.equal(checkCallback(hash(jwt({ ...good, aud: "evil.apps.googleusercontent.com" })), stored, CID, now).kind, "error");
});

test("rejects expired tokens and stale sign-ins", () => {
  assert.equal(checkCallback(hash(jwt({ ...good, exp: now - 1 })), stored, CID, now).kind, "error");
  assert.equal(checkCallback(hash(jwt(good)), { ...stored, at: now * 1000 - 11 * 60_000 }, CID, now).kind, "error");
});

test("rejects when nothing was started in this tab", () => {
  assert.equal(checkCallback(hash(jwt(good)), null, CID, now).kind, "error");
});

test("user cancelling at Google", () => {
  assert.equal(checkCallback("#error=access_denied&state=x", stored, CID, now).kind, "cancelled");
});

test("auth URL asks only for an ID token with state + nonce", () => {
  const u = new URL(googleAuthUrl("st", "no", "https://app.example"));
  assert.equal(u.origin + u.pathname, "https://accounts.google.com/o/oauth2/v2/auth");
  assert.equal(u.searchParams.get("response_type"), "id_token");
  assert.equal(u.searchParams.get("scope"), "openid email");
  assert.equal(u.searchParams.get("redirect_uri"), "https://app.example/auth/google");
  assert.equal(u.searchParams.get("state"), "st");
  assert.equal(u.searchParams.get("nonce"), "no");
});
