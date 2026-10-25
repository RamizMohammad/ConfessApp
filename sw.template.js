/*
 * Confess service worker — caches ONLY the app's own files (the "shell").
 * It never touches API calls, Google, Cloudflare or GIPHY (all other origins), so no
 * confession, token or personal data is ever written to the device's cache.
 * Build id and file list are filled in at build time (vite.config.ts).
 */
const BUILD = "__BUILD__";
const CACHE = `confess-shell-${BUILD}`;
const SHELL = __ASSETS__;

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key !== CACHE) await caches.delete(key);
    await self.clients.claim();
  })());
});

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== self.location.origin) return;   // never other origins

  if (req.mode === "navigate") {
    // Always try the network first so a new version is picked up; offline → cached shell.
    event.respondWith(fetch(req).catch(() => caches.match("/index.html")));
    return;
  }
  if (SHELL.includes(url.pathname)) {
    event.respondWith(caches.match(url.pathname).then((hit) => hit || fetch(req)));
  }
});
