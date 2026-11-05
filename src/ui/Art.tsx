/**
 * The Android app's drawn art — logos, Letty's poses — and its AnimatedVectorDrawables (the
 * splash, the tab icons), from src/android/art.ts (generated). <AnimatedArt> plays an AVD with the
 * same timings and interpolators as Android, on a requestAnimationFrame clock, by setting SVG
 * attributes (the CSP allows those; it forbids inline styles).
 */
import { useCallback, useEffect, useId, useImperativeHandle, useRef, type ReactNode, type Ref } from "react";
import { anims, vectors, type ArtAnim, type ArtGradient, type ArtNode, type ArtVector } from "../android/art.ts";

// ── Android interpolators ──────────────────────────────────────────────────────

function bezier(x1: number, y1: number, x2: number, y2: number): (t: number) => number {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const x = (u: number) => ((ax * u + bx) * u + cx) * u;
  const y = (u: number) => ((ay * u + by) * u + cy) * u;
  return (t) => {
    let u = t;
    for (let i = 0; i < 8; i++) {
      const dx = x(u) - t, d = (3 * ax * u + 2 * bx) * u + cx;
      if (Math.abs(dx) < 1e-5 || Math.abs(d) < 1e-6) break;
      u -= dx / d;
    }
    return y(Math.min(1, Math.max(0, u)));
  };
}

export const EASE: Record<string, (t: number) => number> = {
  linear: (t) => t,
  accelerate_decelerate: (t) => Math.cos((t + 1) * Math.PI) / 2 + 0.5,
  accelerate_quad: (t) => t * t,
  decelerate_quad: (t) => 1 - (1 - t) * (1 - t),
  accelerate_cubic: (t) => t * t * t,
  decelerate_cubic: (t) => 1 - (1 - t) ** 3,
  // OvershootInterpolator(tension 2)
  overshoot: (t) => { const u = t - 1; return u * u * (3 * u + 2) + 1; },
  fast_out_slow_in: bezier(0.4, 0, 0.2, 1),
};

/** OvershootInterpolator with another tension (ConfessDialog uses 1.1, ChoiceSheet 2). */
export const overshoot = (tension: number) => (t: number) => { const u = t - 1; return u * u * ((tension + 1) * u + tension) + 1; };

/** TourActivity.Spring: 1 − e^(−7t)·cos(ωt). */
export const spring = (w: number) => (t: number) => (t >= 1 ? 1 : 1 - Math.exp(-7 * t) * Math.cos(w * t));

export const reducedMotion = () =>
  typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;

// ── Drawing ────────────────────────────────────────────────────────────────────

type Path = Extract<ArtNode, { t: "p" }>;

/** Android's group matrix: translate(t + pivot) · rotate · scale · translate(−pivot). */
export function groupTransform(g: { tx?: number; ty?: number; sx?: number; sy?: number; r?: number; px?: number; py?: number }): string | undefined {
  const tx = g.tx ?? 0, ty = g.ty ?? 0, sx = g.sx ?? 1, sy = g.sy ?? 1, r = g.r ?? 0, px = g.px ?? 0, py = g.py ?? 0;
  if (!tx && !ty && sx === 1 && sy === 1 && !r) return undefined;
  return `translate(${tx + px} ${ty + py}) rotate(${r}) scale(${sx} ${sy}) translate(${-px} ${-py})`;
}

function trim(p: { ts?: number; te?: number; to?: number }): { dash?: string; offset?: number } {
  if (p.ts == null && p.te == null && p.to == null) return {};
  const start = (p.ts ?? 0) + (p.to ?? 0), end = (p.te ?? 1) + (p.to ?? 0);
  const len = Math.max(0, end - start);
  return { dash: `${len} ${1 + 1e-3}`, offset: -start };
}

