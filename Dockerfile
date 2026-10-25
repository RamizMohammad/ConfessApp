# syntax=docker/dockerfile:1
#
# Confess web app.  Stage 1 tests and builds; stage 2 is a small non-root nginx serving dist/.
#
#   docker build -t confess-web --build-arg VITE_API_BASE=https://… .
#   docker compose up --build        (reads the same values from .env)
#   docker run --rm -p 8012:8012 confess-web      → http://localhost:8012
#
# Build args are PUBLIC values only (they end up in the JavaScript) — never pass a secret here.

# ── Stage 1: test + build ─────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app
ENV CI=true \
    NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_AUDIT=false

# Dependencies first (cached until package files change). Installed inside the image,
# so the right platform binaries are picked — never copy node_modules from a Mac.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

# Required: the API address (no default — the build stops without it).
ARG VITE_API_BASE
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_TURNSTILE_SITE_KEY=
ARG VITE_PLAY_STORE_URL=https://play.google.com/store/apps/details?id=in.mohammad.ramiz.confess
ARG VITE_WEBSITE_URL=https://confess.mohammadramiz.in
ENV VITE_API_BASE=$VITE_API_BASE \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY \
    VITE_PLAY_STORE_URL=$VITE_PLAY_STORE_URL \
    VITE_WEBSITE_URL=$VITE_WEBSITE_URL

# A failing test or type error stops the image from being built.
RUN npm test \
 && npm run build \
 && node docker/nginx-headers.mjs > /app/security-headers.conf


# ── Stage 2: serve ────────────────────────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html

USER nginx
EXPOSE 8012
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8012/healthz || exit 1
