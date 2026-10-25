/**
 * Google sign-in by full-page redirect (OpenID Connect, response_type=id_token).
 *
 * Why a redirect and not Google's popup button: popups are unreliable inside iPhone
 * home-screen apps, and our Cross-Origin-Opener-Policy blocks popup messaging anyway.
 *
 *   start()  → random `state` + `nonce` kept in sessionStorage (this tab only) → accounts.google.com
 *   Google   → back to /auth/google#id_token=…&state=…
 *   finish() → state must match, the token's nonce must match, audience must be our client id;
 *              the URL fragment is wiped straight away. The ID token is then sent to the server,
 *              which verifies Google's signature. It is never stored anywhere.
 */
import { config } from "../config.ts";
import { jwtPayload, toBase64url } from "./base64url.ts";

export const CALLBACK_PATH = "/auth/google";
const KEY = "confess.google";

export type GoogleResult =
  | { kind: "token"; idToken: string }
  | { kind: "cancelled" }
  | { kind: "error"; message: string };

function random(): string {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return toBase64url(b);
}

export function googleConfigured(): boolean {
  return config.googleClientId.endsWith(".apps.googleusercontent.com");
}

export function googleAuthUrl(state: string, nonce: string, origin = window.location.origin): string {
  const u = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  u.searchParams.set("client_id", config.googleClientId);
  u.searchParams.set("redirect_uri", origin + CALLBACK_PATH);
  u.searchParams.set("response_type", "id_token");
  u.searchParams.set("scope", "openid email");
  u.searchParams.set("state", state);
  u.searchParams.set("nonce", nonce);
  u.searchParams.set("prompt", "select_account");
  return u.toString();
}

/** Leave for Google. `intent` comes back to us so we know why we went. */
export function startGoogleSignIn(intent: "signin" | "reauth" = "signin"): void {
  const state = random();
  const nonce = random();
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ state, nonce, intent, at: Date.now() }));
  } catch {
    /* without sessionStorage finish() will refuse — safer than skipping the checks */
  }
  window.location.assign(googleAuthUrl(state, nonce));
}

export function isGoogleCallback(path = window.location.pathname): boolean {
  return path === CALLBACK_PATH;
}

/**
 * Pure check of a callback fragment against what we stored (exported for tests).
 * `now` in seconds.
 */
export function checkCallback(hash: string, stored: { state: string; nonce: string; at: number } | null,
                              clientId: string, now = Date.now() / 1000): GoogleResult {
  const p = new URLSearchParams(hash.replace(/^#/, ""));
  if (p.get("error")) {
    return p.get("error") === "access_denied" ? { kind: "cancelled" } : { kind: "error", message: "Google sign-in didn't go through. Try again." };
  }
  const idToken = p.get("id_token");
  if (!stored || !idToken) return { kind: "error", message: "Google sign-in didn't go through. Try again." };
  if (now * 1000 - stored.at > 10 * 60_000) return { kind: "error", message: "Your sign-in timed out. Please continue with Google again." };
  if (p.get("state") !== stored.state) return { kind: "error", message: "Google sign-in didn't go through. Try again." };
  const claims = jwtPayload(idToken);
  const aud = claims?.aud;
  if (!claims || claims.nonce !== stored.nonce || (Array.isArray(aud) ? !aud.includes(clientId) : aud !== clientId)
      || typeof claims.exp !== "number" || claims.exp < now) {
    return { kind: "error", message: "Google sign-in didn't go through. Try again." };
  }
  return { kind: "token", idToken };
}

/** Read and wipe the callback. Call once, on /auth/google. */
export function finishGoogleSignIn(): GoogleResult {
  const hash = window.location.hash;
  // Get the token out of the address bar and history immediately.
  window.history.replaceState(null, "", "/");
  let stored = null;
  try {
    const raw = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    stored = raw ? JSON.parse(raw) : null;
  } catch {
    stored = null;
  }
  return checkCallback(hash, stored, config.googleClientId);
}
