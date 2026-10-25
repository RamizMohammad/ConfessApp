/**
 * Build-time settings. All of these are PUBLIC (they ship in the bundle) — see .env.example.
 * Nothing secret is ever configured in the web app.
 */
// Vite fills import.meta.env at build time; plain Node (unit tests) falls back to process.env.
const env: Partial<ImportMetaEnv> & Record<string, string | undefined> =
  import.meta.env ?? (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env ?? {};

function clean(v: string | undefined, fallback = ""): string {
  return (v ?? fallback).trim().replace(/\/+$/, "");
}

export const config = {
  /** From VITE_API_BASE (.env) — required; the build stops without it (vite.config.ts). */
  apiBase: clean(env.VITE_API_BASE),
  googleClientId: clean(env.VITE_GOOGLE_CLIENT_ID),
  turnstileSiteKey: clean(env.VITE_TURNSTILE_SITE_KEY),
  playStoreUrl: clean(env.VITE_PLAY_STORE_URL, "https://play.google.com/store/apps/details?id=in.mohammad.ramiz.confess"),
  websiteUrl: clean(env.VITE_WEBSITE_URL, "https://confess.mohammadramiz.in"),
  /** Lock again after this long in the background (same default as the Android app). */
  relockAfterMs: 5 * 60_000,
} as const;
