/**
 * The screen map: which iPhone / iPad this is, how big its screen really is, and how the app
 * should size itself on it. Measured on this device, kept in this installed app's own storage
 * (localStorage of the Home Screen app — never sent anywhere), and applied at start-up before the
 * first frame, then re-checked on every rotation, Split View resize and keyboard close.
 *
 * Two things it fixes:
 *  1. The bottom gap. Some iOS versions give a Home Screen app a layout viewport that's shorter
 *     than the screen by the status-bar height (62 pt on an iPhone 16 Pro), so anything sized to
 *     the viewport or pinned to `bottom: 0` stops above a black bar. We measure the real screen,
 *     set --app-h to it, and pull bottom-pinned layers down by --gap (see ui/screen.css).
 *  2. iPad blank space. On an iPad the app is drawn larger (a UI scale per screen size, through the
 *     viewport meta, so touch and layout stay in plain CSS px) so the phone layout fills the screen
 *     instead of floating in the middle. Split View windows fall back towards 1×.
 */

export type DeviceClass = "phone" | "tablet" | "other";

/** Screen sizes in points, portrait; safe areas in portrait. */
export interface DeviceSpec { name: string; w: number; h: number; dpr: number; cls: DeviceClass; top: number; bottom: number }

export const SCREEN_MAP: DeviceSpec[] = [
  // iPhones
  { name: "iPhone 17 Pro Max · 16 Pro Max", w: 440, h: 956, dpr: 3, cls: "phone", top: 62, bottom: 34 },
  { name: "iPhone Air", w: 420, h: 912, dpr: 3, cls: "phone", top: 68, bottom: 34 },
  { name: "iPhone 17 · 17 Pro · 16 Pro", w: 402, h: 874, dpr: 3, cls: "phone", top: 62, bottom: 34 },
  { name: "iPhone 16 Plus · 15 Plus · 15 Pro Max · 14 Pro Max", w: 430, h: 932, dpr: 3, cls: "phone", top: 59, bottom: 34 },
  { name: "iPhone 16 · 15 · 15 Pro · 14 Pro", w: 393, h: 852, dpr: 3, cls: "phone", top: 59, bottom: 34 },
  { name: "iPhone 14 Plus · 13 Pro Max · 12 Pro Max", w: 428, h: 926, dpr: 3, cls: "phone", top: 47, bottom: 34 },
  { name: "iPhone 16e · 14 · 13 · 13 Pro · 12 · 12 Pro", w: 390, h: 844, dpr: 3, cls: "phone", top: 47, bottom: 34 },
  { name: "iPhone 13 mini · 12 mini", w: 360, h: 780, dpr: 3, cls: "phone", top: 50, bottom: 34 },
  { name: "iPhone 11 Pro · XS · X", w: 375, h: 812, dpr: 3, cls: "phone", top: 44, bottom: 34 },
  { name: "iPhone 11 Pro Max · XS Max", w: 414, h: 896, dpr: 3, cls: "phone", top: 44, bottom: 34 },
  { name: "iPhone 11 · XR", w: 414, h: 896, dpr: 2, cls: "phone", top: 48, bottom: 34 },
  { name: "iPhone 8 Plus · 7 Plus", w: 414, h: 736, dpr: 3, cls: "phone", top: 20, bottom: 0 },
  { name: "iPhone SE (2nd/3rd) · 8 · 7", w: 375, h: 667, dpr: 2, cls: "phone", top: 20, bottom: 0 },
  { name: "iPhone SE (1st)", w: 320, h: 568, dpr: 2, cls: "phone", top: 20, bottom: 0 },
  // iPads
  { name: "iPad Pro 13 (M4/M5)", w: 1032, h: 1376, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad Pro 12.9 · iPad Air 13", w: 1024, h: 1366, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad Pro 11 (M4/M5)", w: 834, h: 1210, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad Pro 11 (1st–4th)", w: 834, h: 1194, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad Air 11 · iPad Air 4/5 · iPad 10th/A16", w: 820, h: 1180, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad mini 6/7", w: 744, h: 1133, dpr: 2, cls: "tablet", top: 24, bottom: 20 },
  { name: "iPad Air 3 · Pro 10.5", w: 834, h: 1112, dpr: 2, cls: "tablet", top: 20, bottom: 0 },
  { name: "iPad 7th–9th", w: 810, h: 1080, dpr: 2, cls: "tablet", top: 20, bottom: 0 },
  { name: "iPad mini 5 · iPad 9.7", w: 768, h: 1024, dpr: 2, cls: "tablet", top: 20, bottom: 0 },
];

/** iPad: the phone layout drawn larger so it fills the screen. Reference: a 640-pt-wide column. */
const TABLET_REF = 640;
const TABLET_MAX_SCALE = 1.4;
/** Never lay out narrower than a small phone (Split View). */
const MIN_LOGICAL = 360;

export interface ScreenProfile {
  v: 1;
  device: string | null;
  cls: DeviceClass;
  /** Portrait screen size in points. */
  screenW: number;
  screenH: number;
  dpr: number;
  ios: boolean;
  standalone: boolean;
  orientation: "portrait" | "landscape";
  /** This window in points (smaller than the screen in Split View / Stage Manager). */
  windowW: number;
  windowH: number;
  /** UI scale (1 on phones). */
  scale: number;
  /** Points of screen below the layout viewport (the iOS bottom-gap bug); 0 when all is well. */
  gap: number;
  /** Measured safe areas, points. */
  safe: { top: number; bottom: number; left: number; right: number };
  at: number;
}

const KEY = "confess.screen";
const UA = typeof navigator !== "undefined" ? navigator.userAgent : "";
const IOS = /iPhone|iPad|iPod/.test(UA) || (/Macintosh/.test(UA) && typeof navigator !== "undefined" && navigator.maxTouchPoints > 1);

function standalone(): boolean {
  return typeof window !== "undefined" && (window.matchMedia?.("(display-mode: standalone)").matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true);
}

/** Which device in the map, if any (iOS reports the screen in portrait points in Safari). */
export function lookup(w: number, h: number, dpr: number): DeviceSpec | null {
  const sw = Math.min(w, h), sh = Math.max(w, h);
  const hits = SCREEN_MAP.filter((d) => d.w === sw && d.h === sh);
  return hits.find((d) => d.dpr === Math.round(dpr)) ?? hits[0] ?? null;
}

// ── Stored profile (per orientation, so a cold start is right before anything is measured) ──

type Stored = { v: 1; profile: ScreenProfile; byOrientation: Partial<Record<"portrait" | "landscape", { gap: number; safe: ScreenProfile["safe"] }>> };

function readStored(): Stored | null {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) ?? "null") as Stored | null;
    return s && s.v === 1 && s.profile ? s : null;
  } catch {
    return null;
  }
}

