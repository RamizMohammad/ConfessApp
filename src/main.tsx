import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./styles/app.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Service worker: app shell only (see sw.template.js). Production builds only.
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", async () => {
    try {
      const reg = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
      const announce = (w: ServiceWorker | null) => {
        if (w && navigator.serviceWorker.controller) window.dispatchEvent(new CustomEvent("confess:update", { detail: w }));
      };
      announce(reg.waiting);
      reg.addEventListener("updatefound", () => {
        const w = reg.installing;
        w?.addEventListener("statechange", () => w.state === "installed" && announce(w));
      });
      let reloaded = false;
      navigator.serviceWorker.addEventListener("controllerchange", () => {
        if (!reloaded) {
          reloaded = true;
          window.location.reload();
        }
      });
      // Look for a new version whenever the app comes back to the foreground.
      document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && void reg.update());
    } catch {
      /* no service worker: the app still works, just without offline shell */
    }
  });
}
