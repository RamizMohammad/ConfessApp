/**
 * Cloudflare Turnstile — invisible "are you a person" check before sign-in / sign-up.
 * Only the public SITE key is here; the server checks the token with its secret.
 * No site key configured → no check (the server must then have TURNSTILE_MODE=off).
 */
import { config } from "../config.ts";

interface TurnstileApi {
  render(el: HTMLElement, opts: Record<string, unknown>): string;
  execute(id: string): void;
  reset(id: string): void;
  remove(id: string): void;
}
declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

let loading: Promise<TurnstileApi> | null = null;

function load(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile);
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
      s.async = true;
      s.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error("turnstile")));
      s.onerror = () => {
        loading = null;
        reject(new Error("turnstile"));
      };
      document.head.appendChild(s);
    });
  }
  return loading;
}

/** A fresh single-use token, or undefined when Turnstile isn't configured. */
export async function turnstileToken(): Promise<string | undefined> {
  if (!config.turnstileSiteKey) return undefined;
  const ts = await load();
  const host = document.createElement("div");
  host.className = "turnstile-host";
  document.body.appendChild(host);
  try {
    let timer: number | undefined;
    return await new Promise<string>((resolve, reject) => {
      const id = ts.render(host, {
        sitekey: config.turnstileSiteKey,
        appearance: "interaction-only",       // invisible unless Cloudflare really needs a click
        execution: "render",
        theme: "dark",
        callback: (token: string) => resolve(token),
        "error-callback": () => reject(new Error("turnstile")),
        "timeout-callback": () => reject(new Error("turnstile")),
      });
      timer = window.setTimeout(() => {
        try {
          ts.remove(id);
        } catch {
          /* ignore */
        }
        reject(new Error("turnstile"));
      }, 60_000);
    }).finally(() => window.clearTimeout(timer));
  } finally {
    setTimeout(() => host.remove(), 0);
  }
}
