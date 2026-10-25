// Turns vercel.json's security headers into nginx `add_header` lines, so Docker and Vercel
// always send the same headers (one source of truth). Run at image build time.
//
// No API address lives here: the header allows `connect-src 'self' https:` and the page pins the
// exact API from VITE_API_BASE (vite.config.ts). For a plain-http local API (testing only) its
// origin is allowed too, and HSTS / upgrade-insecure-requests are left out.
import { readFileSync } from "node:fs";

const vercel = JSON.parse(readFileSync(new URL("../vercel.json", import.meta.url), "utf8"));
const block = vercel.headers.find((h) => h.source === "/(.*)");
if (!block) throw new Error("vercel.json: no '/(.*)' header block");

const api = (process.env.VITE_API_BASE || "").trim();
if (!api) throw new Error("VITE_API_BASE is not set");
const origin = new URL(api).origin;
const insecure = origin.startsWith("http://");

const lines = ["# Generated from vercel.json by docker/nginx-headers.mjs — don't edit by hand."];
for (const { key, value } of block.headers) {
  if (insecure && key === "Strict-Transport-Security") continue;
  let v = value;
  if (key === "Content-Security-Policy" && insecure) {
    v = v.replace("connect-src 'self' https:", `connect-src 'self' https: ${origin}`).replace(/;\s*upgrade-insecure-requests/, "");
  }
  if (/["\\\n]/.test(v)) throw new Error(`header ${key} has characters nginx can't take`);
  lines.push(`add_header ${key} "${v}" always;`);
}
process.stdout.write(lines.join("\n") + "\n");
