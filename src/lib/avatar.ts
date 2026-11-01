/**
 * Designed avatars — a port of the Android AvatarSpec + AvatarRenderer.
 *
 * Stored in the same `avatar` field as auras: "av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5" — one letter
 * per part, one number per choice. The server checks exactly these keys and ranges
 * (app/schemas/user.py AVATAR_PARTS). Illustrated and non-photographic on purpose.
 *
 * `avatarShapes()` turns a spec into plain SVG shapes in a 100 × 100 box (the Java geometry,
 * unchanged), so it is crisp at any size and testable without a browser.
 */
import { AURAS } from "./auras.ts";
import { type Argb, hex, mix, svg, withAlpha } from "./color.ts";

export const PREFIX = "av1:";

export type PartKey = "skin" | "hair" | "hairColor" | "eyes" | "brows" | "mouth" | "beard" | "glasses" | "hat" | "outfit" | "backdrop";

export interface PartDef {
  key: PartKey;
  letter: string;
  count: number;
  label: string;
  /** Shown as colour swatches rather than little avatars. */
  swatch: boolean;
}

/** Same order as AvatarSpec.Part (the encoded string follows it). */
export const PARTS: readonly PartDef[] = [
  { key: "skin", letter: "k", count: 8, label: "Skin", swatch: true },
  { key: "hair", letter: "h", count: 10, label: "Hair", swatch: false },
  { key: "hairColor", letter: "c", count: 10, label: "Hair colour", swatch: true },
  { key: "eyes", letter: "e", count: 6, label: "Eyes", swatch: false },
  { key: "brows", letter: "b", count: 4, label: "Brows", swatch: false },
  { key: "mouth", letter: "m", count: 6, label: "Mouth", swatch: false },
  { key: "beard", letter: "f", count: 5, label: "Beard", swatch: false },
  { key: "glasses", letter: "g", count: 4, label: "Glasses", swatch: false },
  { key: "hat", letter: "x", count: 5, label: "Headwear", swatch: false },
  { key: "outfit", letter: "o", count: 10, label: "Outfit", swatch: true },
  { key: "backdrop", letter: "d", count: 12, label: "Backdrop", swatch: true },
];

export type AvatarSpec = Record<PartKey, number>;

export const DEFAULT_SPEC: Readonly<AvatarSpec> = {
  skin: 2, hair: 2, hairColor: 1, eyes: 0, brows: 0, mouth: 0, beard: 0, glasses: 0, hat: 0, outfit: 0, backdrop: 3,
};

const clamp = (p: PartDef, v: number) => (Number.isInteger(v) && v >= 0 && v < p.count ? v : 0);

export function isSpec(value: string | null | undefined): boolean {
  return !!value && value.startsWith(PREFIX);
}

/** Lenient, like the app: unknown keys / bad numbers fall back to defaults. */
export function parseSpec(value: string | null | undefined): AvatarSpec {
  const s: AvatarSpec = { ...DEFAULT_SPEC };
  if (!isSpec(value)) return s;
  for (const token of value!.slice(PREFIX.length).split(".")) {
    if (token.length < 2) continue;
    const n = /^\d+$/.test(token.slice(1)) ? Number(token.slice(1)) : NaN;
    if (Number.isNaN(n)) continue;
    const p = PARTS.find((x) => x.letter === token[0]);
    if (p) s[p.key] = clamp(p, n);
  }
  return s;
}

export function encodeSpec(s: AvatarSpec): string {
  return PREFIX + PARTS.map((p) => `${p.letter}${clamp(p, s[p.key])}`).join(".");
}

export function withPart(s: AvatarSpec, key: PartKey, value: number): AvatarSpec {
  const p = PARTS.find((x) => x.key === key)!;
  return { ...s, [key]: clamp(p, value) };
}

/** A pleasant random look (no beard / glasses / hat most of the time) — AvatarSpec.random. */
export function randomSpec(rnd: () => number = Math.random): AvatarSpec {
  const int = (n: number) => Math.floor(rnd() * n);
  const s = {} as AvatarSpec;
  for (const p of PARTS) s[p.key] = int(p.count);
  if (int(3) > 0) s.beard = 0;
  if (int(3) > 0) s.glasses = 0;
  if (int(3) > 0) s.hat = 0;
  if (s.hairColor >= 7 && rnd() < 0.5) s.hairColor = int(5);
  return s;
}