function store(p: ScreenProfile): void {
  try {
    const old = readStored();
    const byOrientation = { ...(old?.byOrientation ?? {}), [p.orientation]: { gap: p.gap, safe: p.safe } };
    localStorage.setItem(KEY, JSON.stringify({ v: 1, profile: p, byOrientation } satisfies Stored));
  } catch {
    /* private mode: just not remembered */
  }
}

// ── Measuring ──

let probe: HTMLDivElement | null = null;
function safeArea(): ScreenProfile["safe"] {
  if (typeof document === "undefined" || !document.body) return { top: 0, bottom: 0, left: 0, right: 0 };
  if (!probe) {
    probe = document.createElement("div");
    probe.setAttribute("aria-hidden", "true");
    probe.className = "screen-probe";
    document.body.appendChild(probe);
  }
  const cs = getComputedStyle(probe);
  return { top: parseFloat(cs.paddingTop) || 0, bottom: parseFloat(cs.paddingBottom) || 0,
           left: parseFloat(cs.paddingLeft) || 0, right: parseFloat(cs.paddingRight) || 0 };
}

const orientationNow = (): "portrait" | "landscape" =>
  (typeof matchMedia === "function" && matchMedia("(orientation: landscape)").matches) ? "landscape" : "portrait";

/** The current page scale (what our viewport meta asked for). */
const pageScale = () => window.visualViewport?.scale ?? 1;

export function measure(): ScreenProfile {
  const s = pageScale();
  const sw = Math.min(screen.width, screen.height), sh = Math.max(screen.width, screen.height);
  const dpr = window.devicePixelRatio || 1;
  const spec = IOS ? lookup(sw, sh, dpr) : null;
  const orientation = orientationNow();
  const cls: DeviceClass = spec?.cls ?? (IOS ? (sw >= 700 ? "tablet" : "phone") : "other");
  // This window in points: the visual viewport times the zoom we set.
  const vv = window.visualViewport;
  const windowW = Math.round((vv?.width ?? window.innerWidth) * (vv ? s : 1));
  const layoutH = window.innerHeight * s;
  const safeCss = safeArea();
  const safe = { top: safeCss.top * s, bottom: safeCss.bottom * s, left: safeCss.left * s, right: safeCss.right * s };
  const expectedH = orientation === "portrait" ? sh : sw;
  const sa = standalone();
  let gap = 0;
  if (IOS && sa) {
    const missing = expectedH - layoutH;
    // An iPhone app always fills the screen; an iPad window only counts when the gap is the bug's
    // signature (the status-bar height), not a Split View / Stage Manager window.
    const topRef = safe.top || spec?.top || 0;
    if (missing > 1 && missing <= 120 && (cls === "phone" || Math.abs(missing - topRef) <= 3)) gap = Math.round(missing);
  }
  return {
    v: 1, device: spec?.name ?? null, cls, screenW: sw, screenH: sh, dpr, ios: IOS, standalone: sa, orientation,
    windowW, windowH: Math.round(layoutH + gap), scale: scaleFor(cls, sw, windowW), gap, safe, at: Date.now(),
  };
}

