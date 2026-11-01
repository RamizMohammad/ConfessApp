/** ARGB colour maths, the same as Android's ColorUtils / the Java views (ints in, ints out). */

export type Argb = number;

export const a = (c: Argb) => (c >>> 24) & 0xff;
export const r = (c: Argb) => (c >>> 16) & 0xff;
export const g = (c: Argb) => (c >>> 8) & 0xff;
export const b = (c: Argb) => c & 0xff;

export function argb(al: number, rd: number, gr: number, bl: number): Argb {
  const k = (v: number) => Math.max(0, Math.min(255, Math.trunc(v)));
  return ((k(al) << 24) | (k(rd) << 16) | (k(gr) << 8) | k(bl)) >>> 0;
}

/** Android #AARRGGBB literal (e.g. 0xFFE8A04C) or "#RRGGBB" → Argb. */
export function hex(s: string): Argb {
  const v = s.replace("#", "");
  return v.length === 6 ? (0xff000000 | parseInt(v, 16)) >>> 0 : parseInt(v, 16) >>> 0;
}

export function withAlpha(c: Argb, al: number): Argb {
  return argb(al, r(c), g(c), b(c));
}

/** ColorUtils.blendARGB / the views' mix(): f = 0 → a, 1 → b. */
export function mix(x: Argb, y: Argb, f: number): Argb {
  const k = 1 - f;
  return argb(a(x) * k + a(y) * f, r(x) * k + r(y) * f, g(x) * k + g(y) * f, b(x) * k + b(y) * f);
}

/** Darker by f (0 = black, 1 = unchanged), alpha kept. */
export function shade(c: Argb, f: number): Argb {
  return argb(a(c), r(c) * f, g(c) * f, b(c) * f);
}

export function lighten(c: Argb, f: number): Argb {
  return mix(c, 0xffffffff, f);
}

/** For canvas / CSS. */
export function css(c: Argb): string {
  return `rgba(${r(c)},${g(c)},${b(c)},${+(a(c) / 255).toFixed(4)})`;
}

/** For SVG attributes: colour without alpha + a separate opacity. */
export function svg(c: Argb): { color: string; opacity: number } {
  return { color: `#${(c & 0xffffff).toString(16).padStart(6, "0")}`, opacity: +(a(c) / 255).toFixed(4) };
}
