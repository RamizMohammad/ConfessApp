/**
 * Bursts as the Android app shows them: item_burst_slider (swipe through, oldest first, dots +
 * counter, the height follows the page), item_burst_stack (a pile of paper that opens the reader),
 * BurstReaderActivity (one per page, segments, Previous / Next / Done) and sheet_burst_chooser
 * with its looping BurstPreviewView drawings.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { paperColours, useAppearance } from "../lib/appearance.ts";
import { type BurstMode, burstMode, fetchBurst, setBurstMode, SMALL } from "../lib/bursts.ts";
import { relative } from "../lib/format.ts";
import { apply, cardActions, remember, subscribe } from "../lib/postStore.ts";
import { back, navigate, openPost } from "../lib/router.ts";
import { S } from "../lib/strings.ts";
import type { Mood, Post } from "../lib/types.ts";
import { reducedMotion } from "../ui/Art.tsx";
import { Icon } from "../ui/Icon.tsx";
import { Btn, haptic, IconBtn, Sheet, Spinner } from "../ui/kit.tsx";
import { openProfile, PostCard } from "./post.tsx";
import { RingAvatar } from "./stories.tsx";

export interface BurstCtx { scope: "all" | "following"; mood: Mood | null }

/** The feed card for a burst, in the reader's chosen look (Bursts.asStack). */
export function BurstCard({ head, mode, ctx }: { head: Post; mode: BurstMode; ctx: BurstCtx }) {
  const total = head.burst?.count ?? 1;
  if (mode === "stack" && total > SMALL) return <BurstStack head={head} ctx={ctx} />;
  return <BurstSlider head={head} ctx={ctx} />;
}

/** BurstStore: one burst, fetched a set at a time, oldest first. */
function useBurst(id: string, ctx: BurstCtx, initialTotal: number, limit = 5) {
  const [posts, setPosts] = useState<Post[]>([]);
  const [total, setTotal] = useState(initialTotal);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<"idle" | "loading" | "done" | "error">("idle");
  const busy = useRef(false);

  const load = useCallback(async (c: string | null) => {
    if (busy.current) return;
    busy.current = true;
    setState("loading");
    try {
      const r = await fetchBurst(id, { scope: ctx.scope, mood: ctx.mood, cursor: c, limit });
      remember(r.posts);
      setPosts((cur) => (c ? [...cur, ...r.posts.filter((p) => !cur.some((x) => x.postId === p.postId))] : r.posts));
      setTotal(r.total);
      setCursor(r.nextCursor);
      setState(r.nextCursor ? "idle" : "done");
    } catch {
      setState("error");
    } finally {
      busy.current = false;
    }
  }, [id, ctx.scope, ctx.mood, limit]);

  useEffect(() => { void load(null); }, [load]);
  useEffect(() => subscribe((e) => setPosts((cur) => apply(cur, e))), []);
  return { posts, total, cursor, state, load };
}

const pageOf = new Map<string, number>();

