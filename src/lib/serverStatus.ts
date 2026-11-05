/**
 * Is the server up? Twin of the Android app's ServerStatus: GET /ready (Postgres + Redis
 * reachable) with a short timeout; only a 200 counts as "up". Asked through /api/v2/ready, the
 * same check on the API's own path (the web app's address only forwards /api/ to the server).
 * An older server without that path (404) counts as up, so a web deploy never locks people out.
 */
import { config } from "../config.ts";

const TIMEOUT_MS = 4000;

/** No network on the device at all → not the server's fault: don't show maintenance. */
export function isDeviceOffline(): boolean {
  return typeof navigator !== "undefined" && navigator.onLine === false;
}

export async function serverUp(signal?: AbortSignal): Promise<boolean> {
  if (!config.apiBase) return true;
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), TIMEOUT_MS);
  signal?.addEventListener("abort", () => ctl.abort());
  try {
    const r = await fetch(`${config.apiBase}/api/v2/ready`, {
      method: "GET", cache: "no-store", credentials: "omit", signal: ctl.signal,
      headers: { Accept: "application/json" },
    });
    return r.status === 200 || r.status === 404;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}