function Gradient({ g, id }: { g: ArtGradient; id: string }) {
  const stops = g.stops.map(([o, c], i) => <stop key={i} offset={o} stopColor={c} />);
  return g.type === "radial"
    ? <radialGradient id={id} gradientUnits="userSpaceOnUse" cx={g.centerX} cy={g.centerY} r={g.gradientRadius}>{stops}</radialGradient>
    : <linearGradient id={id} gradientUnits="userSpaceOnUse" x1={g.startX} y1={g.startY} x2={g.endX} y2={g.endY}>{stops}</linearGradient>;
}

type Register = (name: string, el: SVGElement | null) => void;

function draw(nodes: ArtNode[], uid: string, reg: Register | null, key = ""): ReactNode[] {
  const paint = (v: Path["f"] | undefined) => (v == null ? undefined : typeof v === "string" ? v : `url(#${uid}${v.grad})`);
  return nodes.map((n, i) => {
    const k = `${key}${i}`;
    if (n.t === "g") {
      return (
        <g key={k} transform={groupTransform(n)} ref={n.n && reg ? (el) => reg(n.n!, el) : undefined}>
          {draw(n.c, uid, reg, `${k}.`)}
        </g>
      );
    }
    if (n.t === "c") return <clipPath key={k}><path d={n.d} /></clipPath>;
    const t = trim(n);
    return (
      <path key={k} d={n.d} fill={paint(n.f)} fillOpacity={n.fa} fillRule={n.rule} stroke={paint(n.s)} strokeWidth={n.sw}
            strokeOpacity={n.sa} strokeLinecap={n.cap as "round" | undefined} strokeLinejoin={n.join as "round" | undefined}
            pathLength={t.dash ? 1 : undefined} strokeDasharray={t.dash} strokeDashoffset={t.offset}
            ref={n.n && reg ? (el) => reg(n.n!, el) : undefined} />
    );
  });
}

function Svg({ v, size, width, height, className, label, reg }:
  { v: ArtVector; size?: number; width?: number; height?: number; className?: string; label?: string; reg: Register | null }) {
  const uid = useId().replace(/:/g, "");
  // Like an ImageView with adjustViewBounds: one side given → the other keeps the drawable's shape.
  const w = width ?? size ?? (height != null ? (height * v.w) / v.h : v.w);
  const h = height ?? (w * v.h) / v.w;
  return (
    <svg viewBox={v.vb} width={w} height={h} className={className} role={label ? "img" : undefined}
         aria-label={label} aria-hidden={label ? undefined : true} focusable="false">
      {v.defs ? <defs>{v.defs.map((g) => <Gradient key={g.id} g={g} id={uid + g.id} />)}</defs> : null}
      {draw(v.c, uid, reg)}
    </svg>
  );
}

/** A static drawable (logo_letty, logo_lockup, letty_pose_wave…). size = width in dp. */
export function Art({ name, size, width, height, className, label }:
  { name: string; size?: number; width?: number; height?: number; className?: string; label?: string }) {
  const v = vectors[name];
  if (!v) return null;
  return <Svg v={v} size={size} width={width} height={height} className={className} label={label} reg={null} />;
}

// ── Playing an AnimatedVectorDrawable ──────────────────────────────────────────

export type AnimatedArtHandle = { play: () => void; finish: () => void };

function baseValues(nodes: ArtNode[], into: Map<string, Record<string, number>>) {
  for (const n of nodes) {
    if (n.t === "g") {
      if (n.n) into.set(n.n, { tx: n.tx ?? 0, ty: n.ty ?? 0, sx: n.sx ?? 1, sy: n.sy ?? 1, r: n.r ?? 0, px: n.px ?? 0, py: n.py ?? 0 });
      baseValues(n.c, into);
    } else if (n.t === "p" && n.n) {
      into.set(n.n, { fa: n.fa ?? 1, sa: n.sa ?? 1, ts: n.ts ?? 0, te: n.te ?? 1, to: n.to ?? 0 });
    }
  }
}

