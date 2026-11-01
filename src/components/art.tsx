/**
 * Drawn things: designed avatars (SVG), profile banners (canvas, can move) and the confession
 * graph (SVG). Ports of the Android AvatarRenderer, BannerView and ConfessionGraphView.
 * No inline styles anywhere — the page's CSP only allows its own stylesheet.
 */
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { avatarShapes, parseSpec, type Shape } from "../lib/avatar.ts";
import { type Banner, drawBanner, LOOP_MS, RULED } from "../lib/banner.ts";
import { addDays, cellPath, colorOf, type GraphData, type GraphStyle, level } from "../lib/graph.ts";

/* ── Avatar ─────────────────────────────────────────────────────────────── */

function ShapeEl({ s }: { s: Shape }) {
  const common = {
    fill: "fill" in s ? s.fill : undefined,
    fillOpacity: "fillOpacity" in s ? s.fillOpacity : undefined,
    stroke: "stroke" in s ? s.stroke : undefined,
    strokeOpacity: "strokeOpacity" in s ? s.strokeOpacity : undefined,
    strokeWidth: "width" in s ? s.width : undefined,
  };
  switch (s.el) {
    case "path": return <path d={s.d} {...common} />;
    case "circle": return <circle cx={s.cx} cy={s.cy} r={s.r} {...common} />;
    case "ellipse": return <ellipse cx={s.cx} cy={s.cy} rx={s.rx} ry={s.ry} {...common} />;
    case "rect": return <rect x={s.x} y={s.y} width={s.w} height={s.h} rx={s.rx} {...common} />;
    case "line": return <line x1={s.x1} y1={s.y1} x2={s.x2} y2={s.y2} {...common} />;
  }
}

export function AvatarArt({ value, size = 40, label }: { value: string; size?: number; label?: string }) {
  const uid = useId().replace(/:/g, "");
  const drawing = useMemo(() => avatarShapes(parseSpec(value)), [value]);
  const { backdrop: bd } = drawing;
  return (
    <svg className="aura" width={size} height={size} viewBox="0 0 100 100" role={label ? "img" : undefined}
         aria-label={label} aria-hidden={label ? undefined : true}>
      <defs>
        <clipPath id={`c${uid}`}><circle cx="50" cy="50" r="50" /></clipPath>
        <radialGradient id={`b${uid}`} cx={bd.cx} cy={bd.cy} r={bd.r} gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor={bd.stops[0]} />
          <stop offset="0.45" stopColor={bd.stops[1]} />
          <stop offset="1" stopColor={bd.stops[2]} />
        </radialGradient>
      </defs>
      <g clipPath={`url(#c${uid})`} strokeLinecap="round" strokeLinejoin="round">
        <rect width="100" height="100" fill={`url(#b${uid})`} />
        <g transform="matrix(1.12 0 0 1.12 -6 -6.96)">
          {drawing.shapes.map((s, i) => <ShapeEl key={i} s={s} />)}
        </g>
      </g>
      <circle cx="50" cy="50" r="49.6" fill="none" stroke="#fff" strokeOpacity={28 / 255} strokeWidth="0.8" />
    </svg>
  );
}

/* ── Banner ─────────────────────────────────────────────────────────────── */

const reducedMotion = () => typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * Fills its parent. `animated`: stars twinkle, rain falls… slowly, only while on screen,
 * the page is visible and the phone allows motion (same rules as Android).
 */
export function BannerArt({ banner, fade = true, fadeFrom = 0.5, animated = false, className = "" }:
  { banner: Banner; fade?: boolean; fadeFrom?: number; animated?: boolean; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    let w = 0, h = 0, frame = 0, visible = true;
    const t0 = performance.now();
    const moving = () => animated && visible && banner.pattern !== RULED && !reducedMotion() && document.visibilityState === "visible";

    const paint = () => {
      if (!w || !h) return;
      const t = moving() ? ((performance.now() - t0) % LOOP_MS) / LOOP_MS : 0;
      const dpr = Math.min(3, window.devicePixelRatio || 1);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      drawBanner(ctx, w, h, banner, { t, moving: moving(), fade, fadeFrom });
    };
    const loop = () => {
      paint();
      frame = moving() ? requestAnimationFrame(loop) : 0;
    };
    const kick = () => { if (!frame) frame = requestAnimationFrame(loop); };

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      w = Math.round(rect.width);
      h = Math.round(rect.height);
      const dpr = Math.min(3, window.devicePixelRatio || 1);
      canvas.width = Math.max(1, Math.round(w * dpr));
      canvas.height = Math.max(1, Math.round(h * dpr));
      paint();
    };
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    const io = new IntersectionObserver((e) => { visible = e.some((x) => x.isIntersecting); if (visible) kick(); });
    io.observe(canvas);
    const onVis = () => kick();
    document.addEventListener("visibilitychange", onVis);
    resize();
    kick();
    return () => {
      ro.disconnect();
      io.disconnect();
      document.removeEventListener("visibilitychange", onVis);
      cancelAnimationFrame(frame);
    };
  }, [banner.pattern, banner.colour, fade, fadeFrom, animated]);

  return <canvas ref={ref} className={`banner-art ${className}`} aria-hidden />;
}

