/**
 * The reader's look, as Android keeps it (Settings → Appearance):
 *   PaperTone  — paper colour (cream → night) and the ink on it (auto or a fixed blend)
 *   CardText   — confession size / line spacing / font on the cards
 *   NavBar     — bottom-bar order, labels (always · selected · never), animated icons
 * Kept on this device only (localStorage, like SharedPreferences) and applied as CSS variables
 * on <html>, so every card and the bar follow at once. The Settings page for it lands in R7.
 */
import { useEffect, useState } from "react";

export type NavTab = "feed" | "search" | "books" | "me";
export const NAV_DEFAULT: NavTab[] = ["feed", "search", "books", "me"];
export const LABELS_ALWAYS = 0, LABELS_SELECTED = 1, LABELS_NEVER = 2;

/** ReadingFonts.java — id → CSS family and the size scale (handwriting reads small). */
export const READING_FONTS: { family: string; scale: number }[] = [
  { family: '"Newsreader", Georgia, serif', scale: 1 },               // 0 NEWSREADER
  { family: '"Manrope", -apple-system, system-ui, sans-serif', scale: 1 }, // 1 MANROPE (default)
  { family: '"Literata", Georgia, serif', scale: 1 },                 // 2
  { family: '"Lora", Georgia, serif', scale: 1 },                     // 3
  { family: '"Atkinson Hyperlegible", system-ui, sans-serif', scale: 1 }, // 4
  { family: '"Nunito", system-ui, sans-serif', scale: 1 },            // 5
  { family: '"Caveat", cursive', scale: 1.22 },                       // 6
  { family: '"JetBrains Mono", ui-monospace, monospace', scale: 0.92 }, // 7 TYPEWRITER
];
const LINE = [1.42, 1.59, 1.8];
export const CARD_MIN = 14, CARD_MAX = 22, CARD_DEFAULT = 18, DETAIL_EXTRA = 4;

export interface Appearance {
  paperTone: number;     // 0..100
  textTone: number;      // -1 auto, else 0..100
  cardSize: number;      // 14..22
  cardSpacing: number;   // 0 compact · 1 normal · 2 airy
  cardFont: number;      // ReadingFonts id
  navOrder: NavTab[];
  navLabels: number;
  navAnimate: boolean;
}

export const DEFAULTS: Appearance = {
  paperTone: 0, textTone: -1, cardSize: CARD_DEFAULT, cardSpacing: 1, cardFont: 1,
  navOrder: NAV_DEFAULT, navLabels: LABELS_ALWAYS, navAnimate: true,
};

const KEY = "confess.appearance";
const clamp = (v: unknown, lo: number, hi: number, d: number) =>
  typeof v === "number" && Number.isFinite(v) ? Math.max(lo, Math.min(hi, Math.round(v))) : d;

function sanitize(raw: Partial<Appearance>): Appearance {
  const order = Array.isArray(raw.navOrder) && raw.navOrder.length === 4 && NAV_DEFAULT.every((t) => raw.navOrder!.includes(t))
    ? raw.navOrder : NAV_DEFAULT;
  return {
    paperTone: clamp(raw.paperTone, 0, 100, 0),
    textTone: raw.textTone === -1 ? -1 : clamp(raw.textTone, 0, 100, -1),
    cardSize: clamp(raw.cardSize, CARD_MIN, CARD_MAX, CARD_DEFAULT),
    cardSpacing: clamp(raw.cardSpacing, 0, 2, 1),
    cardFont: clamp(raw.cardFont, 0, 7, 1),
    navOrder: order,
    navLabels: clamp(raw.navLabels, 0, 2, 0),
    navAnimate: raw.navAnimate !== false,
  };
}

let current: Appearance = load();
const listeners = new Set<(a: Appearance) => void>();

function load(): Appearance {
  try {
    const s = localStorage.getItem(KEY);
    return s ? sanitize(JSON.parse(s) as Partial<Appearance>) : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

export function appearance(): Appearance {
  return current;
}

export function setAppearance(patch: Partial<Appearance>): void {
  current = sanitize({ ...current, ...patch });
  try { localStorage.setItem(KEY, JSON.stringify(current)); } catch { /* private mode */ }
  applyAppearance();
  listeners.forEach((l) => l(current));
}

export function useAppearance(): Appearance {
  const [a, setA] = useState(current);
  useEffect(() => {
    listeners.add(setA);
    setA(current);
    return () => { listeners.delete(setA); };
  }, []);
  return a;
}

// ── PaperTone colour maths (androidx ColorUtils) ─────────────────────────────

type RGB = [number, number, number];
const PAPER: RGB = [0xED, 0xE6, 0xDA], PAPER_INK: RGB = [0x1B, 0x1A, 0x20];
const NIGHT: RGB = [0x3E, 0x3C, 0x39], CREAM: RGB = [0xEF, 0xE9, 0xDF];

const blend = (a: RGB, b: RGB, f: number): RGB => [0, 1, 2].map((i) => Math.round(a[i]! + (b[i]! - a[i]!) * f)) as RGB;
function luminance(c: RGB): number {
  const [r, g, b] = c.map((v) => { const s = v / 255; return s < 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; });
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}
const contrast = (a: RGB, b: RGB) => { const x = luminance(a) + 0.05, y = luminance(b) + 0.05; return Math.max(x, y) / Math.min(x, y); };
const hex = (c: RGB) => `#${c.map((v) => v.toString(16).padStart(2, "0")).join("")}`;
const rgba = (c: RGB, a: number) => `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${(a / 255).toFixed(3)})`;

/** PaperTone.paper / ink / meta / rule for these settings. */
export function paperColours(a: Appearance = current): { paper: string; ink: string; meta: string; rule: string; dark: boolean } {
  const paper = blend(PAPER, NIGHT, a.paperTone / 100);
  const ink = a.textTone === -1
    ? (contrast(PAPER_INK, paper) >= contrast(CREAM, paper) ? PAPER_INK : CREAM)
    : blend(PAPER_INK, CREAM, a.textTone / 100);
  return { paper: hex(paper), ink: hex(ink), meta: rgba(ink, 0xA6), rule: rgba(ink, 0x26), dark: luminance(paper) < 0.3 };
}

/** CardText.apply: font, size (+4 on the confession screen) and line height. */
export function cardText(a: Appearance = current, detail = false): { family: string; size: number; line: number } {
  const f = READING_FONTS[a.cardFont] ?? READING_FONTS[1]!;
  const size = (a.cardSize + (detail ? DETAIL_EXTRA : 0)) * f.scale;
  return { family: f.family, size, line: Math.round(size * LINE[a.cardSpacing]!) };
}

/** Sets the CSS variables the cards and the bar read. Called at start-up and on every change. */
export function applyAppearance(): void {
  if (typeof document === "undefined") return;
  const s = document.documentElement.style;
  const c = paperColours();
  s.setProperty("--tone-paper", c.paper);
  s.setProperty("--tone-ink", c.ink);
  s.setProperty("--tone-meta", c.meta);
  s.setProperty("--tone-rule", c.rule);
  const t = cardText(), d = cardText(current, true);
  s.setProperty("--card-font", t.family);
  s.setProperty("--card-size", `${t.size}px`);
  s.setProperty("--card-line", `${t.line}px`);
  s.setProperty("--card-detail-size", `${d.size}px`);
  s.setProperty("--card-detail-line", `${d.line}px`);
}

applyAppearance();
