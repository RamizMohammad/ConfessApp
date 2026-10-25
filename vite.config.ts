import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { defineConfig, type Plugin } from "vite";
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

export default defineConfig({
  plugins: [react(), serviceWorker()],
  build: {
    target: "es2022",
    sourcemap: false,          // don't publish source maps
    assetsInlineLimit: 0,      // every asset is a real file — nothing inlined as data: URLs (strict CSP)
  },
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
});