/* ── Confession graph ───────────────────────────────────────────────────── */

const GAP = 3, MIN_CELL = 9, MAX_CELL = 15, LABEL_W = 14, MONTH_H = 16, RING_PAD = 2.5;

function useWidth<T extends Element>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setW(Math.round(el.getBoundingClientRect().width)));
    ro.observe(el);
    setW(Math.round(el.getBoundingClientRect().width));
    return () => ro.disconnect();
  }, []);
  return [ref, w];
}

const dayLabel = (d: Date) => d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });

/**
 * GitHub-style: a column per week (Monday on top), a cell per day, deeper the more you
 * confessed. Shows as many recent weeks as fit; today is outlined; tap a day to hear about it.
 */
export function ConfessionGraph({ data, style, label }: { data: GraphData; style: GraphStyle; label: string }) {
  const [box, width] = useWidth<HTMLDivElement>();
  const [selected, setSelected] = useState<number | null>(null);
  const days = data.days;
  const cols = Math.max(1, Math.ceil(days.length / 7));
  const avail = width - LABEL_W - 2 * RING_PAD;
  const shown = Math.max(1, Math.min(cols, Math.floor((avail + GAP) / (MIN_CELL + GAP))));
  const cell = Math.max(4, Math.min(MAX_CELL, (avail - GAP * (shown - 1)) / shown));
  const first = cols - shown;
  const height = MONTH_H + 7 * cell + 6 * GAP + RING_PAD;
  const used = shown * cell + (shown - 1) * GAP;
  const colX = (col: number) => width - RING_PAD - used + (col - first) * (cell + GAP);
  const rowY = (row: number) => MONTH_H + row * (cell + GAP);
  const today = days.length - 1;

  const months: { x: number; text: string }[] = [];
  let lastEnd = -1;
  for (let col = first; col < cols; col++) {
    const monday = addDays(data.start, col * 7);
    const prev = addDays(data.start, col * 7 - 7);
    if (col !== first && monday.getMonth() === prev.getMonth()) continue;
    const text = monday.toLocaleDateString(undefined, { month: "short" });
    const x = colX(col);
    if (x < lastEnd + 4) continue;
    months.push({ x, text });
    lastEnd = x + text.length * 6.2;
  }

  const sel = selected ?? null;
  const caption = sel === null ? null
    : days[sel] ? `${days[sel]} confession${days[sel] === 1 ? "" : "s"} · ${dayLabel(addDays(data.start, sel))}`
      : `Nothing that day · ${dayLabel(addDays(data.start, sel))}`;

  return (
    <div className="graph" ref={box}>
      {width > 0 ? (
        <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img"
             aria-label={`${label}: ${data.total} confession${data.total === 1 ? "" : "s"} on ${data.activeDays} day${data.activeDays === 1 ? "" : "s"}`}>
          {months.map((m) => <text key={m.x} x={m.x} y={MONTH_H - 5} className="graph-label">{m.text}</text>)}
          {["M", "W", "F"].map((t, i) => (
            <text key={t} x={colX(first) - LABEL_W} y={rowY(i * 2) + cell / 2 + 3.5} className="graph-label">{t}</text>
          ))}
          {Array.from({ length: shown }, (_, k) => first + k).map((col) =>
            Array.from({ length: 7 }, (_, row) => {
              const i = col * 7 + row;
              if (i >= days.length) return null;
              const x = colX(col), y = rowY(row);
              const [colr, op] = colorOf(style, level(days[i]!));
              const ring = i === today || i === sel;
              return (
                <g key={i}>
                  <path d={cellPath(style.shape, x, y, cell)} fill={colr} fillOpacity={op}
                        className="graph-cell" onClick={() => setSelected(i === sel ? null : i)} />
                  {ring ? (
                    <path d={cellPath(style.shape, x - 1.5, y - 1.5, cell + 3, true)} fill="none" strokeWidth="1.5"
                          stroke={i === sel ? "#ECE8E1" : colorOf(style, 4)[0]} pointerEvents="none" />
                  ) : null}
                </g>
              );
            }))}
        </svg>
      ) : <div className="graph-placeholder" />}
      <div className="graph-foot">
        <span className="meta" aria-live="polite">{caption ?? "Tap a day"}</span>
        <span className="graph-legend" aria-hidden>
          <span className="meta">Less</span>
          <svg width="66" height="10" viewBox="0 0 66 10">
            {[0, 1, 2, 3, 4].map((l) => {
              const [c, o] = colorOf(style, l);
              return <path key={l} d={cellPath(style.shape, l * 14, 0, 10)} fill={c} fillOpacity={o} />;
            })}
          </svg>
          <span className="meta">More</span>
        </span>
      </div>
    </div>
  );
}

/** A small preview cell for the style sheet. */
export function ShapeSwatch({ style, size = 18 }: { style: GraphStyle; size?: number }) {
  return (
    <svg width={size * 3 + 8} height={size} viewBox={`0 0 ${size * 3 + 8} ${size}`} aria-hidden>
      {[2, 3, 4].map((l, i) => {
        const [c, o] = colorOf(style, l);
        return <path key={l} d={cellPath(style.shape, i * (size + 4), 0, size)} fill={c} fillOpacity={o} />;
      })}
    </svg>
  );
}
