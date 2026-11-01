/**
 * Profile banners — a port of the Android Banner + BannerView.
 *
 * Server value "bn1:p<pattern>.c<colour>" (users.banner); "" = automatic: the avatar's colours and
 * a pattern picked from the alias, so every profile has one before anyone chooses. Always drawn,
 * never an image — nothing to upload or moderate. Shapes come from java.util.Random with the
 * Android seed, so a banner looks the same on a phone and here.
 */
import { AURAS, type AuraDef, resolveAura } from "./auras.ts";
import { type Argb, css, hex, lighten, mix, shade, withAlpha } from "./color.ts";
import { floorMod, JavaRandom, javaHash } from "./javaRandom.ts";

export const PATTERNS = [
  "Glow", "Stars", "Waves", "Letter paper", "Letters", "Moon", "Rain", "Constellation", "Dunes", "Bokeh",
] as const;
export const GLOW = 0, STARS = 1, WAVES = 2, RULED = 3, LETTERS = 4, MOON = 5, RAIN = 6, CONSTELLATION = 7, DUNES = 8, BOKEH = 9;

export interface Banner {
  pattern: number;
  colour: number;            // index into AURAS (= Aura ordinal on Android)
  automatic: boolean;
}

const SPEC = /^bn1:p([0-9])\.c([0-9]|1[01])$/;

export function resolveBanner(spec: string | null | undefined, avatar: string | null | undefined, alias: string | null | undefined): Banner {
  const m = SPEC.exec((spec ?? "").trim());
  if (m) return { pattern: Number(m[1]), colour: Number(m[2]), automatic: false };
  return automaticBanner(avatar, alias);
}

export function automaticBanner(avatar: string | null | undefined, alias: string | null | undefined): Banner {
  const h = alias ? javaHash(alias.toLowerCase()) : 0;
  const aura = resolveAura(avatar);
  return { pattern: floorMod(h, PATTERNS.length), colour: AURAS.findIndex((a) => a.id === aura.id), automatic: true };
}

/** The value saved on the server ("" for automatic). */
export function bannerSpec(b: Banner): string {
  return b.automatic ? "" : `bn1:p${b.pattern}.c${b.colour}`;
}

// ── Drawing ───────────────────────────────────────────────────────────────────

export const LOOP_MS = 14_000;
const INK = hex("#0F0E13");

interface Pieces { ex: number[]; ey: number[]; es: number[]; ea: number[]; ep: number[]; linkA: number[]; linkB: number[] }

function count(pattern: number): number {
  return [0, 120, 5, 3, 16, 60, 90, 16, 4, 20][pattern] ?? 0;
}

const piecesCache = new Map<number, Pieces>();

/** Pattern pieces in fractions of the view — BannerView.build(). */
export function pieces(pattern: number): Pieces {
  const hit = piecesCache.get(pattern);
  if (hit) return hit;
  const n = count(pattern);
  const rnd = new JavaRandom(7919 * (pattern + 1));
  const p: Pieces = { ex: [], ey: [], es: [], ea: [], ep: [], linkA: [], linkB: [] };
  for (let i = 0; i < n; i++) {
    p.ex.push(rnd.nextFloat());
    p.ey.push(rnd.nextFloat());
    p.es.push(rnd.nextFloat());
    p.ea.push(rnd.nextFloat());
    p.ep.push(rnd.nextFloat());
  }
  if (pattern === CONSTELLATION) {
    // Math.fround: the Java code does this in float.
    for (let i = 0; i < n; i++) {
      p.ex[i] = Math.fround(0.05 + Math.fround(p.ex[i]! * 0.9));
      p.ey[i] = Math.fround(0.12 + Math.fround(p.ey[i]! * 0.6));
    }
    for (let i = 0; i < n; i++) {
      let best = -1, bd = Number.MAX_VALUE;
      for (let j = 0; j < n; j++) {
        if (j === i) continue;
        const dx = (p.ex[i]! - p.ex[j]!) * 2.4, dy = p.ey[i]! - p.ey[j]!;
        const d = dx * dx + dy * dy;
        if (d < bd) { bd = d; best = j; }
      }
      if (best >= 0 && bd < 0.12) { p.linkA.push(i); p.linkB.push(best); }
    }
  }
  piecesCache.set(pattern, p);
  return p;
}

export interface DrawOptions {
  /** 0…1 through the 14 s loop; ignored when not moving. */
  t: number;
  /** Twinkle / drift on (only while the loop runs, as on Android). */
  moving: boolean;
  /** Fade the bottom into the page (profiles). */
  fade: boolean;
  /** Where that fade starts, as a fraction of the height. */
  fadeFrom: number;
}

