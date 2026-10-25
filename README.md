# Confess — web app (iPhone)

The installable web app for Confess. On iPhone people open it in Safari, tap **Share → Add to
Home Screen**, and from then on it opens like an app and unlocks with Face ID.

React 19 + Vite 7 + TypeScript. Five dev dependencies, two runtime ones (react, react-dom).

```bash
npm install
cp .env.example .env            # fill in — PUBLIC values only (VITE_API_BASE is required)
npm run dev                     # http://localhost:5173
npm test                        # unit tests (Node's built-in runner, no extra packages)
npm run build                   # type-check + production build → dist/
```

## Docker

```bash
docker build -t confess-web --build-arg VITE_API_BASE=https://… .   # runs the tests, then builds
docker run --rm -p 8012:8080 confess-web            # → http://localhost:8012
docker compose up --build -d                        # same, on 127.0.0.1:8012, hardened
```

- Stage 1 (`node:22-alpine`): `npm ci` inside the image (right binaries for Linux — never copy
  `node_modules` in), `npm test`, `npm run build`. A failing test stops the build.
- Stage 2 (`nginx-unprivileged`, non-root, port 8080): serves `dist/` with the **same security
  headers as `vercel.json`** — they're generated from it (`docker/nginx-headers.mjs`), so the two
  can't drift. `/healthz` for health checks.
- Build args (`--build-arg` or a `.env` next to `docker-compose.yml`): `VITE_API_BASE`,
  `VITE_GOOGLE_CLIENT_ID`, `VITE_TURNSTILE_SITE_KEY`, `VITE_PLAY_STORE_URL`, `VITE_WEBSITE_URL` —
  public values only. `VITE_API_BASE` is required; compose reads it from `.env`.
- Compose: localhost-only port, read-only filesystem, all capabilities dropped, no-new-privileges.

On the VPS, put it behind your central nginx (TLS there) and don't add security headers in
that proxy — the container already sends them:

```nginx
server {
    server_name app.confess.mohammadramiz.in;
    location / { proxy_pass http://127.0.0.1:8012; proxy_set_header Host $host; }
}
```

## Security model — nothing secret in the browser

| What | Where it lives |
|---|---|
| Session | Two `HttpOnly; Secure; SameSite=Strict` cookies set by the API (`__Host-cf_at`, `__Secure-cf_rt`). JavaScript can't read them; this code never sees a token. |
| API key / signing key | None on the web. The server identifies the web app by its exact Origin + the `X-Confess-Web: 1` header (CSRF guard). |
| GIPHY key | Server only (`/api/v2/gifs/search`). |
| Turnstile | Only the public **site** key here; the secret is the server's `TURNSTILE_SECRET`. |
| Google | Only the public client ID. The ID token comes back in the URL fragment, is checked (state, nonce, audience, expiry), wiped from the address bar at once, sent to the server and never stored. |
| Face ID | Passkeys. The private key never leaves the phone's Secure Enclave; the server keeps only the public key. The phone lists it as "Confess account", never the alias. |
| Local storage | Only flags: `confess.faceid`, `confess.faceid_declined`, `confess.install_dismissed`. |
| Offline cache | The service worker caches the app's own files only — never API responses or any other origin. |
| Drafts | Unsent confessions stay on the device in IndexedDB, AES-GCM encrypted with a non-extractable key; wiped on sign-out. |
| GIFs | Searched through the Confess server; images load only from `media.giphy.com`, no referrer. |

Headers (`vercel.json`): strict CSP (`default-src 'none'`, scripts only from this site + Cloudflare
Turnstile, connections only to the API), no framing, no referrer, HSTS, COOP, Permissions-Policy
allowing only passkeys. No inline scripts or styles, no source maps in production.

App lock: after 5 minutes in the background (same as Android) or on a cold start, Face ID
(or the password lock) is needed again. While in the background the content is blurred so the
iOS app switcher shows nothing.

## Setup checklist

1. **Google Cloud Console → Credentials → OAuth client ID → Web application**
   - Authorized JavaScript origins: `https://app.confess.mohammadramiz.in`
   - Authorized redirect URIs: `https://app.confess.mohammadramiz.in/auth/google`
   - Put the client ID in `VITE_GOOGLE_CLIENT_ID` **and** add it to the server's `GOOGLE_CLIENT_ID` (comma-separated after the Android one).
2. **Cloudflare Turnstile** widget for `app.confess.mohammadramiz.in` (Invisible or Managed):
   site key → `VITE_TURNSTILE_SITE_KEY`, secret key → server `TURNSTILE_SECRET` (`TURNSTILE_MODE=report`, later `enforce`).
3. **Server** `.env.prod`: `WEB_APP_ORIGIN=https://app.confess.mohammadramiz.in`,
   `PASSKEY_RP_ID=confess.mohammadramiz.in`, run `sql/0022_passkeys.sql`, deploy.
4. **Vercel**: import this folder, framework Vite, set the `VITE_*` variables, add the domain
   `app.confess.mohammadramiz.in` (CNAME to Vercel). `vercel.json` is picked up automatically.

The API address lives only in `.env` (`VITE_API_BASE`). `vercel.json` / nginx allow `connect-src 'self' https:`
and the build pins the exact API in the page's own CSP (`vite.config.ts`), so moving the API is a `.env` change.

## Layout

```
src/
  lib/api.ts         every server call: cookies, refresh, Face ID re-check
  lib/session.tsx    signed in / out, onboarding, lock, Face ID
  lib/google.ts      Google sign-in (redirect + state/nonce checks)
  lib/webauthn.ts    Face ID (passkeys) ↔ JSON
  lib/turnstile.ts   bot check
  lib/posts.ts       confessions, responses, reactions, reports, blocks
  lib/postStore.ts   keeps every open list in step (react / report / block / delete anywhere)
  lib/drafts.ts      encrypted on-device drafts
  lib/router.ts      /, /p/<id>, /write, /drafts (back-swipe works)
  screens/           Entry, Onboarding, Secure (Face ID, lock), Home (frame + settings),
                     Feed, PostDetail, Compose, Drafts
  components/        post.tsx (card, reactions, poll, report/block/delete), GifPicker, Aura, toast
  data/terms.json    community guidelines (same file as the Android app)
sw.template.js       service worker (build fills in the file list)
vercel.json          hosting + security headers (source of truth for Docker's too)
Dockerfile           test + build, then non-root nginx
docker/              nginx.conf + nginx-headers.mjs (vercel.json → nginx headers)
docker-compose.yml   hardened local / VPS run
```

Phase 4a (feed, responses, compose, drafts, report/block) is in. Next: 4b — profile, avatar studio, follow, notifications, settings.
