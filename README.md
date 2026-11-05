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
docker run --rm -p 8012:8012 confess-web            # → http://localhost:8012
docker compose up --build -d                        # same, on 127.0.0.1:8012, hardened
```

- Stage 1 (`node:22-alpine`): `npm ci` inside the image (right binaries for Linux — never copy
  `node_modules` in), `npm test`, `npm run build`. A failing test stops the build.
- Stage 2 (`nginx-unprivileged`, non-root, port 8012): serves `dist/` with the **same security
  headers as `vercel.json`** — they're generated from it (`docker/nginx-headers.mjs`), so the two
  can't drift. `/healthz` for health checks.
- Build args (`--build-arg` or a `.env` next to `docker-compose.yml`): `VITE_API_BASE`,
  `VITE_GOOGLE_CLIENT_ID`, `VITE_TURNSTILE_SITE_KEY`, `VITE_PLAY_STORE_URL`, `VITE_WEBSITE_URL` —
  public values only. `VITE_API_BASE` is required; compose reads it from `.env`.
- Compose: localhost-only port, read-only filesystem, all capabilities dropped, no-new-privileges.

On the VPS it runs like the API: the container joins `proxy_network` and the central nginx
proxies `web.confessapp.in` → `http://confess-web:8012` — copy
`deploy/web.confessapp.in.conf` into nginx's sites, get the certificate with certbot,
reload. Don't add security headers in the proxy; the container already sends them.
The same site file also sends `web.confessapp.in/api/…` to `confess-api:8011`, so the app calls
its API on its own address (`VITE_API_BASE=https://web.confessapp.in`). It has to: the sign-in
cookies are `SameSite=Strict`, and Safari blocks cookies from another site. The Android app keeps
using `confess.api.mohammadramiz.in` — same container.

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
| Notifications | Web push, only from the Home Screen app and only after a tap. The server encrypts each push for this iPhone (RFC 8291) and signs it (VAPID — the private key lives only in the server's `.env`). Private by default: the lock screen says just "Confess · Something new is waiting for you". Tied to the session — signing out turns them off. |
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
   - Authorized JavaScript origins: `https://web.confessapp.in`
   - Authorized redirect URIs: `https://web.confessapp.in/auth/google`
   - Put the client ID in `VITE_GOOGLE_CLIENT_ID` **and** add it to the server's `GOOGLE_CLIENT_ID` (comma-separated after the Android one).
2. **Cloudflare Turnstile** widget for `web.confessapp.in` (Invisible or Managed):
   site key → `VITE_TURNSTILE_SITE_KEY`, secret key → server `TURNSTILE_SECRET` (`TURNSTILE_MODE=report`, later `enforce`).
3. **Server** `.env.prod`: `WEB_APP_ORIGIN=https://web.confessapp.in`,
   `PASSKEY_RP_ID=confessapp.in`, run `sql/0022_passkeys.sql`, deploy.
4. **Vercel**: import this folder, framework Vite, set the `VITE_*` variables, add the domain
   `web.confessapp.in` (CNAME to Vercel). `vercel.json` is picked up automatically.

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
  lib/avatar.ts      designed avatars ("av1:…") — port of AvatarSpec + AvatarRenderer
  lib/banner.ts      profile banners ("bn1:…") — port of BannerView (same java.util.Random seeds)
  lib/graph.ts       confession graph looks ("g1:…")
  lib/users.ts       profiles, follows, people, account settings
  lib/activity.ts    activity + unread badge (polls once a minute while open)
  screens/           Entry, Onboarding, Secure (Face ID, lock), Home (frame + tabs),
                     Feed, PostDetail, Compose, Drafts, Activity, Me, Profile, People
                     (followers, search, letters, felt/responses), AvatarStudio, Settings
  components/        post.tsx (card, reactions, poll, report/block/delete), art.tsx (avatar, banner, graph),
                     people.tsx (follow), GifPicker, Aura, toast
  data/terms.json    community guidelines (same file as the Android app)
  android/           GENERATED from the Android app's res/ (scripts/android_sync.py) — don't edit:
                     tokens.css/.ts (colours, dimens, text styles, motion), strings.ts, icons.ts,
                     fonts.css, splash + launcher SVGs
  ui/kit.tsx         the Android components at Android sizes: buttons, chips, switch, setting row,
                     step bar, toolbar, scope / glide toggles, text field, ConfessDialog, sheets,
                     ChoiceSheet, PopMenu, skeleton, paper note, plate, snackbar (+ kit.css)
  ui/Icon.tsx        <Icon name="bell" /> — the app's own ic_*.xml vectors
  lib/strings.ts     S("key", …) / P("plural", n) / A("array") — the app's English strings
  lib/shell.ts       iPhone / iPad shell: keyboard height, edge swipe-back, scroll lock, useWide()
  screens/DesignCheck.tsx  /dev/design — every token and component (twin of DesignCheckActivity)
  ui/Art.tsx         <Art> logos / Letty poses, <AnimatedArt> plays Android's animated vectors (splash, tab icons)
  ui/motion.ts       tween(): View.animate() with Android interpolators (Web Animations, CSP-safe)
  ui/screens.css     the Android layouts, one block per layout file
  screens/Launch.tsx splash (Letty's opening, zoom-out) + maintenance (GET /api/v2/ready ≠ 200)
  screens/Welcome.tsx welcome, "A glimpse inside", the sign-in sheet
  screens/Onboarding.tsx guidelines (also read-only), alias (+ password lock), avatar
  screens/Secure.tsx unlock (Face ID where Android has the fingerprint), Face ID offer, "Confirm it's you"
  screens/Tour.tsx   the feature tour with its live samples (once after sign-in, Settings → Take the tour)
sw.template.js       service worker (build fills in the file list)
vercel.json          hosting + security headers (source of truth for Docker's too)
Dockerfile           test + build, then non-root nginx
docker/              nginx.conf + nginx-headers.mjs (vercel.json → nginx headers)
docker-compose.yml   hardened VPS run (proxy_network, like the API)
deploy/              central-nginx site for web.confessapp.in (app + /api → confess-api)
```

## Looking exactly like the Android app

The Android res/ folder is the source of truth. After changing colours, sizes, text styles,
animations, strings, icons or fonts there:

```
npm run android:sync -- ../ConfessMain/app/src/main/res     # rewrites src/android/ + public/fonts
npm run android:check                                       # CI: fails if src/android/ is stale
node scripts/make_app_images.mjs                            # only if the launcher icon / splash changed
```

1 dp on Android = 1 CSS px = 1 pt on iPhone. The reference phone is 393 dp wide (Pixel 7/8 at 1080 px,
or any phone after `adb shell wm density 440`), the same width as iPhone 14 Pro–16.

Checking a screen against the phone (needs `npm i --no-save playwright` and adb):

```
scripts/replica/capture_android.sh design settings          # phone → replica/android/*.png
node scripts/replica/shoot.mjs /dev/design /settings         # web → replica/web/<device>/*.png
python3 scripts/replica/compare.py design=dev_design         # side by side, overlay, diff → replica/report
```

Phase 4a (feed, responses, compose, drafts, report/block), 4b (profiles, avatar studio, banners, confession graph, follow, activity, settings) 4c (stories, books + reader + writer's desk, bursts) and 5 (notifications on the Home Screen app) are in. The Android look-alike pass (R0–R8, see the replica plan) runs before 6 — hardening and soft launch; R0 (tokens, kit, iPhone/iPad shell, /dev/design, replica harness) and R1 (splash, maintenance, welcome, sign-in, guidelines, alias, avatar, unlock, feature tour) are in.