/** Draws in CSS pixels (1 dp = 1 px). The caller scales the context for the screen's density. */
export function drawBanner(c: CanvasRenderingContext2D, w: number, h: number, banner: Banner, o: DrawOptions): void {
  const aura: AuraDef = AURAS[banner.colour] ?? AURAS[3];
  const A = { glow: hex(aura.glow), mid: hex(aura.mid), base: hex(aura.base) };
  const P = pieces(banner.pattern);
  const { ex, ey, es, ea, ep } = P;
  const t = o.moving ? o.t : 0;
  const tau = Math.PI * 2;
  const dp = 1;

  const lin = (x0: number, y0: number, x1: number, y1: number, cols: Argb[], pos: number[]) => {
    const gr = c.createLinearGradient(x0, y0, x1, y1);
    cols.forEach((col, i) => gr.addColorStop(pos[i]!, css(col)));
    return gr;
  };
  const fillC = (col: Argb) => { c.fillStyle = css(col); };
  const strokeC = (col: Argb, width: number) => {
    c.strokeStyle = css(col);
    c.lineWidth = width;
    c.lineCap = "round";
    c.lineJoin = "round";
  };
  const dot = (x: number, y: number, r: number) => { c.beginPath(); c.arc(x, y, Math.max(0, r), 0, tau); c.fill(); };
  const ringAt = (x: number, y: number, r: number) => { c.beginPath(); c.arc(x, y, Math.max(0, r), 0, tau); c.stroke(); };
  const lineAt = (x0: number, y0: number, x1: number, y1: number) => { c.beginPath(); c.moveTo(x0, y0); c.lineTo(x1, y1); c.stroke(); };
  const roundRect = (x0: number, y0: number, x1: number, y1: number, r: number) => {
    c.beginPath();
    c.moveTo(x0 + r, y0);
    c.arcTo(x1, y0, x1, y1, r);
    c.arcTo(x1, y1, x0, y1, r);
    c.arcTo(x0, y1, x0, y0, r);
    c.arcTo(x0, y0, x1, y0, r);
    c.closePath();
  };
  const glow = (x: number, y: number, radius: number) => {
    if (radius <= 0) return;
    const gr = c.createRadialGradient(x, y, 0, x, y, radius);
    gr.addColorStop(0, css(withAlpha(A.glow, 150)));
    gr.addColorStop(0.45, css(withAlpha(A.glow, 40)));
    gr.addColorStop(1, css(withAlpha(A.glow, 0)));
    c.fillStyle = gr;
    dot(x, y, radius);
  };
  const twinkle = (i: number, speed: number) => (o.moving ? 0.55 + 0.45 * Math.sin(tau * (t * speed + ep[i]!)) : 1);

  const stars = (n: number, scale: number) => {
    const g = A.glow;
    for (let i = 0; i < n; i++) {
      const r = (0.5 + es[i]! * 1.2) * dp * scale;
      fillC(withAlpha(lighten(g, 0.5), (60 + ea[i]! * 190) * twinkle(i, 3)));
      dot(ex[i]! * w, ey[i]! * h * 0.9, r);
    }
  };
  const sparkles = () => {
    const g = lighten(A.glow, 0.6);
    for (let i = 0; i < 5; i++) {
      const x = ex[i]! * w, y = (0.1 + ey[i]! * 0.5) * h, s = (4 + es[i]! * 4) * dp * twinkle(i, 2);
      fillC(withAlpha(g, 230));
      c.beginPath();
      c.moveTo(x, y - s);
      c.quadraticCurveTo(x, y, x + s, y);
      c.quadraticCurveTo(x, y, x, y + s);
      c.quadraticCurveTo(x, y, x - s, y);
      c.quadraticCurveTo(x, y, x, y - s);
      c.closePath();
      c.fill();
    }
  };
  const hills = (top: number, colour: Argb, shift: number) => {
    c.beginPath();
    c.moveTo(0, h);
    c.lineTo(0, h * (top + 0.06));
    c.bezierCurveTo(w * (0.25 + shift * 0.2), h * (top - 0.08), w * 0.45, h * (top + 0.12), w * 0.68, h * top);
    c.bezierCurveTo(w * 0.82, h * (top - 0.05), w * 0.92, h * (top + 0.02), w, h * (top - 0.02));
    c.lineTo(w, h);
    c.closePath();
    fillC(colour);
    c.fill();
  };
  const heart = (x: number, y: number, s: number, colour: Argb) => {
    c.beginPath();
    c.moveTo(x, y + s);
    c.bezierCurveTo(x - s * 1.6, y - s * 0.2, x - s * 0.6, y - s * 1.4, x, y - s * 0.4);
    c.bezierCurveTo(x + s * 0.6, y - s * 1.4, x + s * 1.6, y - s * 0.2, x, y + s);
    c.closePath();
    fillC(colour);
    c.fill();
  };

  c.save();
  c.clearRect(0, 0, w, h);
  c.fillStyle = lin(0, 0, w * 0.6, h * 1.4, [shade(A.mid, 0.62), shade(A.mid, 0.38), A.base], [0, 0.45, 1]);
  c.fillRect(0, 0, w, h);

  switch (banner.pattern) {
    case STARS: glow(w * 0.75, h * 0.2, w * 0.55); stars(ex.length, 1); sparkles(); break;
    case WAVES: {
      glow(w * 0.2, h * 0.15, w * 0.6);
      const step = Math.max(4, 6 * dp);
      for (let i = 0; i < ex.length; i++) {
        const base = h * (0.3 + i * 0.13), amp = h * (0.05 + 0.03 * es[i]!);
        const k = (tau * (0.8 + ex[i]! * 0.9)) / w;
        const ph = tau * ep[i]! + tau * t * (i % 2 === 0 ? 1 : -1);
        c.beginPath();
        c.moveTo(0, h);
        for (let x = 0; x <= w + step; x += step) c.lineTo(x, base + amp * Math.sin(k * x + ph));
        c.lineTo(w, h);
        c.closePath();
        fillC(withAlpha(mix(A.glow, A.mid, i / 4), 34 + i * 9));
        c.fill();
      }
      break;
    }
    case RULED: {
      glow(w * 0.85, h * 0.1, w * 0.5);
      const gap = 20 * dp;
      strokeC(withAlpha(A.glow, 34), dp);
      for (let y = 30 * dp; y < h; y += gap) lineAt(0, y, w, y);
      const mx = w * 0.12;
      strokeC(withAlpha(A.glow, 90), dp);
      lineAt(mx, 0, mx, h);
      lineAt(mx + 3 * dp, 0, mx + 3 * dp, h);
      strokeC(withAlpha(lighten(A.glow, 0.3), 120), 1.6 * dp);
      for (let i = 0; i < ex.length; i++) {
        const y = 30 * dp + gap * (i + 1) - 4 * dp, end = mx + 14 * dp + w * (0.25 + 0.35 * ex[i]!);
        c.beginPath();
        c.moveTo(mx + 14 * dp, y);
        for (let x = mx + 14 * dp; x < end; x += 5 * dp) c.lineTo(x, y - Math.abs(Math.sin(x / (3.2 * dp) + ep[i]! * 6)) * 5 * dp);
        c.stroke();
      }
      const sx = w * 0.86, sy = h * 0.34, sr = 17 * dp;
      fillC(withAlpha(A.mid, 235));
      dot(sx, sy, sr);
      strokeC(withAlpha(A.glow, 170), 1.4 * dp);
      ringAt(sx, sy, sr * 0.68);
      heart(sx, sy, sr * 0.34, withAlpha(lighten(A.glow, 0.2), 220));
      break;
    }
    case LETTERS: {
      glow(w * 0.3, h * 0.3, w * 0.6);
      for (let i = 0; i < ex.length; i++) {
        const s = (18 + es[i]! * 18) * dp, hh = s * 0.66;
        const drift = o.moving ? 3 * dp * Math.sin(tau * (2 * t + ep[i]!)) : 0;
        c.save();
        c.translate(ex[i]! * w, ey[i]! * h * 0.85 + drift);
        c.rotate(((-30 + ep[i]! * 60) * Math.PI) / 180);
        strokeC(withAlpha(lighten(A.glow, 0.2), 50 + ea[i]! * 110), 1.5 * dp);
        roundRect(-s / 2, -hh / 2, s / 2, hh / 2, 2 * dp);
        c.stroke();
        c.beginPath();
        c.moveTo(-s / 2, -hh / 2);
        c.lineTo(0, hh * 0.08);
        c.lineTo(s / 2, -hh / 2);
        c.stroke();
        c.restore();
      }
      break;
    }
    case MOON: {
      const cx = w * 0.78, cy = h * 0.32, r = h * 0.17;
      glow(cx, cy, r * 4.2);
      stars(ex.length, 0.9);
      c.save();
      c.beginPath();                                   // everything except the bite → a crescent
      c.rect(0, 0, w, h);
      c.arc(cx + r * 0.45, cy - r * 0.2, r * 0.9, 0, tau);
      c.clip("evenodd");
      fillC(withAlpha(lighten(A.glow, 0.45), 245));
      dot(cx, cy, r);
      c.restore();
      hills(0.66, mix(A.mid, A.base, 0.5), 0.05);
      hills(0.76, shade(A.base, 0.8), 0.6);
      break;
    }
    case RAIN: {
      glow(w * 0.5, 0, w * 0.7);
      const g = lighten(A.glow, 0.3);
      for (let i = 0; i < ex.length; i++) {
        const len = (10 + es[i]! * 16) * dp, speed = 2 + ep[i]! * 2;
        const y = ((ey[i]! + t * speed) % 1) * (h + len) - len;
        const x = ex[i]! * (w + h * 0.3) - (y + len) * 0.28;
        strokeC(withAlpha(g, 40 + ea[i]! * 110), 1.2 * dp);
        lineAt(x, y, x + len * 0.28, y + len);
      }
      break;
    }
    case CONSTELLATION: {
      glow(w * 0.65, h * 0.35, w * 0.5);
      const g = lighten(A.glow, 0.4);
      strokeC(withAlpha(g, 70), dp);
      for (let k = 0; k < P.linkA.length; k++) {
        const i = P.linkA[k]!, j = P.linkB[k]!;
        lineAt(ex[i]! * w, ey[i]! * h, ex[j]! * w, ey[j]! * h);
      }
      for (let i = 0; i < ex.length; i++) {
        const r = (1.6 + es[i]! * 1.6) * dp, tw = twinkle(i, 2);
        fillC(withAlpha(g, 40 * tw));
        dot(ex[i]! * w, ey[i]! * h, r * 3.2);
        fillC(withAlpha(g, 160 + 90 * tw));
        dot(ex[i]! * w, ey[i]! * h, r);
      }
      break;
    }
    case DUNES: {
      glow(w * 0.7, h * 0.18, w * 0.5);
      for (let i = 0; i < ex.length; i++) {
        const top = h * (0.3 + 0.14 * i), sway = o.moving ? 0.03 * Math.sin(tau * (t + i * 0.25)) : 0;
        c.beginPath();
        c.moveTo(0, h);
        c.lineTo(0, top + h * 0.08 * (ex[i]! - 0.5));
        c.bezierCurveTo(w * (0.3 + sway), top - h * (0.1 + 0.06 * es[i]!), w * (0.6 - sway), top + h * 0.12, w, top - h * 0.04 * ep[i]!);
        c.lineTo(w, h);
        c.closePath();
        fillC(mix(withAlpha(A.glow, 120), A.base, i / 3));
        c.fill();
      }
      break;
    }
    case BOKEH: {
      const g = A.glow;
      glow(w * 0.5, h * 0.3, w * 0.6);
      for (let i = 0; i < ex.length; i++) {
        const r = (8 + es[i]! * 36) * dp;
        const dx = o.moving ? 6 * dp * Math.cos(tau * (t + ep[i]!)) : 0;
        const dy = o.moving ? 6 * dp * Math.sin(tau * (t + ep[i]!)) : 0;
        const al = Math.trunc(16 + ea[i]! * 44);
        fillC(withAlpha(g, al));
        dot(ex[i]! * w + dx, ey[i]! * h * 0.85 + dy, r);
        strokeC(withAlpha(g, al + 30), dp);
        ringAt(ex[i]! * w + dx, ey[i]! * h * 0.85 + dy, r);
      }
      break;
    }
    default: {                                          // GLOW: two soft lights drifting + faint rings
      glow(w * (0.25 + 0.06 * Math.sin(tau * t)), h * (0.4 + 0.1 * Math.cos(tau * t)), w * 0.7);
      glow(w * (0.82 - 0.05 * Math.sin(tau * t)), h * 0.2, w * 0.45);
      for (let i = 1; i <= 4; i++) {
        strokeC(withAlpha(A.glow, 46 - i * 8), dp);
        ringAt(w * 0.25, h * 0.4, h * 0.28 * i);
      }
    }
  }

  c.fillStyle = lin(0, 0, 0, h * 0.38, [withAlpha(INK, 120), withAlpha(INK, 0)], [0, 1]);
  c.fillRect(0, 0, w, h * 0.38);
  if (o.fade && o.fadeFrom > 0.6) {
    c.fillStyle = lin(0, h * 0.45, 0, h, [withAlpha(INK, 0), withAlpha(INK, 70), withAlpha(INK, 0)], [0, 0.5, 1]);
    c.fillRect(0, 0, w, h);
  }
  const from = o.fade ? o.fadeFrom : 0.5;
  c.fillStyle = o.fade
    ? lin(0, h * from, 0, h, [withAlpha(INK, 0), withAlpha(INK, 140), INK], [0, 0.55, 1])
    : lin(0, h * 0.5, 0, h, [withAlpha(INK, 0), withAlpha(INK, 90)], [0, 1]);
  c.fillRect(0, h * from, w, h - h * from);
  c.restore();
}