/** iPad: draw the app larger, about the same physical size as on a big phone held closer. */
export function scaleFor(cls: DeviceClass, shortSide: number, windowW: number): number {
  if (cls !== "tablet") return 1;
  const base = Math.min(TABLET_MAX_SCALE, Math.max(1, shortSide / TABLET_REF));
  const fit = Math.max(1, windowW / MIN_LOGICAL);
  return Math.round(Math.min(base, fit) * 100) / 100;
}

// ── Applying ──

const DEFAULT_VIEWPORT = "width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover";

function setViewport(scale: number, windowW: number): void {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="viewport"]');
  if (!meta) return;
  const content = scale === 1
    ? DEFAULT_VIEWPORT
    : `width=${Math.round(windowW / scale)}, initial-scale=${scale}, minimum-scale=${scale}, maximum-scale=${scale}, viewport-fit=cover`;
  if (meta.content !== content) meta.content = content;
}

function setVars(p: Pick<ScreenProfile, "gap" | "scale" | "cls" | "orientation" | "windowH">): void {
  const r = document.documentElement;
  const gapCss = p.gap / p.scale;
  r.style.setProperty("--gap", `${gapCss}px`);
  r.style.setProperty("--ui-scale", String(p.scale));
  // The real height of this window: the layout viewport plus whatever the bug cut off.
  if (p.gap > 0) r.style.setProperty("--app-h", `${Math.round(window.innerHeight + gapCss)}px`);
  else r.style.removeProperty("--app-h");
  r.dataset.device = p.cls;
  r.dataset.orientation = p.orientation;
  r.classList.toggle("has-gap", p.gap > 0);
}

let current: ScreenProfile | null = null;
const listeners = new Set<(p: ScreenProfile) => void>();

export function screenProfile(): ScreenProfile | null {
  return current;
}

/** Measure again now (the screen check page). */
export function remeasure(): void {
  update();
}

/** Forget what this device saved (the screen check page). */
export function forgetScreen(): void {
  try { localStorage.removeItem(KEY); } catch { /* */ }
}

export function storedScreen(): unknown {
  return readStored();
}

export function onScreenChange(l: (p: ScreenProfile) => void): () => void {
  listeners.add(l);
  return () => { listeners.delete(l); };
}

function update(): void {
  const p = measure();
  setViewport(p.scale, p.windowW);
  // The new viewport changes innerHeight: measure the gap again once it has settled.
  requestAnimationFrame(() => {
    const q = { ...measure(), scale: p.scale };
    setVars(q);
    current = q;
    store(q);
    listeners.forEach((l) => l(q));
  });
}

/**
 * Start-up: apply what this device measured last time (no first-frame jump), then measure for
 * real and keep measuring when the window changes.
 */
export function installScreenMap(): void {
  if (typeof window === "undefined") return;
  const stored = readStored();
  if (stored) {
    const o = orientationNow();
    const known = stored.byOrientation[o];
    const p = stored.profile;
    const scale = scaleFor(p.cls, Math.min(screen.width, screen.height), Math.round(window.innerWidth));
    setViewport(scale, Math.round(window.innerWidth));
    setVars({ gap: known?.gap ?? 0, scale, cls: p.cls, orientation: o, windowH: p.windowH });
    current = { ...p, orientation: o, scale, gap: known?.gap ?? 0 };
  }
  let t = 0;
  const later = () => { window.clearTimeout(t); t = window.setTimeout(update, 120); };
  window.addEventListener("resize", later);
  window.addEventListener("orientationchange", later);
  // iOS can open the gap after the keyboard closes: re-check whenever the keyboard goes away.
  window.visualViewport?.addEventListener("resize", () => {
    if (!document.documentElement.classList.contains("kb-open")) later();
  });
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") later(); });
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", update, { once: true });
  else update();
}
