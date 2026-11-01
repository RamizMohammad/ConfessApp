/**
 * Notifications on this iPhone (web push).
 *
 * iOS only allows them for a Home Screen app (iOS 16.4+), and only after a tap — so permission
 * is asked from a button, never on load. The subscription (endpoint + the browser's public
 * keys) goes to the server, which encrypts each push for this device alone. Nothing secret is
 * kept here: the VAPID public key comes from the server, the private one never leaves it.
 *
 * "Private" (default on): the lock screen shows only "Confess · Something new is waiting for you".
 */
import { api } from "./api.ts";
import { isIOS, isStandalone } from "./device.ts";

const ON = "confess.push_on";
const PRIVATE = "confess.push_private";
const ASKED = "confess.push_asked";

function flag(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function setFlag(key: string, v: string | null): void {
  try {
    if (v === null) localStorage.removeItem(key);
    else localStorage.setItem(key, v);
  } catch {
    /* storage blocked */
  }
}

export type PushState =
  | "unsupported"        // this browser can't do web push at all
  | "install"            // iPhone in Safari: add to Home Screen first
  | "unavailable"        // the server has no VAPID key yet
  | "denied"             // blocked in iPhone Settings
  | "off"
  | "on";

export function browserSupportsPush(): boolean {
  return typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

let info: Promise<{ available: boolean; publicKey: string | null }> | null = null;
function serverInfo() {
  if (!info) info = api<{ available: boolean; publicKey: string | null }>("/push/web").catch((e) => { info = null; throw e; });
  return info;
}

export function base64UrlToBytes(s: string): Uint8Array<ArrayBuffer> {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4);
  const raw = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

const sameKey = (a: ArrayBuffer | null | undefined, b: Uint8Array) =>
  !!a && a.byteLength === b.length && new Uint8Array(a).every((x, i) => x === b[i]);

async function registration(): Promise<ServiceWorkerRegistration | null> {
  if (!browserSupportsPush()) return null;
  const reg = await navigator.serviceWorker.getRegistration("/");
  return reg ?? null;
}

export async function pushState(): Promise<PushState> {
  if (!browserSupportsPush()) return isIOS && !isStandalone() ? "install" : "unsupported";
  if (isIOS && !isStandalone()) return "install";
  try {
    if (!(await serverInfo()).available) return "unavailable";
  } catch {
    return "unavailable";
  }
  if (Notification.permission === "denied") return "denied";
  const reg = await registration();
  const sub = await reg?.pushManager.getSubscription();
  return sub && Notification.permission === "granted" && flag(ON) === "1" ? "on" : "off";
}

export function isPrivate(): boolean {
  return flag(PRIVATE) !== "0";
}

async function send(sub: PushSubscription, priv: boolean): Promise<void> {
  const json = sub.toJSON();
  await api("/push/web/subscribe", { method: "POST", body: { endpoint: json.endpoint, keys: json.keys, private: priv } });
}

/** From a tap: ask iOS, subscribe, tell the server. Returns the new state. */
export async function enablePush(priv = isPrivate()): Promise<PushState> {
  setFlag(ASKED, "1");
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return permission === "denied" ? "denied" : "off";
  const { publicKey } = await serverInfo();
  if (!publicKey) return "unavailable";
  const reg = await navigator.serviceWorker.ready;
  const key = base64UrlToBytes(publicKey);
  let sub = await reg.pushManager.getSubscription();
  if (sub && !sameKey(sub.options.applicationServerKey, key)) {
    await sub.unsubscribe();                             // the server's key changed
    sub = null;
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  await send(sub, priv);
  setFlag(ON, "1");
  setFlag(PRIVATE, priv ? "1" : "0");
  return "on";
}

export async function setPrivate(priv: boolean): Promise<void> {
  const sub = await (await registration())?.pushManager.getSubscription();
  if (sub) await send(sub, priv);
  setFlag(PRIVATE, priv ? "1" : "0");
}

/** Off on this iPhone (also used on sign-out, so the next account doesn't inherit it). */
export async function disablePush(tellServer = true): Promise<void> {
  setFlag(ON, null);
  const sub = await (await registration())?.pushManager.getSubscription().catch(() => null);
  if (!sub) return;
  if (tellServer) await api("/push/web/unsubscribe", { method: "POST", body: { endpoint: sub.endpoint } }).catch(() => undefined);
  await sub.unsubscribe().catch(() => undefined);
}

export async function testPush(): Promise<boolean> {
  const sub = await (await registration())?.pushManager.getSubscription();
  if (!sub) return false;
  return (await api<{ sent: boolean }>("/push/web/test", { method: "POST", body: { endpoint: sub.endpoint } })).sent;
}

/**
 * On every start: if notifications are on here, re-send the subscription — it ties it to this
 * session again (sessions change at sign-in) and picks up a rotated server key.
 */
export async function resyncPush(): Promise<void> {
  try {
    if (flag(ON) !== "1" || !browserSupportsPush() || Notification.permission !== "granted") return;
    const { available, publicKey } = await serverInfo();
    if (!available || !publicKey) return;
    const reg = await navigator.serviceWorker.ready;
    const key = base64UrlToBytes(publicKey);
    let sub = await reg.pushManager.getSubscription();
    if (sub && !sameKey(sub.options.applicationServerKey, key)) {
      await sub.unsubscribe();
      sub = null;
    }
    sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
    await send(sub, isPrivate());
  } catch {
    /* try again next start */
  }
}

/** The one-time "Know when your words land" card: installed, possible, never asked. */
export async function shouldOfferPush(): Promise<boolean> {
  if (flag(ASKED) === "1" || !isStandalone()) return false;
  return (await pushState()) === "off" && Notification.permission === "default";
}

export function dismissPushOffer(): void {
  setFlag(ASKED, "1");
}

/** Home Screen icon badge = unread activity (iOS 16.4+, with notifications allowed). */
export function setAppBadge(n: number): void {
  const nav = navigator as Navigator & { setAppBadge?: (n?: number) => Promise<void>; clearAppBadge?: () => Promise<void> };
  try {
    if (n > 0) void nav.setAppBadge?.(n).catch(() => undefined);
    else void nav.clearAppBadge?.().catch(() => undefined);
  } catch {
    /* not supported */
  }
}