// ── Palettes (index = option number) ──────────────────────────────────────────

export const SKIN: Argb[] = [0xFFFFE0C7, 0xFFF6CBA5, 0xFFE8B184, 0xFFD39A68, 0xFFB57A4E, 0xFF94603B, 0xFF6F452A, 0xFF4E301E];
export const HAIR: Argb[] = [0xFF1E1B1A, 0xFF3B2A20, 0xFF6B4423, 0xFFA5652D, 0xFFD9B26F,
  0xFFEDE0C4, 0xFF9C9AA0, 0xFFC0485A, 0xFF4A74C0, 0xFF8A63C9];
export const OUTFIT: Argb[] = [0xFFE8A04C, 0xFF8FB39A, 0xFFD2553F, 0xFF6C4F8C, 0xFF2F4A6D,
  0xFF2A2832, 0xFFEDE6DA, 0xFF3E8E8A, 0xFFD98BA3, 0xFFC9A227];

const INK = 0xFF1B1A20, LIP = 0xFF9E3B3B, TONGUE = 0xFFE07A8A, WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

/** Swatch colour for a swatch part's option (avatar studio). */
export function swatchColor(key: PartKey, i: number): string {
  const c = key === "skin" ? SKIN[i] : key === "hairColor" ? HAIR[i] : key === "outfit" ? OUTFIT[i] : hex(AURAS[i % 12]!.mid);
  return svg(c ?? INK).color;
}

// ── Shapes ────────────────────────────────────────────────────────────────────

export type Shape =
  | { el: "path"; d: string; fill?: string; fillOpacity?: number; stroke?: string; strokeOpacity?: number; width?: number }
  | { el: "circle"; cx: number; cy: number; r: number; fill?: string; fillOpacity?: number; stroke?: string; strokeOpacity?: number; width?: number }
  | { el: "ellipse"; cx: number; cy: number; rx: number; ry: number; fill: string; fillOpacity?: number }
  | { el: "rect"; x: number; y: number; w: number; h: number; rx?: number; fill?: string; fillOpacity?: number; stroke?: string; strokeOpacity?: number; width?: number }
  | { el: "line"; x1: number; y1: number; x2: number; y2: number; stroke: string; strokeOpacity?: number; width: number };

export interface AvatarDrawing {
  /** Radial backdrop: centre, radius and three stops. */
  backdrop: { cx: number; cy: number; r: number; stops: [string, string, string] };
  /** Drawn inside the portrait transform (scale 1.12 about 50,58). */
  shapes: Shape[];
}

const f = (n: number) => +n.toFixed(3);

/** Path builder with the Android Path method names. */
class P {
  private d: string[] = [];
  moveTo(x: number, y: number) { this.d.push(`M${f(x)} ${f(y)}`); return this; }
  lineTo(x: number, y: number) { this.d.push(`L${f(x)} ${f(y)}`); return this; }
  quadTo(x1: number, y1: number, x: number, y: number) { this.d.push(`Q${f(x1)} ${f(y1)} ${f(x)} ${f(y)}`); return this; }
  cubicTo(x1: number, y1: number, x2: number, y2: number, x: number, y: number) {
    this.d.push(`C${f(x1)} ${f(y1)} ${f(x2)} ${f(y2)} ${f(x)} ${f(y)}`);
    return this;
  }
  close() { this.d.push("Z"); return this; }
  toString() { return this.d.join(""); }
}