/** item_burst_slider: swipe through them in one card, oldest first. */
function BurstSlider({ head, ctx }: { head: Post; ctx: BurstCtx }) {
  const id = head.burst!.id;
  const b = useBurst(id, ctx, head.burst!.count);
  const [page, setPage] = useState(() => pageOf.get(id) ?? 0);
  const track = useRef<HTMLDivElement>(null);
  const actions = cardActions((p, r) => openPost(p.postId, r));
  const shown = b.posts.length ? b.posts : [head];
  const total = Math.max(b.total, b.posts.length);

  // The pager's height follows the page you're on (as the Android slider measures each page).
  const fit = useCallback((p: number) => {
    const t = track.current;
    const slide = t?.children[Math.min(p, t.children.length - 1)] as HTMLElement | undefined;
    if (t && slide) t.style.setProperty("--h", `${slide.offsetHeight}px`);
  }, []);
  useLayoutEffect(() => { fit(page); }, [fit, page, shown]);
  useEffect(() => {
    const t = track.current;
    if (!t || typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(() => fit(page));
    Array.from(t.children).forEach((c) => ro.observe(c));
    return () => ro.disconnect();
  }, [fit, page, shown]);

  // Start where you left it (oldest first the first time).
  const started = useRef(false);
  useLayoutEffect(() => {
    const t = track.current;
    if (!started.current && b.posts.length && t) { started.current = true; t.scrollLeft = page * t.clientWidth; }
  }, [b.posts.length]); // eslint-disable-line react-hooks/exhaustive-deps

  function onScroll() {
    const el = track.current;
    if (!el) return;
    const p = Math.round(el.scrollLeft / el.clientWidth);
    if (p !== page) { setPage(p); pageOf.set(id, p); }
    if (p >= b.posts.length - 2 && b.state === "idle" && b.cursor) void b.load(b.cursor);
  }

  let counter = S("burst_counter", Math.min(page + 1, total), total);
  if (b.state === "loading" && page >= b.posts.length - 2) counter = S("burst_counter_loading", counter);

  return (
    <section className="bslider" aria-label={S("burst_slider_label", head.author.alias, total)}>
      <p className="bslider-label t-meta-small"><Icon name="arrow_right" size={14} />{S("burst_slider_label", head.author.alias, total)}</p>
      <div className="bslider-track" ref={track} onScroll={onScroll}>
        {shown.map((p) => <div className="bslider-page" key={b.posts.length ? p.postId : "head"}><PostCard post={p} actions={actions} /></div>)}
      </div>
      <div className="bslider-foot">
        <span className="grow" />
        {total <= 10 ? (
          <span className="bslider-dots" aria-hidden>{Array.from({ length: total }, (_, i) => <i key={i} className={i === page ? "on" : ""} />)}</span>
        ) : null}
        <span className="bslider-counter t-meta-small">{counter}</span>
      </div>
    </section>
  );
}

/** item_burst_stack: more than 3, one pile of paper that opens the reader. */
function BurstStack({ head, ctx }: { head: Post; ctx: BurstCtx }) {
  const id = head.burst!.id;
  const b = useBurst(id, ctx, head.burst!.count);
  const total = Math.max(b.total, b.posts.length) || head.burst!.count;
  const first = b.posts[0] ?? head;
  const alias = head.author.alias;
  const open = () => { haptic(); navigate({ name: "burst", id, scope: ctx.scope }); };
  return (
    <div className="bstack">
      <span className="bstack-sheet s2" aria-hidden />
      <span className="bstack-sheet s1" aria-hidden />
      <div className="bstack-card" role="button" tabIndex={0} aria-label={S("cd_burst_stack", alias, total)}
           onClick={(e) => { if (!(e.target as Element).closest(".bstack-author")) open(); }}
           onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); } }}>
        <div className="pc-head">
          <button type="button" className="pc-avatar bstack-author" onClick={() => openProfile(alias, head.isMine)}>
            <RingAvatar alias={alias} avatar={head.author.avatar} size={36} />
          </button>
          <div className="pc-who">
            <button type="button" className="pc-alias t-alias bstack-author" onClick={() => openProfile(alias, head.isMine)}>@{alias}</button>
            <span className="pc-time t-meta">{relative(head.createdAt)}</span>
          </div>
          <span className="bstack-count">{total}</span>
        </div>
        <p className="bstack-title">{S("burst_stack_title", alias, total)}</p>
        <p className="bstack-preview">{first.text?.trim()}</p>
        <div className="bstack-foot">
          <span className="t-meta">{S("burst_stack_sub")}</span>
          <span className="k-btn k-btn-link bstack-read">{S("burst_read")}<Icon name="arrow_right" size={18} /></span>
        </div>
      </div>
    </div>
  );
}

/** BurstReaderActivity: /burst/<id> — one confession per page, oldest first. */
export function BurstReader({ id, scope }: { id: string; scope: "all" | "following" }) {
  const b = useBurst(id, { scope, mood: null }, 0, 10);
  const [page, setPage] = useState(0);
  const pager = useRef<HTMLDivElement>(null);
  const actions = cardActions((p, r) => openPost(p.postId, r));
  const total = Math.max(b.total, b.posts.length);
  const alias = b.posts[0]?.author.alias ?? "";
  const atEnd = b.state === "done" && page >= b.posts.length - 1;

  useEffect(() => {
    if (page >= b.posts.length - 3 && b.state === "idle" && b.cursor) void b.load(b.cursor);
  }, [page, b]);
  // All taken down / hidden for me (or I blocked them): nothing left to read.
  useEffect(() => { if (b.state === "done" && !b.posts.length) back(); }, [b.state, b.posts.length]);

  const go = (p: number) => {
    const el = pager.current;
    if (!el) return;
    const to = Math.max(0, Math.min(b.posts.length - 1, p));
    el.scrollTo({ left: to * el.clientWidth, behavior: reducedMotion() ? "auto" : "smooth" });
  };
  const onScroll = () => {
    const el = pager.current;
    if (el) setPage(Math.round(el.scrollLeft / el.clientWidth));
  };
  const MAX = 12;
  const segs = total <= MAX ? total : 2;

  return (
    <main className="a-screen breader">
      <header className="breader-top">
        <IconBtn icon="arrow_left" label={S("action_back")} onClick={back} />
        <span className="breader-titles">
          <span className="t-alias breader-title">{alias ? `@${alias}` : ""}</span>
          <span className="t-meta breader-sub">{total > 0 ? S("burst_reader_position", Math.min(page + 1, total), total) : ""}</span>
        </span>
      </header>
      <div className={`breader-segs${total > MAX ? " two" : ""}`} aria-hidden>
        {Array.from({ length: segs }, (_, i) => {
          const on = total > MAX ? i === 0 : i <= page;
          return <i key={i} className={on ? "on" : ""} data-grow={total > MAX ? (i === 0 ? page + 1 : Math.max(0, total - page - 1)) : undefined}
                    ref={(el) => { if (el && total > MAX) el.style.setProperty("flex-grow", String(i === 0 ? page + 1 : Math.max(0, total - page - 1))); }} />;
        })}
      </div>
      <div className="breader-body">
        <div className="breader-pager" ref={pager} onScroll={onScroll}>
          {b.posts.map((p) => (
            <div key={p.postId} className="breader-page"><PostCard post={p} actions={actions} /></div>
          ))}
        </div>
        {b.state === "loading" && !b.posts.length ? <span className="breader-loading"><Spinner /></span> : null}
      </div>
      <div className="breader-bottom">
        <Btn kind="outlined" disabled={page <= 0} onClick={() => go(page - 1)}>{S("burst_prev")}</Btn>
        <Btn disabled={!b.posts.length} onClick={() => (atEnd ? back() : go(page + 1))}>{S(atEnd ? "burst_done" : "burst_next")}</Btn>
      </div>
    </main>
  );
}

