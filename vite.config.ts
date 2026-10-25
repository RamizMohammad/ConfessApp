import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { defineConfig, loadEnv, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

/** Emits /sw.js with this build's id and the exact list of shell files to cache. */
function serviceWorker(): Plugin {
  return {
    name: "confess-sw",
    apply: "build",
    generateBundle(_, bundle) {
      const files = Object.keys(bundle).filter((f) => !f.endsWith(".map")).map((f) => `/${f}`);
      const fonts = ["manrope-400", "manrope-500", "manrope-600", "manrope-700", "newsreader-display",
        "newsreader-display-italic", "newsreader-400", "newsreader-400-italic", "jetbrainsmono-400"].map((f) => `/fonts/${f}.woff2`);
      const shell = ["/index.html", "/manifest.webmanifest", "/icons/apple-touch-icon.png", "/icons/icon-192.png",
        ...fonts, ...files.filter((f) => f !== "/index.html")];
      const build = createHash("sha256").update(shell.join("|")).digest("hex").slice(0, 12);
      const source = readFileSync(new URL("./sw.template.js", import.meta.url), "utf8")
        .replace("__BUILD__", build)
        .replace("__ASSETS__", JSON.stringify(shell));
      this.emitFile({ type: "asset", fileName: "sw.js", source });
    },
  };
}

/**
 * The API address comes only from VITE_API_BASE (.env / build arg). It must be set, and https
 * unless it's a local test server. The build then pins it in the page's own CSP:
 *   vercel.json / nginx send `connect-src 'self' https:` (they can't read .env), and this meta
 *   tag adds `connect-src 'self' <api>`. Browsers enforce both, so only the API is reachable.
 */
function apiOrigin(mode: string): string {
  const raw = (loadEnv(mode, process.cwd(), "VITE_").VITE_API_BASE ?? "").trim().replace(/\/+$/, "");
  if (!raw) throw new Error("VITE_API_BASE is not set — add it to .env (e.g. VITE_API_BASE=https://your-api.example)");
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new Error(`VITE_API_BASE isn't a URL: ${raw}`);
  }
  const local = ["localhost", "127.0.0.1"].includes(u.hostname);
  if (u.protocol !== "https:" && !local) throw new Error(`VITE_API_BASE must be https:// (got ${raw})`);
  return u.origin;
}

function pinApi(origin: string): Plugin {
  return {
    name: "confess-csp-api",
    apply: "build",
    transformIndexHtml: () => [{
      tag: "meta",
      attrs: { "http-equiv": "Content-Security-Policy", content: `connect-src 'self' ${origin}` },
      injectTo: "head-prepend",
    }],
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), serviceWorker(), pinApi(apiOrigin(mode))],
  build: {
    target: "es2022",
    sourcemap: false,          // don't publish source maps
    assetsInlineLimit: 0,      // every asset is a real file — nothing inlined as data: URLs (strict CSP)
  },
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
}));
