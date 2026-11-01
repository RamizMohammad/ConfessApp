/*
 * Confess service worker — caches ONLY the app's own files (the "shell"), and shows
 * notifications (web push, phase 5).
 * It never touches API calls, Google, Cloudflare or GIPHY (all other origins), so no
 * confession, token or personal data is ever written to the device's cache.
 * Build id and file list are filled in at build time (vite.config.ts).
 */
const BUILD = "__BUILD__";
const CACHE = `confess-shell-${BUILD}`;
const SHELL = __ASSETS__;
const API = __API__;

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
  if (url.pathname.startsWith("/api/")) return;                               // never the API

  if (req.mode === "navigate") {
    // Always try the network first so a new version is picked up; offline → cached shell.
    event.respondWith(fetch(req).catch(() => caches.match("/index.html")));
    return;
  }
  if (SHELL.includes(url.pathname)) {
    event.respondWith(caches.match(url.pathname).then((hit) => hit || fetch(req)));
  }
});

/* ── Notifications ─────────────────────────────────────────────────────────
 * The server encrypts every push for this iPhone only (RFC 8291); the browser decrypts it and
 * hands us { title, body, url, tag }. "Private" pushes say only "Confess · Something new…".
 * `url` is always a path inside the app — anything else opens the feed.
 */
const safePath = (u) => (typeof u === "string" && u.startsWith("/") && !u.startsWith("//") && !u.includes("\\") ? u : "/");

self.addEventListener("push", (event) => {
  let msg = {};
  try {
    msg = event.data ? event.data.json() : {};
  } catch {
    msg = {};
  }
  const title = typeof msg.title === "string" && msg.title ? msg.title.slice(0, 120) : "Confess";
  const body = typeof msg.body === "string" ? msg.body.slice(0, 240) : "Something new is waiting for you.";
  event.waitUntil(self.registration.showNotification(title, {
    body,
    tag: typeof msg.tag === "string" ? msg.tag.slice(0, 64) : undefined,
    icon: "/icons/icon-192.png",
    badge: "/icons/icon-192.png",
    data: { url: safePath(msg.url) },
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = safePath(event.notification.data && event.notification.data.url);
  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const win = wins.find((w) => new URL(w.url).origin === self.location.origin);
    if (win) {
      await win.focus();
      win.postMessage({ type: "confess:open", url });        // the app routes it (keeps its state)
      return;
    }
    await self.clients.openWindow(url);
  })());
});

// The push service rotated this iPhone's subscription: hand the new one to the server.
self.addEventListener("pushsubscriptionchange", (event) => {
  event.waitUntil((async () => {
    try {
      const old = event.oldSubscription;
      const sub = event.newSubscription || (old && await self.registration.pushManager.subscribe(old.options));
      if (!sub) return;
      const json = sub.toJSON();
      await fetch(`${API}/api/v2/push/web/subscribe`, {
        method: "POST", credentials: "include", cache: "no-store",
        headers: { "Content-Type": "application/json", "X-Confess-Web": "1" },
        body: JSON.stringify({ endpoint: json.endpoint, keys: json.keys, private: true }),
      });
    } catch {
      /* next app start re-subscribes */
    }
  })());
});