/* ── BurstPreviewView: the looping little drawings in the chooser ───────── */

const LOOP_MS = 4200;
const ease = (x: number) => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };

function BurstPreview({ mode }: { mode: BurstMode }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const a = useAppearance();
  useEffect(() => {
    const cv = canvas.current;
    const ctx = cv?.getContext("2d");
    if (!cv || !ctx) return;
    const tone = paperColours(a);
    const css = getComputedStyle(document.documentElement);
    const col = (n: string) => css.getPropertyValue(n).trim();
    const frame = col("--c-surface-high"), accent = col("--c-amber");
    const dim = "rgba(142, 138, 150, 0.353)";
    const inkHex = tone.ink;
    const ink = `rgba(${parseInt(inkHex.slice(1, 3), 16)}, ${parseInt(inkHex.slice(3, 5), 16)}, ${parseInt(inkHex.slice(5, 7), 16)}, ${(120 / 255).toFixed(3)})`;
    const pp = tone.paper;
    const shade = (hex: string, f: number) => {
      const c = [1, 3, 5].map((i) => Math.round(parseInt(hex.slice(i, i + 2), 16) * (1 - f)));
      return `rgb(${c[0]}, ${c[1]}, ${c[2]})`;
    };
    const shadow = shade(pp, 0.18);
    let raf = 0;
    const start = performance.now();
    const rr = (x: number, y: number, w: number, h: number, r: number, fill: string) => {
      ctx.fillStyle = fill;
      ctx.beginPath();
      ctx.roundRect(x, y, Math.max(0, w), Math.max(0, h), r);
      ctx.fill();
    };
    const lines = (l: number, r: number, y: number, n: number, H: number) => {
      for (let i = 0; i < n; i++) {
        const end = i === n - 1 ? l + (r - l) * 0.6 : r;
        const yy = y + i * 8;
        if (yy > H) break;
        rr(l, yy, end - l, 3.5, 2, ink);
      }
    };
    const card = (l: number, t: number, r: number, b: number, n: number, H: number) => {
      rr(l, t, r - l, b - t, 8, pp);
      const x = l + 8, y = t + 8;
      ctx.fillStyle = accent; ctx.beginPath(); ctx.arc(x + 4, y + 4, 4, 0, Math.PI * 2); ctx.fill();
      rr(x + 12, y + 2, 28, 4, 2, ink);
      if (n > 0) lines(l + 8, r - 8, y + 16, n, H);
    };
    const draw = (t: number) => {
      const dpr = window.devicePixelRatio || 1;
      const W = cv.clientWidth, H = 108;
      if (cv.width !== Math.round(W * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);
      const fw = Math.min(W, 132), fx = (W - fw) / 2;
      rr(fx, 0, fw, H, 14, frame);
      ctx.save();
      ctx.beginPath(); ctx.rect(fx + 6, 6, fw - 12, H - 12); ctx.clip();
      const left = fx + 10, right = fx + fw - 10;
      if (mode === "slider") {
        const cw = right - left;
        const pos = t < 0.22 ? 0 : t < 0.34 ? ease((t - 0.22) / 0.12) : t < 0.56 ? 1 : t < 0.68 ? 1 + ease((t - 0.56) / 0.12)
          : t < 0.9 ? 2 : 2 - 2 * ease((t - 0.9) / 0.1);
        for (let i = 0; i < 3; i++) {
          const x = left + (i - pos) * (cw + 8);
          if (x > right || x + cw < left) continue;
          card(x, 14, x + cw, H - 26, i === 0 ? 3 : i === 1 ? 2 : 4, H);
        }
        const cy = H - 15, gap = 9, c0 = (left + right) / 2 - gap, on = Math.round(pos);
        for (let i = 0; i < 3; i++) { ctx.fillStyle = i === on ? accent : dim; ctx.beginPath(); ctx.arc(c0 + i * gap, cy, 2.6, 0, Math.PI * 2); ctx.fill(); }
      } else if (mode === "stack") {
        const top = 16, bottom = H - 22;
        const open = t < 0.35 ? 0 : t < 0.5 ? ease((t - 0.35) / 0.15) : t < 0.9 ? 1 : 1 - ease((t - 0.9) / 0.1);
        const drop = 1 - open;
        rr(left + 14, top + 12 * drop, right - left - 28, bottom - top, 8, shadow);
        rr(left + 7, top + 6 * drop, right - left - 14, bottom - top, 8, shadow);
        const grow = 8 * open;
        card(left - grow, top - grow, right + grow, bottom + grow, 0, H);
        if (open < 0.5) {
          const al = 1 - open * 2;
          ctx.globalAlpha = al;
          rr(left + 10, top + 30, (right - left) * 0.72 - 10, 6, 3, ink);
          ctx.fillStyle = accent; ctx.beginPath(); ctx.arc(right - 14, top + 14, 8, 0, Math.PI * 2); ctx.fill();
          ctx.globalAlpha = 1;
          if (t > 0.18 && t < 0.38) {
            const k = (t - 0.18) / 0.2;
            ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.globalAlpha = (220 * (1 - k)) / 255;
            ctx.beginPath(); ctx.arc((left + right) / 2, (top + bottom) / 2 + 8, 6 + 14 * k, 0, Math.PI * 2); ctx.stroke();
            ctx.globalAlpha = 1;
          }
        } else {
          const pg = t < 0.65 ? 0 : t < 0.8 ? 1 : 2;
          const sx = left + 4, sw = (right - left - 8 - 12) / 4;
          for (let i = 0; i < 4; i++) rr(sx + i * (sw + 4), top - 4, sw, 2, 1, i <= pg ? accent : dim);
          lines(left + 8, right - 8, top + 12, 3 + pg, H);
        }
      } else {
        const ch = 40, gap = 8, shift = ((t * 3) % 1) * (ch + gap);
        for (let i = 0; i < 4; i++) { const y = 8 + i * (ch + gap) - shift; card(left, y, right, y + ch, 2, H); }
      }
      ctx.restore();
    };
    if (reducedMotion()) { draw(0.3); return; }
    const step = (now: number) => { draw(((now - start) % LOOP_MS) / LOOP_MS); raf = requestAnimationFrame(step); };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [mode, a]);
  return <canvas ref={canvas} className="bpreview" aria-hidden />;
}

/** sheet_burst_chooser: asked once, the first time a burst shows up (also Settings → Appearance). */
export function BurstChooser({ open, onClose, onPicked }: { open: boolean; onClose: () => void; onPicked?: (m: BurstMode) => void }) {
  const [choice, setChoice] = useState<BurstMode>(burstMode);
  useEffect(() => { if (open) setChoice(burstMode()); }, [open]);
  const opts: { key: BurstMode; label: string; sub: string }[] = [
    { key: "slider", label: "burst_mode_slider", sub: "burst_mode_slider_sub" },
    { key: "stack", label: "burst_mode_stack", sub: "burst_mode_stack_sub" },
    { key: "separate", label: "burst_mode_separate", sub: "burst_mode_separate_sub" },
  ];
  return (
    <Sheet open={open} onClose={onClose} label={S("burst_choose_title")}>
      <div className="k-sheet-scroll bchooser">
        <h2 className="t-headline-small">{S("burst_choose_title")}</h2>
        <p className="t-body bchooser-body">{S("burst_choose_body")}</p>
        <div className="bchooser-opts" role="radiogroup">
          {opts.map((o) => (
            <button key={o.key} type="button" role="radio" aria-checked={choice === o.key}
                    className={`bchooser-opt${choice === o.key ? " on" : ""}`}
                    onClick={() => { haptic("tick"); setChoice(o.key); }}>
              <BurstPreview mode={o.key} />
              <span className="t-body-medium bchooser-label">{S(o.label)}</span>
              <span className="t-meta-small bchooser-sub">{S(o.sub)}</span>
            </button>
          ))}
        </div>
        <p className="t-meta-small bchooser-later">{S("burst_choose_later")}</p>
        <Btn block className="bchooser-use" onClick={() => { setBurstMode(choice); haptic(); onClose(); onPicked?.(choice); }}>
          {S("burst_choose_use")}
        </Btn>
      </div>
    </Sheet>
  );
}