export function avatarShapes(spec: AvatarSpec): AvatarDrawing {
  const out: Shape[] = [];
  const skin = SKIN[spec.skin]!;
  const skinShade = mix(skin, BLACK, 0.14);
  const hair = HAIR[spec.hairColor]!;
  const hairShade = mix(hair, BLACK, 0.25);
  const outfit = OUTFIT[spec.outfit]!;

  const fillOf = (c: Argb) => { const s = svg(c); return s.opacity < 1 ? { fill: s.color, fillOpacity: s.opacity } : { fill: s.color }; };
  const strokeOf = (c: Argb, w: number) => {
    const s = svg(c);
    return s.opacity < 1 ? { stroke: s.color, strokeOpacity: s.opacity, width: w } : { stroke: s.color, width: w };
  };
  const path = (p: P, c: Argb) => out.push({ el: "path", d: p.toString(), ...fillOf(c) });
  const strokePath = (p: P, c: Argb, w: number) => out.push({ el: "path", d: p.toString(), fill: "none", ...strokeOf(c, w) });
  const circle = (cx: number, cy: number, rr: number, c: Argb) => out.push({ el: "circle", cx, cy, r: rr, ...fillOf(c) });
  const ring = (cx: number, cy: number, rr: number, c: Argb, w: number) => out.push({ el: "circle", cx, cy, r: rr, fill: "none", ...strokeOf(c, w) });
  const oval = (l: number, t: number, rt: number, bt: number, c: Argb) =>
    out.push({ el: "ellipse", cx: (l + rt) / 2, cy: (t + bt) / 2, rx: (rt - l) / 2, ry: (bt - t) / 2, ...fillOf(c) });
  const rect = (l: number, t: number, rt: number, bt: number, c: Argb, rx = 0) =>
    out.push({ el: "rect", x: l, y: t, w: rt - l, h: bt - t, rx: rx || undefined, ...fillOf(c) });
  const rectStroke = (l: number, t: number, rt: number, bt: number, c: Argb, w: number, rx = 0) =>
    out.push({ el: "rect", x: l, y: t, w: rt - l, h: bt - t, rx: rx || undefined, fill: "none", ...strokeOf(c, w) });
  const line = (x1: number, y1: number, x2: number, y2: number, c: Argb, w: number) => {
    const s = strokeOf(c, w);
    out.push({ el: "line", x1, y1, x2, y2, stroke: s.stroke, strokeOpacity: s.strokeOpacity, width: w });
  };

  /** Beanie / cap sit on the head: tall styles are drawn as the short cut underneath. */
  const hairStyle = (): number => {
    const h = spec.hair, hat = spec.hat;
    const covering = hat === 1 || hat === 2;
    if (covering && (h === 4 || h === 8)) return 2;
    if (hat === 1 && h === 5) return 6;
    return h;
  };

  // ── hair (back) ──
  {
    const style = hairStyle();
    const p = new P();
    switch (style) {
      case 5: circle(50, 17, 8.5, hair); break;
      case 6:
        p.moveTo(26, 44).cubicTo(24, 23, 38, 16, 50, 16).cubicTo(62, 16, 76, 23, 74, 44).lineTo(77, 82)
          .cubicTo(70, 88, 30, 88, 23, 82).close();
        path(p, hairShade);
        break;
      case 7:
        p.moveTo(25, 44).cubicTo(24, 21, 38, 15, 50, 15).cubicTo(62, 15, 76, 21, 75, 44).lineTo(75.5, 67)
          .cubicTo(70, 71, 30, 71, 24.5, 67).close();
        path(p, hairShade);
        break;
      case 9: circle(50, 39, 31, hair); break;
      default: break;
    }
  }

  // ── body ──
  rect(43.5, 62, 56.5, 82, skinShade);
  path(new P().moveTo(12, 101).cubicTo(12, 85, 28, 77, 50, 77).cubicTo(72, 77, 88, 85, 88, 101).close(), outfit);
  path(new P().moveTo(43.5, 77).lineTo(50, 85).lineTo(56.5, 77).close(), skinShade);
  const seam = mix(outfit, BLACK, 0.18);
  path(new P().moveTo(12, 101).cubicTo(12, 92, 16, 87, 22, 84).cubicTo(20, 90, 20, 96, 21, 101).close(), seam);
  path(new P().moveTo(88, 101).cubicTo(88, 92, 84, 87, 78, 84).cubicTo(80, 90, 80, 96, 79, 101).close(), seam);

  // ── ears + head ──
  circle(29.5, 50, 4.6, skinShade);
  circle(70.5, 50, 4.6, skinShade);
  oval(29, 23, 71, 72, skin);

  // ── blush ──
  const blush = withAlpha(0xFFFF7A8A, spec.skin >= 4 ? 30 : 44);
  circle(37.5, 59, 3.6, blush);
  circle(62.5, 59, 3.6, blush);

  // ── beard ──
  if (spec.beard === 1) {
    path(new P().moveTo(29.5, 52).cubicTo(30, 70, 40, 72.5, 50, 72.5).cubicTo(60, 72.5, 70, 70, 70.5, 52)
      .cubicTo(68, 63, 60, 67, 50, 67).cubicTo(40, 67, 32, 63, 29.5, 52).close(), withAlpha(hair, 70));
  } else if (spec.beard > 1) {
    if (spec.beard === 4) {
      path(new P().moveTo(29, 50).cubicTo(28.5, 72, 40, 79, 50, 79).cubicTo(60, 79, 71.5, 72, 71, 50)
        .cubicTo(69, 58, 65, 62, 61, 64.5).cubicTo(57, 61.5, 43, 61.5, 39, 64.5).cubicTo(35, 62, 31, 58, 29, 50).close(), hair);
    }
    if (spec.beard === 3) {
      path(new P().moveTo(45.6, 66.6).quadTo(50, 68.8, 54.4, 66.6).lineTo(53.4, 72.4).quadTo(50, 74.6, 46.6, 72.4).close(), hair);
    }
    path(new P().moveTo(42.6, 61).cubicTo(45.6, 58.4, 48.8, 58.8, 50, 60.1).cubicTo(51.2, 58.8, 54.4, 58.4, 57.4, 61)
      .cubicTo(54.4, 62.8, 51.4, 62, 50, 61.3).cubicTo(48.6, 62, 45.6, 62.8, 42.6, 61).close(), hair);
  }

  // ── nose ──
  strokePath(new P().moveTo(48.4, 57.2).quadTo(50, 59.2, 51.6, 57.2), skinShade, 1.4);

  // ── mouth ──
  switch (spec.mouth) {
    case 1:
      path(new P().moveTo(43.5, 61.5).quadTo(50, 70.5, 56.5, 61.5).close(), LIP);
      rect(45.5, 61.5, 54.5, 63.4, WHITE);
      break;
    case 2: line(45.6, 64, 54.4, 64, INK, 1.8); break;
    case 3: oval(47.4, 61.2, 52.6, 67.4, LIP); break;
    case 4: strokePath(new P().moveTo(45, 63.6).quadTo(51.5, 66, 56, 61.8), INK, 1.8); break;
    case 5:
      path(new P().moveTo(43.8, 61.8).quadTo(50, 69.5, 56.2, 61.8).close(), LIP);
      oval(47.2, 63.6, 52.8, 69.6, TONGUE);
      break;
    default: strokePath(new P().moveTo(44.2, 62).quadTo(50, 67.6, 55.8, 62), INK, 1.8);
  }

  // ── eyes ──
  const dotEye = (x: number, rr: number) => {
    circle(x, 50.5, rr, INK);
    circle(x + rr * 0.35, 50.5 - rr * 0.4, rr * 0.33, WHITE);
  };
  const roundEye = (x: number) => { oval(x - 4.4, 46, x + 4.4, 55, WHITE); dotEye(x, 2.5); };
  const happyEye = (x: number) => strokePath(new P().moveTo(x - 3.6, 51.5).quadTo(x, 47, x + 3.6, 51.5), INK, 1.9);
  const sleepyEye = (x: number) => strokePath(new P().moveTo(x - 3.6, 50.5).quadTo(x, 53.5, x + 3.6, 50.5), INK, 1.8);
  const lashEye = (x: number, side: number) => {
    dotEye(x, 2.9);
    const ox = x + side * 2.6;
    line(ox, 48.6, ox + side * 2.2, 46.6, INK, 1.3);
    line(ox - side * 0.6, 47.6, ox + side * 0.6, 45.4, INK, 1.3);
  };
  switch (spec.eyes) {
    case 1: roundEye(41); roundEye(59); break;
    case 2: happyEye(41); happyEye(59); break;
    case 3: sleepyEye(41); sleepyEye(59); break;
    case 4: dotEye(41, 2.7); happyEye(59); break;
    case 5: lashEye(41, -1); lashEye(59, 1); break;
    default: dotEye(41, 2.7); dotEye(59, 2.7);
  }

  // ── brows ──
  {
    const style = spec.brows;
    const col = mix(hairShade, INK, 0.25);
    const w = style === 1 ? 2.7 : 1.7;
    if (style === 3) {
      line(37, 42.2, 45, 44.2, col, w);
      line(55, 44.2, 63, 42.2, col, w);
    } else {
      const y = style === 2 ? 40.5 : 43.5, lift = style === 2 ? 3.2 : 2.2;
      strokePath(new P().moveTo(37, y).quadTo(41, y - lift, 45, y).moveTo(55, y).quadTo(59, y - lift, 63, y), col, w);
    }
  }

  // ── hair (front) ──
  {
    const style = hairStyle();
    const p = new P();
    let col: Argb = hair;
    let draw = true;
    switch (style) {
      case 1:
        col = withAlpha(hair, 215);
        p.moveTo(29.2, 43).cubicTo(29, 27, 39, 22, 50, 22).cubicTo(61, 22, 71, 27, 70.8, 43)
          .cubicTo(66, 35.5, 58, 33, 50, 33).cubicTo(42, 33, 34, 35.5, 29.2, 43);
        break;
      case 2:
        p.moveTo(27.5, 46).cubicTo(26, 25, 38, 18, 50, 18).cubicTo(63, 18, 74, 25, 72.5, 46)
          .cubicTo(70.5, 40, 68, 35.5, 64, 33.5).cubicTo(57, 37.5, 45, 37.5, 37, 33.5).cubicTo(32.5, 36, 29.5, 40, 27.5, 46);
        break;
      case 3:
        p.moveTo(27.5, 47).cubicTo(25.5, 24, 40, 16, 53, 17).cubicTo(66, 18, 75, 27, 72.5, 46)
          .cubicTo(70, 36, 64, 30.5, 56, 30).cubicTo(46, 29.5, 35, 35, 27.5, 47);
        break;
      case 4: {
        path(new P().moveTo(28, 45).cubicTo(27, 26, 39, 20, 50, 20).cubicTo(61, 20, 73, 26, 72, 45)
          .cubicTo(66, 36, 34, 36, 28, 45), hair);
        for (let i = 0; i <= 8; i++) {
          const ang = Math.PI + (Math.PI * i) / 8;
          circle(f(50 + Math.cos(ang) * 21.5), f(40 + Math.sin(ang) * 19.5), 6.2, hair);
        }
        draw = false;
        break;
      }
      case 5:
      case 6:
        p.moveTo(27.5, 48).cubicTo(26, 24, 40, 18, 50, 18).cubicTo(60, 18, 74, 24, 72.5, 48)
          .cubicTo(70, 36, 60, 30, 50, 28.5).cubicTo(40, 30, 30, 36, 27.5, 48);
        break;
      case 7:
        p.moveTo(27.5, 46).cubicTo(26, 24, 38, 17, 50, 17).cubicTo(62, 17, 74, 24, 72.5, 46).lineTo(72.5, 39.5)
          .cubicTo(66, 36.5, 34, 36.5, 27.5, 39.5).close();
        break;
      case 8:
        p.moveTo(28, 44).lineTo(29.5, 30).lineTo(34.5, 33).lineTo(37.5, 21).lineTo(43, 28).lineTo(48, 16.5).lineTo(53, 27)
          .lineTo(58.5, 18.5).lineTo(62, 29).lineTo(67.5, 23.5).lineTo(69, 33.5).lineTo(72.5, 31.5).lineTo(72, 44)
          .cubicTo(66, 36, 34, 36, 28, 44);
        break;
      case 9:
        p.moveTo(27.5, 46).cubicTo(26, 25, 38, 19, 50, 19).cubicTo(62, 19, 74, 25, 72.5, 46)
          .cubicTo(69, 39, 64, 36, 58, 35.5).cubicTo(54, 37.5, 46, 37.5, 42, 35.5).cubicTo(36, 36, 31, 39, 27.5, 46);
        break;
      default:
        draw = false;                                                       // bald
    }
    if (draw) path(p.close(), col);
  }

  // ── hats ──
  switch (spec.hat) {
    case 0: break;
    case 1: {
      const col = 0xFFD2553F;
      path(new P().moveTo(26, 39).cubicTo(26, 13, 74, 13, 74, 39).close(), col);
      rect(24.5, 34.5, 75.5, 42.5, mix(col, BLACK, 0.2), 4);
      circle(50, 13.5, 4.4, 0xFFEDE6DA);
      break;
    }
    case 2: {
      const col = 0xFF2F4A6D;
      path(new P().moveTo(27, 39).cubicTo(27, 15, 73, 15, 73, 39).close(), col);
      path(new P().moveTo(48, 38).lineTo(80, 39.5).cubicTo(84, 41.5, 81, 44.5, 75, 44).lineTo(48, 41.5).close(), mix(col, BLACK, 0.25));
      circle(50, 17.6, 1.8, 0xFFE8A04C);
      break;
    }
    case 3: {
      // drawArc(RectF(25,19,75,69), 190°, 160°): centre (50,44), r 25, clockwise.
      const rad = (deg: number) => (deg * Math.PI) / 180;
      const sx = 50 + 25 * Math.cos(rad(190)), sy = 44 + 25 * Math.sin(rad(190));
      const ex = 50 + 25 * Math.cos(rad(350)), ey = 44 + 25 * Math.sin(rad(350));
      out.push({ el: "path", d: `M${f(sx)} ${f(sy)}A25 25 0 0 1 ${f(ex)} ${f(ey)}`, fill: "none", stroke: "#2a2832", width: 3.6 });
      rect(22, 41, 31, 58, 0xFF2A2832, 4);
      rect(69, 41, 78, 58, 0xFF2A2832, 4);
      rect(23.5, 44, 29.5, 55, 0xFFE8A04C, 3);
      rect(70.5, 44, 76.5, 55, 0xFFE8A04C, 3);
      break;
    }
    default: {
      path(new P().moveTo(28, 37).cubicTo(36, 30, 64, 30, 72, 37).lineTo(72.5, 40.5).cubicTo(64, 34, 36, 34, 27.5, 40.5).close(), 0xFF8FB39A);
      for (let i = 0; i < 5; i++) {
        const ang = (Math.PI * 2 * i) / 5;
        circle(f(35 + Math.cos(ang) * 2.6), f(33.5 + Math.sin(ang) * 2.6), 2.2, 0xFFF6C76A);
      }
      circle(35, 33.5, 1.6, 0xFFD2553F);
    }
  }

  // ── glasses ──
  if (spec.glasses === 3) {
    rect(33.5, 45.5, 47.5, 55.5, 0xF0141318, 4);
    rect(52.5, 45.5, 66.5, 55.5, 0xF0141318, 4);
    line(47.5, 48, 52.5, 48, 0xFF141318, 1.6);
    rect(35.5, 47, 40, 49, withAlpha(WHITE, 90), 1);
    rect(54.5, 47, 59, 49, withAlpha(WHITE, 90), 1);
  } else if (spec.glasses > 0) {
    if (spec.glasses === 1) {
      ring(41, 50.5, 6, INK, 1.5);
      ring(59, 50.5, 6, INK, 1.5);
    } else {
      rectStroke(34.2, 45.5, 47.6, 55.5, INK, 1.5, 2.5);
      rectStroke(52.4, 45.5, 65.8, 55.5, INK, 1.5, 2.5);
    }
    const l = spec.glasses === 1 ? 47 : 47.6, rt = spec.glasses === 1 ? 53 : 52.4;
    strokePath(new P().moveTo(l, 50).quadTo(50, 48, rt, 50), INK, 1.5);
  }

  const aura = AURAS[spec.backdrop % AURAS.length]!;
  return {
    backdrop: { cx: 50 + aura.fx * 50, cy: 50 + aura.fy * 50, r: 62, stops: [aura.glow, aura.mid, aura.base] },
    shapes: out,
  };
}