const PROP: Record<string, string> = {
  translateX: "tx", translateY: "ty", scaleX: "sx", scaleY: "sy", rotation: "r",
  fillAlpha: "fa", strokeAlpha: "sa", trimPathStart: "ts", trimPathEnd: "te", trimPathOffset: "to",
};

/** The value of one property at time t (ms): the latest animator that has started wins, as on Android. */
function valueAt(list: ArtAnim[], base: number, t: number): number {
  let v = base;
  for (const a of list) {
    if (t < a.at) continue;
    const f = a.ms <= 0 ? 1 : Math.min(1, (t - a.at) / a.ms);
    v = a.from + (a.to - a.from) * (EASE[a.ease] ?? EASE.accelerate_decelerate!)(f);
  }
  return v;
}

/**
 * An AnimatedVectorDrawable (e.g. "splash_letty_anim"). Plays once when `autoPlay`, or via the
 * handle. With Reduce Motion on it shows the last frame.
 */
export function AnimatedArt({ avd, size, className, label, autoPlay = true, onEnd, handle }:
  { avd: string; size?: number; className?: string; label?: string; autoPlay?: boolean; onEnd?: () => void; handle?: Ref<AnimatedArtHandle> }) {
  const spec = anims[avd]!;
  const v = vectors[spec.drawable]!;
  const els = useRef(new Map<string, SVGElement>());
  const raf = useRef(0);
  const reg = useCallback<Register>((name, el) => { if (el) els.current.set(name, el); else els.current.delete(name); }, []);

  const apply = useCallback((t: number) => {
    const base = new Map<string, Record<string, number>>();
    baseValues(v.c, base);
    for (const [target, list] of Object.entries(spec.targets)) {
      const el = els.current.get(target), b = base.get(target);
      if (!el || !b) continue;
      const now = { ...b };
      const byProp = new Map<string, ArtAnim[]>();
      for (const a of list) {
        const p = PROP[a.p];
        if (!p) continue;
        if (!byProp.has(p)) byProp.set(p, []);
        byProp.get(p)!.push(a);
      }
      for (const [p, l] of byProp) now[p] = valueAt([...l].sort((x, y) => x.at - y.at), b[p] ?? 0, t);
      if (el.tagName === "g") {
        const tr = groupTransform(now);
        if (tr) el.setAttribute("transform", tr); else el.removeAttribute("transform");
      } else {
        if (byProp.has("fa")) el.setAttribute("fill-opacity", String(now.fa));
        if (byProp.has("sa")) el.setAttribute("stroke-opacity", String(now.sa));
        if (byProp.has("ts") || byProp.has("te") || byProp.has("to")) {
          const tm = trim(now);
          el.setAttribute("pathLength", "1");
          el.setAttribute("stroke-dasharray", tm.dash ?? "1 0");
          el.setAttribute("stroke-dashoffset", String(tm.offset ?? 0));
        }
      }
    }
  }, [spec, v]);

  const finish = useCallback(() => {
    cancelAnimationFrame(raf.current);
    apply(spec.ms);
  }, [apply, spec.ms]);

  const play = useCallback(() => {
    cancelAnimationFrame(raf.current);
    if (reducedMotion()) { apply(spec.ms); onEnd?.(); return; }
    apply(0);
    const start = performance.now();
    const step = (now: number) => {
      const t = now - start;
      apply(Math.min(t, spec.ms));
      if (t < spec.ms) raf.current = requestAnimationFrame(step);
      else onEnd?.();
    };
    raf.current = requestAnimationFrame(step);
  }, [apply, spec.ms, onEnd]);

  useImperativeHandle(handle, () => ({ play, finish }), [play, finish]);
  useEffect(() => {
    if (autoPlay) play(); else apply(0);
    return () => cancelAnimationFrame(raf.current);
  }, [autoPlay]); // eslint-disable-line react-hooks/exhaustive-deps

  return <Svg v={v} size={size} className={className} label={label} reg={reg} />;
}
