/**
 * The feature tour (TourActivity + Tour + TourDemos + MorphView, activity_tour.xml).
 *
 * Words top-left — a NEW tag, the title (its words rise in one after another), a line, and
 * "Find it" lighting up step by step — and under them a live sample of the feature inside one
 * shape that morphs from page to page. The page follows your finger and lets go on a spring.
 * Shown once after sign-in (and when a web version adds pages), and from Settings → Take the tour.
 *
 * Web differences: no "Confess sounds" page (an iPhone web app can't choose notification
 * sounds); "Locked to everyone else" talks about Face ID and the app-switcher blur.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type PointerEvent as RPointerEvent, type ReactNode } from "react";
import { biometricName } from "../lib/device.ts";
import { A, S } from "../lib/strings.ts";
import { Art, reducedMotion, spring } from "../ui/Art.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, haptic, Tag } from "../ui/kit.tsx";
import { accelerate } from "../ui/motion.ts";

// ── Which pages, and when they've been seen ───────────────────────────────────

/** Bump when a web release adds a tour page (its `since`), so returning people see "What's new". */
export const TOUR_VERSION = 1;
const SEEN_KEY = "confess.tour.seen";

type Page = { id: string; icon: IconName | "letty"; title: string; body: string; points: string[]; since: number; brand?: boolean };

const p = (id: string, icon: Page["icon"], since: number, points: number): Page => ({
  id, icon, since, title: `tour_${id}_title`, body: `tour_${id}_body`,
  points: Array.from({ length: points }, (_, i) => `tour_${id}_p${i + 1}`),
});

const WELCOME: Page = { ...p("welcome", "letty", 0, 2), brand: true };
const WHATS_NEW: Page = { ...p("whatsnew", "letty", 0, 0), brand: true };
const END: Page = { ...p("end", "letty", 0, 1), brand: true };
const FEATURES: Page[] = [
  p("kinds", "check_check", 1, 3), p("gif", "gif", 1, 3), p("feel", "heart", 1, 3), p("stories", "clock", 1, 2),
  p("follow", "users", 1, 2), p("books", "book_open", 1, 3), p("bursts", "layers", 1, 2), p("gentle", "eye_off", 1, 2),
  p("yours", "sliders", 1, 3), p("private", "vibrate", 1, 3),
];

/** Words the web says differently (an iPhone has Face ID, no recent-apps screen block). */
const WEB_TEXT: Record<string, () => string> = {
  tour_private_p1: () => `A password lock, with ${biometricName} unlock`,
  tour_private_p2: () => "Locks again when you've been away",
  tour_private_p3: () => "Hidden in the app switcher",
};
const T = (key: string) => (WEB_TEXT[key] ? WEB_TEXT[key]!() : S(key));

let showing = false;
/** Is the tour on screen? (Other first-run prompts wait for it.) */
export function tourOpen(): boolean { return showing; }

export function tourSeenVersion(): number {
  try { return Number(localStorage.getItem(SEEN_KEY) ?? 0) || 0; } catch { return TOUR_VERSION; }
}
function markSeen() {
  try { localStorage.setItem(SEEN_KEY, String(TOUR_VERSION)); } catch { /* private mode: shows again next time */ }
}
function full(): Page[] { return [WELCOME, ...FEATURES, END]; }
function whatsNew(seen: number): Page[] {
  const fresh = FEATURES.filter((f) => f.since > seen);
  return fresh.length ? [WHATS_NEW, ...fresh, END] : [];
}

/** Should Home open the tour now? (Never seen → full; seen an older one with new pages → what's new.) */
export function tourDue(): "full" | "new" | null {
  const seen = tourSeenVersion();
  if (seen >= TOUR_VERSION) return null;
  if (seen > 0 && !whatsNew(seen).length) { markSeen(); return null; }
  return seen === 0 ? "full" : "new";
}

// ── Motion helpers ────────────────────────────────────────────────────────────

type State = { x: number; y: number; scale: number; alpha: number; blur: number };
const REST: State = { x: 0, y: 0, scale: 1, alpha: 1, blur: 0 };

/** One moving layer (View.animate()): remembers where it is, so every move starts from there. */
class Layer {
  s: State = { ...REST };
  el: HTMLElement | null = null;
  private anim: Animation | null = null;
  private frame(s: State): Keyframe {
    return { transform: `translate(${s.x}px, ${s.y}px) scale(${s.scale})`, opacity: s.alpha, filter: `blur(${s.blur}px)` };
  }
  set(to: Partial<State>) {
    this.anim?.cancel();
    this.s = { ...this.s, ...to };
    if (this.el) this.anim = this.el.animate([this.frame(this.s)], { duration: 0, fill: "forwards" });
  }
  to(to: Partial<State>, ms: number, ease: (t: number) => number, delay = 0): Promise<void> {
    if (!this.el) { this.s = { ...this.s, ...to }; return Promise.resolve(); }
    const from = { ...this.s }, end = { ...this.s, ...to };
    this.anim?.cancel();
    if (reducedMotion()) { this.set(end); return Promise.resolve(); }
    const n = Math.max(12, Math.round(ms / 16)), frames: Keyframe[] = [];
    for (let i = 0; i <= n; i++) {
      const e = ease(i / n), k = (a: number, b: number) => a + (b - a) * e;
      frames.push({ ...this.frame({ x: k(from.x, end.x), y: k(from.y, end.y), scale: k(from.scale, end.scale),
        alpha: k(from.alpha, end.alpha), blur: Math.max(0, k(from.blur, end.blur)) }), offset: i / n });
    }
    this.s = end;
    const a = this.el.animate(frames, { duration: ms, delay, fill: "both", easing: "linear" });
    this.anim = a;
    return a.finished.then(() => undefined, () => undefined);
  }
}

/** rAF tween of plain numbers (the morphing shape's box, the glow). */
function run(ms: number, ease: (t: number) => number, onFrame: (t: number) => void): () => void {
  if (reducedMotion()) { onFrame(1); return () => {}; }
  let raf = 0;
  const start = performance.now();
  const step = (now: number) => {
    const f = Math.min(1, (now - start) / ms);
    onFrame(ease(f));
    if (f < 1) raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}

/** TourDemos.pop: fade in from 0.92 with a little overshoot. */
function pop(el: Element | null | undefined) {
  if (!el || reducedMotion()) return;
  el.animate([{ opacity: 0, scale: "0.92" }, { opacity: 1, scale: "1" }],
    { duration: 260, easing: "cubic-bezier(0.34, 1.5, 0.64, 1)" });
}

/** setInterval that stops with the page (TourDemos.Demo.every). */
function useEvery(ms: number, fn: () => void, on = true) {
  const f = useRef(fn);
  f.current = fn;
  useEffect(() => {
    if (!on) return;
    const t = window.setInterval(() => f.current(), ms);
    return () => window.clearInterval(t);
  }, [ms, on]);
}

// ── Live samples (TourDemos) ──────────────────────────────────────────────────

function Author({ alias, when }: { alias: string; when: string }) {
  return <div className="td-author"><i className="td-dot" /><span className="td-small td-meta">@{alias}  ·  {when}</span></div>;
}
const Caption = ({ children }: { children: ReactNode }) => <p className="td-small td-caption">{children}</p>;

function Kinds() {
  const [state, setState] = useState(0);
  const extra = useRef<HTMLDivElement>(null);
  const [clock, setClock] = useState(0);
  const next = useCallback(() => setState((s) => (s + 1) % 3), []);
  useEvery(2600, next);
  useEffect(() => { pop(extra.current?.firstElementChild); }, [state, clock]);
  const label = ["MOOD  ·  tells people how to respond", "POLL  ·  anonymous votes, final", "UNSENT LETTER  ·  addressed to someone"][state];
  return (
    <>
      <div className="td-card" onClick={() => { haptic("tick"); next(); setClock((c) => c + 1); }}>
        <Author alias="sam" when="Tonight · 11:42 pm" />
        <p className="td-body">Should I text him back?</p>
        <div className="td-extra" ref={extra}>
          {state === 0 ? <span className="td-chip">💬  Advice wanted</span> : null}
          {state === 1 ? <div className="td-poll"><Bar label="Yes" share={0.64} /><Bar label="Wait a week" share={0.36} /></div> : null}
          {state === 2 ? <span className="td-letter">✉  To: my dad</span> : null}
        </div>
      </div>
      <Caption>{label}</Caption>
    </>
  );
}

function Bar({ label, share }: { label: string; share: number }) {
  const fill = useRef<HTMLSpanElement>(null);
  useEffect(() => run(700, (t) => 1 - (1 - t) * (1 - t), (t) => fill.current?.style.setProperty("--w", `${t * share * 100}%`)), [share]);
  return <span className="td-bar"><i ref={fill} /><span className="td-small">{label}   {Math.round(share * 100)}%</span></span>;
}

function Gif() {
  const words = "this is me on mondays";
  const over = " and tuesdays and every day ending in y, honestly all week long if I'm being real with you";
  const total = words.length + over.length;
  const [step, setStep] = useState(reducedMotion() ? total : 0);
  const [colors, setColors] = useState(["#6D5BD0", "#E8A04C", "#D2553F"]);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (reducedMotion()) return;
    const shown = Math.min(step, total);
    const t = window.setTimeout(() => setStep((s) => (s + 2 >= total + 30 ? 0 : s + 2)), shown >= total ? 90 : 45);
    return () => window.clearTimeout(t);
  }, [step, total]);
  const shown = Math.min(step, total), all = words + over;
  useEffect(() => { if (shown === words.length || shown === words.length + 1) pop(box.current); }, [shown, words.length]);
  useLayoutEffect(() => {
    box.current?.style.setProperty("--g1", colors[0]!); box.current?.style.setProperty("--g2", colors[1]!); box.current?.style.setProperty("--g3", colors[2]!);
  }, [colors]);
  return (
    <>
      <div className="td-card">
        <Author alias="you" when="Tonight · 2:17 am" />
        <p className="td-body">{all.slice(0, Math.min(shown, 100))}<s className="td-over">{shown > 100 ? all.slice(100, shown) : ""}</s></p>
        <div ref={box} className={shown >= words.length ? "td-gif" : "td-gif hidden"}
             onClick={() => {
               haptic("tick");
               const cols = ["#2F9E8F", "#6D5BD0", "#E8A04C"], r = () => cols[Math.floor(Math.random() * 3)]!;
               setColors([r(), r(), "#D2553F"]); pop(box.current);
             }}>
          <b>GIF</b>
        </div>
        <span className={shown > 100 ? "td-small td-count over" : "td-small td-count"}>{shown} / 100</span>
      </div>
      <Caption>Words + one GIF · up to 100 characters</Caption>
    </>
  );
}

function Feel() {
  const [count, setCount] = useState(12);
  const [mine, setMine] = useState<string | null>(null);
  const [picker, setPicker] = useState(false);
  const touched = useRef(false);
  const row = useRef<HTMLDivElement>(null);
  const burst = useRef<HTMLDivElement>(null);
  const lastTap = useRef(0);
  const press = useRef(0);
  const felt = (m: string | null) => { setCount((c) => c + 1); setMine(m); pop(row.current); };
  const heartBurst = () => {
    const b = burst.current;
    if (!b || reducedMotion()) return;
    b.animate([{ opacity: 1, scale: "0.3" }, { opacity: 1, scale: "1.2" }], { duration: 260, easing: "cubic-bezier(0.34, 1.56, 0.64, 1)" })
      .finished.then(() => b.animate([{ opacity: 1, scale: "1.2" }, { opacity: 0, scale: "1.5" }], { duration: 260 }), () => {});
  };
  useEvery(3200, () => { if (!touched.current) { heartBurst(); felt(null); } });
  const down = () => {
    press.current = window.setTimeout(() => {
      press.current = 0; touched.current = true; haptic();
      setPicker(true);
      const all = [null, "🫂", "🤝", "💪", "🥺"];
      const m = all[Math.floor(Math.random() * all.length)]!;
      window.setTimeout(() => { felt(m); setPicker(false); }, 900);
    }, 500);
  };
  const up = () => {
    if (!press.current) return;
    window.clearTimeout(press.current); press.current = 0;
    touched.current = true;
    const now = Date.now();
    if (now - lastTap.current < 300) { lastTap.current = 0; haptic(); felt(null); heartBurst(); return; }
    lastTap.current = now;
    window.setTimeout(() => { if (lastTap.current === now) { haptic("tick"); felt(null); } }, 300);
  };
  return (
    <>
      <div className="td-stack">
        <div className="td-card td-touch" onPointerDown={down} onPointerUp={up} onPointerLeave={() => { window.clearTimeout(press.current); press.current = 0; }}
             onContextMenu={(e) => e.preventDefault()}>
          <Author alias="nightowl" when="2h" />
          <p className="td-body">I still look for your car at every red light.</p>
          <div className="td-felt" ref={row}>
            {mine ? <span className="td-emoji">{mine}</span> : <Icon name="heart_filled" size={20} className="td-amber" />}
            <span className="td-num">{count}</span>
          </div>
          {picker ? <span className="td-picker">💛   🫂   🤝   💪   🥺</span> : null}
        </div>
        <div className="td-burst" ref={burst} aria-hidden><Icon name="heart_filled" size={72} className="td-amber" /></div>
      </div>
      <Caption>Tap · double-tap · hold</Caption>
    </>
  );
}

function Stories() {
  const names = ["sam", "mira", "owl", "kai"];
  const lines = ["Couldn't sleep again. 3am thoughts hit different.", "Told her today. Lighter already.",
    "Rain on the window, chai in hand. Okay.", "First day at the new job. Terrified."];
  const [k, setK] = useState(0);
  const [seen, setSeen] = useState<boolean[]>([false, false, false, false]);
  const [round, setRound] = useState(0);
  const words = useRef<HTMLParagraphElement>(null);
  const bar = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    if (k === 0) setSeen([false, false, false, false]);
    pop(words.current);
    if (reducedMotion()) { setSeen((s) => s.map((v, i) => v || i === k)); return; }
    const stop = run(3000, (t) => t, (t) => bar.current?.style.setProperty("--p", String(t)));
    const t = window.setTimeout(() => { setSeen((s) => s.map((v, i) => v || i === k)); setK((x) => (x + 1) % names.length); }, 3000);
    return () => { stop(); window.clearTimeout(t); };
  }, [k, round, names.length]);
  return (
    <>
      <div className="td-rings">
        {names.map((n, i) => (
          <button key={n} type="button" className={`td-ring-col${i === k ? " on" : ""}`}
                  onClick={() => { haptic("tick"); setK(i); setRound((r) => r + 1); }}>
            <span className={`td-ring c${i}${seen[i] ? " seen" : ""}`}><i /></span>
            <span className="td-small td-ring-name">@{n}</span>
          </button>
        ))}
      </div>
      <div className={`td-story s${k}`}>
        <span className="td-track"><i ref={bar} /></span>
        <span className="td-small td-who">@{names[k]}  ·  {k + 1}h</span>
        <p className="td-words" ref={words}>{lines[k]}</p>
      </div>
      <Caption>Tap a ring to watch theirs</Caption>
    </>
  );
}

function Follow() {
  const [on, setOn] = useState(false);
  const [note, setNote] = useState(false);
  const touched = useRef(false);
  const btn = useRef<HTMLButtonElement>(null);
  const noteEl = useRef<HTMLDivElement>(null);
  const toggle = () => setOn((v) => !v);
  useEffect(() => {
    pop(btn.current);
    if (!on) { setNote(false); return; }
    const t = window.setTimeout(() => setNote(true), 700);
    return () => window.clearTimeout(t);
  }, [on]);
  useEffect(() => {
    if (note && noteEl.current && !reducedMotion()) {
      noteEl.current.animate([{ opacity: 0, translate: "0 -16px" }, { opacity: 1, translate: "0 0" }], { duration: 300 });
    }
  }, [note]);
  useEvery(3000, () => { if (!touched.current) toggle(); });
  return (
    <>
      <div className="td-card td-row">
        <i className="td-face" />
        <p className="td-body td-name">@nightowl</p>
        <button ref={btn} type="button" className={on ? "td-follow on" : "td-follow"}
                onClick={() => { touched.current = true; haptic("tick"); toggle(); }}>{on ? "Following ✓" : "Follow"}</button>
      </div>
      <div ref={noteEl} className={note ? "td-small td-note" : "td-small td-note hidden"}>🔔  @nightowl just confessed</div>
      <Caption>Tap Follow</Caption>
    </>
  );
}

function Books() {
  const pages = [
    "The house was quiet in the way only old houses are. I sat on the stairs and listened for my father's cough, the one that meant he was still awake.",
    "He never came down. In the morning there was a note on the kitchen table, folded twice, in handwriting I didn't know he still had.",
    "I kept the note for eleven years. I only read it again the night I became a father myself.",
  ];
  const [n, setN] = useState(0);
  const page = useRef<HTMLDivElement>(null);
  const touched = useRef(false);
  const turn = () => {
    const el = page.current;
    if (!el || reducedMotion()) { setN((x) => (x + 1) % pages.length); return; }
    el.animate([{ transform: "perspective(900px) rotateY(0)", opacity: 1 }, { transform: "perspective(900px) rotateY(-70deg)", opacity: 0.2 }],
      { duration: 260, easing: "cubic-bezier(0.55, 0.06, 0.68, 0.19)" }).finished.then(() => {
      setN((x) => (x + 1) % pages.length);
      el.animate([{ transform: "perspective(900px) rotateY(60deg)", opacity: 0.2 }, { transform: "perspective(900px) rotateY(0)", opacity: 1 }],
        { duration: 320, easing: "cubic-bezier(0.22, 0.61, 0.36, 1)" });
    }, () => {});
  };
  useEvery(3600, () => { if (!touched.current) turn(); });
  return (
    <>
      <div className="td-page" ref={page} onClick={() => { touched.current = true; haptic("tick"); turn(); }}>
        <span className="td-small td-chapter">Chapter 2 · The note</span>
        <p className="td-reading">{pages[n]}</p>
        <span className="td-small td-folio">{n + 14}</span>
      </div>
      <Caption>Tap the page to turn it</Caption>
    </>
  );
}

function Bursts() {
  const lines = ["It started with a missed call.", "Then I found the letters.", "I never told anyone. Until now."];
  const [stacked, setStacked] = useState(false);
  const stage = useRef<HTMLDivElement>(null);
  const cards = useRef<(HTMLDivElement | null)[]>([]);
  const timer = useRef(0);
  const layout = useCallback((st: boolean, animate: boolean) => {
    const h = cards.current[0]?.offsetHeight ?? 0;
    if (!h) return;
    const gap = 8, peek = 10;
    cards.current.forEach((v, i) => {
      if (!v) return;
      const ty = st ? i * peek : i * (h + gap), sc = st ? 1 - (2 - i) * 0.05 : 1;
      const to = `translate(0px, ${ty}px) scale(${sc})`;
      if (!animate || reducedMotion()) { v.animate([{ transform: to }], { duration: 0, fill: "forwards" }); return; }
      // Spring from wherever the card is now (fanned ⇄ stacked), as TourActivity.Spring(8).
      const from = getComputedStyle(v).transform;
      const m = new DOMMatrix(from === "none" ? undefined : from);
      const ty0 = m.f, sc0 = m.a, sp = spring(8);
      v.animate(Array.from({ length: 33 }, (_, k) => {
        const e = sp(k / 32);
        return { offset: k / 32, transform: `translate(0px, ${ty0 + (ty - ty0) * e}px) scale(${sc0 + (sc - sc0) * e})` };
      }), { duration: 520, fill: "forwards" });
    });
    const target = st ? h + 2 * peek : 3 * h + 2 * gap;
    stage.current?.style.setProperty("--h", `${target}px`);
  }, []);
  useEffect(() => {
    const t = window.setTimeout(() => { setStacked(true); }, 60);
    return () => window.clearTimeout(t);
  }, []);
  useEffect(() => { layout(stacked, true); }, [stacked, layout]);
  const restart = () => { window.clearInterval(timer.current); timer.current = window.setInterval(() => setStacked((s) => !s), 2800); };
  useEffect(() => { restart(); return () => window.clearInterval(timer.current); }, []);
  const tap = () => { haptic("tick"); setStacked((s) => !s); restart(); };
  return (
    <>
      <div className="td-bursts" ref={stage} onClick={tap}>
        {lines.map((l, i) => (
          <div key={i} className={`td-card td-bcard b${i}`} ref={(el) => { cards.current[i] = el; }}>
            <Author alias="sam" when={`${i + 1} of 3`} />
            <p className="td-body">{l}</p>
          </div>
        ))}
      </div>
      <Caption>{stacked ? "Read together as one burst · tap to fan out" : "3 confessions from @sam in a row · tap to stack"}</Caption>
    </>
  );
}

function Gentle() {
  const [covered, setCovered] = useState(true);
  const pill = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    if (covered) { pop(pill.current); return; }
    const t = window.setTimeout(() => setCovered(true), 4500);
    return () => window.clearTimeout(t);
  }, [covered]);
  return (
    <>
      <div className="td-card" onClick={() => { haptic("tick"); if (covered) setCovered(false); }}>
        <Author alias="anon" when="5h" />
        <div className="td-veil">
          <p className={covered ? "td-body blurred" : "td-body"}>Some days are heavier than others. Today I couldn't get out of bed, and that's okay to say.</p>
          {covered ? <span ref={pill} className="td-small td-pill">  Sensitive · tap to read  </span> : null}
        </div>
      </div>
      <Caption>Tap to read when you're ready</Caption>
    </>
  );
}

const FONTS: [string, string][] = [["manrope", "font_manrope"], ["newsreader", "font_newsreader"], ["literata", "font_literata"],
  ["lora", "font_lora"], ["atkinson", "font_atkinson"], ["nunito", "font_nunito"], ["caveat", "font_caveat"], ["mono", "font_typewriter"]];
const TABS: IconName[] = ["letters", "book", "compose", "bell", "user"];

function Yours() {
  const [f, setF] = useState(0);
  const [tab, setTab] = useState(0);
  const sample = useRef<HTMLParagraphElement>(null);
  const sizes = [16, 18, 20];
  useEffect(() => { pop(sample.current); }, [f]);
  useEvery(2200, () => { setF((x) => x + 1); setTab((t) => (t + 1) % 3); });
  const [font, name] = FONTS[f % FONTS.length]!;
  const size = sizes[f % sizes.length]!;
  useLayoutEffect(() => { sample.current?.style.setProperty("--fs", `${size}px`); }, [size]);
  const tabKeys = [0, 1, 4];
  return (
    <>
      <div className="td-card" onClick={() => { haptic("tick"); setF((x) => x + 1); }}>
        <p ref={sample} className={`td-body td-font-${font}`}>I still look for your car at every red light.</p>
        <span className="td-small td-meta">Aa · {S(name)} · {size}</span>
      </div>
      <div className="td-tabbar">
        {TABS.map((icon, i) => (
          <button key={icon} type="button" className={i === 2 ? "td-tab compose" : tabKeys[tab] === i ? "td-tab on" : "td-tab"}
                  onClick={() => { if (i !== 2) { haptic("tick"); setTab(Math.max(0, tabKeys.indexOf(i))); } }} aria-label={icon}>
            <Icon name={icon} size={i === 2 ? 22 : 24} />
          </button>
        ))}
      </div>
      <Caption>Tap the card for another font · tap a tab</Caption>
    </>
  );
}

function Locked() {
  const [lock, setLock] = useState(false);
  const phone = useRef<HTMLDivElement>(null);
  const lockEl = useRef<HTMLDivElement>(null);
  const shakeLock = useCallback(() => {
    if (lock) return;
    const el = phone.current;
    if (!el || reducedMotion()) { setLock(true); return; }
    el.animate(Array.from({ length: 25 }, (_, k) => ({ offset: k / 24, rotate: `${Math.sin((k / 24) * Math.PI * 6) * 6}deg` })),
      { duration: 480 }).finished.then(() => setLock(true), () => {});
  }, [lock]);
  useEffect(() => { const t = window.setTimeout(shakeLock, 900); return () => window.clearTimeout(t); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEvery(4200, shakeLock);
  useEffect(() => { if (lock) pop(lockEl.current); }, [lock]);
  return (
    <>
      <div className="td-phone" ref={phone}>
        <div className="td-card">
          <Author alias="you" when="now" />
          <p className="td-body">The thing I never told anyone…</p>
        </div>
        {lock ? (
          <div className="td-lock" ref={lockEl} onClick={() => { haptic(); setLock(false); }}>
            <Icon name="fingerprint" size={44} className="td-amber" />
            <span className="td-small td-sec">Locked · {biometricName} to open</span>
          </div>
        ) : null}
      </div>
      <Caption>Locks when you've been away · {biometricName} to open</Caption>
    </>
  );
}

const DEMOS: Record<string, () => ReactNode> = {
  kinds: () => <Kinds />, gif: () => <Gif />, feel: () => <Feel />, stories: () => <Stories />, follow: () => <Follow />,
  books: () => <Books />, bursts: () => <Bursts />, gentle: () => <Gentle />, yours: () => <Yours />, private: () => <Locked />,
};

// ── The tour ──────────────────────────────────────────────────────────────────

export function Tour({ mode: initial = "full", onDone }: { mode?: "full" | "new"; onDone: () => void }) {
  const [mode, setMode] = useState(initial);
  const [pages, setPages] = useState<Page[]>(() => {
    const fresh = initial === "new" ? whatsNew(tourSeenVersion()) : [];
    return fresh.length ? fresh : full();
  });
  const [index, setIndex] = useState(0);
  const [dir, setDir] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const stageRoot = useRef<HTMLDivElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const morph = useRef<HTMLDivElement>(null);
  const glowEl = useRef<HTMLDivElement>(null);
  const textEl = useRef<HTMLDivElement>(null);
  const contentEl = useRef<HTMLDivElement>(null);
  const bottom = useRef<HTMLDivElement>(null);
  const text = useRef(new Layer()), content = useRef(new Layer()), morphL = useRef(new Layer()), glowL = useRef(new Layer());
  const morphBox = useRef<{ l: number; t: number; w: number; h: number; r: number; tone: number } | null>(null);
  const stopMorph = useRef<() => void>(() => {});
  const busy = useRef(false);
  const page = pages[index]!;
  const last = index === pages.length - 1;
  const hasDemo = !!DEMOS[page.id];

  const finish = useCallback(() => { markSeen(); onDone(); }, [onDone]);
  useEffect(() => {
    showing = true;
    window.dispatchEvent(new Event("confess:tour-state"));
    return () => { showing = false; window.dispatchEvent(new Event("confess:tour-state")); };
  }, []);

  // ── the shape that morphs from page to page ──
  const morphAround = useCallback((animate: boolean) => {
    const c = contentEl.current, m = morph.current;
    if (!stage.current || !c || !m) return;
    // Layout boxes (offsets), not the screen rect: the content may be mid-animation (moved, scaled).
    const st = stage.current!;
    const r = { left: st.offsetLeft + c.offsetLeft, top: st.offsetTop + c.offsetTop, width: c.offsetWidth, height: c.offsetHeight };
    let box: { l: number; t: number; w: number; h: number; r: number; tone: number };
    const tone = page.brand ? 1 : 0;
    if (hasDemo) box = { l: r.left - 16, t: r.top - 16, w: r.width + 32, h: r.height + 32, r: 30, tone };
    else {
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      box = { l: cx - 70, t: cy - 70, w: 140, h: 140, r: 70, tone };
    }
    const from = morphBox.current ?? { l: box.l + box.w / 2, t: box.t + box.h / 2, w: 0, h: 0, r: 0, tone };
    stopMorph.current();
    const paint = (b: typeof box) => {
      m.style.setProperty("--ml", `${b.l}px`); m.style.setProperty("--mt", `${b.t}px`);
      m.style.setProperty("--mw", `${Math.max(0, b.w)}px`); m.style.setProperty("--mh", `${Math.max(0, b.h)}px`);
      m.style.setProperty("--mr", `${Math.min(b.r, Math.max(0, Math.min(b.w, b.h)) / 2)}px`);
      m.style.setProperty("--mtone", String(b.tone));
    };
    morphBox.current = box;
    if (!animate) { paint(box); return; }
    const k = (a: number, b: number, t: number) => a + (b - a) * t;
    stopMorph.current = run(760, spring(7), (t) => paint({ l: k(from.l, box.l, t), t: k(from.t, box.t, t), w: k(from.w, box.w, t),
      h: k(from.h, box.h, t), r: k(from.r, box.r, t), tone: k(from.tone, box.tone, Math.min(1, t)) }));
  }, [hasDemo, page.brand]);

  // Samples change size as they play: the frame keeps hugging them.
  useEffect(() => {
    const c = contentEl.current;
    if (!c || typeof ResizeObserver === "undefined") return;
    let first = true;
    const ro = new ResizeObserver(() => {
      if (first) { first = false; return; }
      if (c === contentEl.current && c.isConnected) morphAround(true);     // not the page that just left
    });
    ro.observe(c);
    return () => ro.disconnect();
  }, [morphAround, index]);

  const glowBase = useRef(0);
  const glowAt = useCallback((i: number, animate: boolean) => {
    const w = root.current?.clientWidth ?? 393, h = stageRoot.current?.clientHeight ?? 500, g = 340;
    const span = w - g * 0.5, f = pages.length <= 1 ? 0 : i / (pages.length - 1);
    const to = { x: -g * 0.25 + span * f, y: (i % 2 === 0 ? 0.1 : 0.35) * h };
    glowBase.current = to.x;
    if (animate) void glowL.current.to(to, 900, spring(6)); else glowL.current.set(to);
  }, [pages.length]);

  // ── a page arrives ──
  useLayoutEffect(() => {
    text.current.el = textEl.current; content.current.el = contentEl.current;
    morphL.current.el = morph.current; glowL.current.el = glowEl.current;
    const motion = !reducedMotion();
    text.current.set(REST);
    morphL.current.set({ x: 0 });
    morphAround(motion && morphBox.current != null);
    glowAt(index, motion);
    if (!motion) { content.current.set(REST); return; }
    const sp8 = spring(8);
    const from = (dir === 0 ? 1 : dir) * 56;
    [...(textEl.current?.children ?? [])].forEach((v, k) => {
      if (v.classList.contains("td-skip")) return;
      v.animate(Array.from({ length: 40 }, (_, i) => ({ offset: i / 39, opacity: Math.min(1, sp8(i / 39) * 1.6), translate: `${from * (1 - sp8(i / 39))}px 0` })),
        { duration: 620, delay: 50 * k, fill: "backwards" });
    });
    textEl.current?.querySelectorAll(".tour-point").forEach((v, c) => {
      v.animate(Array.from({ length: 30 }, (_, i) => ({ offset: i / 29, opacity: Math.min(1, sp8(i / 29) * 1.4), translate: `0 ${10 * (1 - sp8(i / 29))}px` })),
        { duration: 420, delay: 260 + 90 * c, fill: "backwards" });
    });
    const w = root.current?.clientWidth ?? 393;
    content.current.set({ x: (dir === 0 ? 0 : dir) * w * 0.4, alpha: 0, scale: 1.05, blur: 16 });
    void content.current.to({ x: 0, alpha: 1, scale: 1, blur: 0 }, 720, sp8, 90);
    busy.current = false;
  }, [index, pages]); // eslint-disable-line react-hooks/exhaustive-deps

  const go = useCallback((to: number) => {
    if (busy.current) return;
    if (to < 0 || to >= pages.length) { settle(); return; }
    if (to === index) return;
    const d = to > index ? 1 : -1;
    if (reducedMotion()) { setDir(d); setIndex(to); return; }
    busy.current = true;
    const w = root.current?.clientWidth ?? 393;
    void text.current.to({ x: -d * w * 0.3, alpha: 0 }, 190, accelerate(1.4));
    void content.current.to({ x: -d * w * 0.45, alpha: 0, scale: 0.92, blur: 14 }, 210, accelerate(1.4)).then(() => { setDir(d); setIndex(to); });
    void morphL.current.to({ x: 0 }, 210, (t) => t);
  }, [index, pages.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const settle = () => {
    void text.current.to({ x: 0, alpha: 1 }, 520, spring(9));
    void content.current.to({ x: 0, scale: 1, alpha: 1 }, 560, spring(9));
    void morphL.current.to({ x: 0 }, 560, spring(9));
    glowAt(index, true);
  };

  // ── dragging between pages (window listeners: the finger may leave the page) ──
  const drag = useRef<{ x: number; y: number; dx: number; on: boolean; vx: number; lx: number; lt: number } | null>(null);
  const handlers = useRef<{ move: (e: PointerEvent) => void; up: (e: PointerEvent) => void }>({ move: () => {}, up: () => {} });
  handlers.current.move = (e: PointerEvent) => {
    const d = drag.current;
    if (!d) return;
    let dx = e.clientX - d.x;
    const dy = e.clientY - d.y;
    if (!d.on) {
      if (Math.abs(dx) > 16 && Math.abs(dx) > Math.abs(dy) * 1.3) {
        d.on = true; d.x = e.clientX - Math.sign(dx); dx = Math.sign(dx);
      } else return;
    }
    const now = performance.now();
    d.vx = ((e.clientX - d.lx) / Math.max(1, now - d.lt)) * 1000; d.lx = e.clientX; d.lt = now;
    const w = Math.max(1, root.current?.clientWidth ?? 393);
    if ((dx < 0 && index === pages.length - 1) || (dx > 0 && index === 0)) dx *= 0.3;   // rubber band at the ends
    d.dx = dx;
    const f = Math.min(1, Math.abs(dx) / w);
    text.current.set({ x: dx * 0.6, alpha: 1 - f * 0.9 });
    content.current.set({ x: dx, scale: 1 - f * 0.08 });
    morphL.current.set({ x: dx * 0.35 });
    glowL.current.set({ x: glowBase.current + dx * 0.15 });
  };
  handlers.current.up = (e: PointerEvent) => {
    const d = drag.current;
    drag.current = null;
    window.removeEventListener("pointermove", onWinMove);
    window.removeEventListener("pointerup", onWinUp);
    window.removeEventListener("pointercancel", onWinUp);
    if (!d?.on) return;
    // A drag isn't also a tap on the sample under the finger.
    const eat = (ev: Event) => { ev.stopPropagation(); ev.preventDefault(); };
    window.addEventListener("click", eat, { capture: true, once: true });
    window.setTimeout(() => window.removeEventListener("click", eat, { capture: true }), 80);
    const w = root.current?.clientWidth ?? 393;
    const to = d.dx < 0 ? index + 1 : index - 1;
    const far = Math.abs(d.dx) > w * 0.22, flung = Math.abs(d.vx) > 500 && Math.sign(d.vx) === Math.sign(d.dx);
    if (e.type === "pointerup" && (far || flung) && to >= 0 && to < pages.length) { haptic("tick"); go(to); }
    else settle();
  };
  const onWinMove = useCallback((e: PointerEvent) => handlers.current.move(e), []);
  const onWinUp = useCallback((e: PointerEvent) => handlers.current.up(e), []);
  const onDown = (e: RPointerEvent) => {
    if (busy.current || (bottom.current && e.clientY >= bottom.current.getBoundingClientRect().top)) return;
    drag.current = { x: e.clientX, y: e.clientY, dx: 0, on: false, vx: 0, lx: e.clientX, lt: performance.now() };
    window.addEventListener("pointermove", onWinMove);
    window.addEventListener("pointerup", onWinUp);
    window.addEventListener("pointercancel", onWinUp);
  };
  useEffect(() => () => {
    window.removeEventListener("pointermove", onWinMove);
    window.removeEventListener("pointerup", onWinUp);
    window.removeEventListener("pointercancel", onWinUp);
  }, [onWinMove, onWinUp]);

  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight") go(index + 1);
      else if (e.key === "ArrowLeft") go(index - 1);
      else if (e.key === "Escape") finish();
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [go, index, finish]);

  // Find it: the path lights up step by step, rests, and again.
  const where = A(`tour_where_${page.id}`);
  const [lit, setLit] = useState(-1);
  useEffect(() => {
    setLit(reducedMotion() ? where.length : -1);
    if (!where.length || reducedMotion()) return;
    let at = 0, t = window.setTimeout(function walk() {
      if (at >= where.length) {
        t = window.setTimeout(() => { setLit(-1); at = 0; t = window.setTimeout(walk, 500); }, 2600);
        return;
      }
      setLit(at++);
      t = window.setTimeout(walk, 520);
    }, 700);
    return () => window.clearTimeout(t);
  }, [index, pages]); // eslint-disable-line react-hooks/exhaustive-deps

  // The title's words rise in, one after another.
  const words = T(page.title).split(" ");
  const titleRef = useRef<HTMLHeadingElement>(null);
  useLayoutEffect(() => {
    const spans = titleRef.current?.querySelectorAll("span");
    if (!spans || reducedMotion()) return;
    const total = 160 * words.length + 260, n = words.length + 1;
    spans.forEach((s, i) => s.animate([{ opacity: 0 }, { opacity: 1 }], { duration: total / n, delay: (total * i) / n, fill: "backwards" }));
  }, [index, pages]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="tour" ref={root} role="dialog" aria-modal="true" aria-label={T(page.title)}>
      <div className="tour-top">
        <span className="t-meta-small tour-step">{S("tour_step", index + 1, pages.length)}</span>
        <Btn kind="ghost" onClick={finish} className={last ? "tour-skip hide" : "tour-skip"}>{S("tour_skip")}</Btn>
      </div>
      <div className="tour-stage-root" ref={stageRoot} onPointerDown={onDown}
           aria-label={`Page ${index + 1} of ${pages.length}`}>
        <div className="tour-glow" ref={glowEl} aria-hidden />
        <div className="tour-morph" ref={morph} aria-hidden />
        <div className="tour-page">
          <div className="tour-text" ref={textEl} key={`t${index}${pages.length}`}>
            {page.since >= TOUR_VERSION && !page.brand ? <span className="tour-badge"><Tag>{S("tour_new")}</Tag></span> : null}
            <h1 className="t-headline tour-title" ref={titleRef} aria-label={T(page.title)}>
              {words.map((w, i) => <span key={i}>{w}{i < words.length - 1 ? " " : ""}</span>)}
            </h1>
            <p className="t-body tour-body">{T(page.body)}</p>
            {where.length ? (
              <div className="tour-where">
                <span className="t-meta-small tour-where-label"><i className="k-dot" aria-hidden />{S("tour_where")}</span>
                <div className="tour-path">
                  {where.map((w, i) => (
                    <span key={i} className="tour-path-step">
                      {i > 0 ? <span className="tour-arrow" aria-hidden>›</span> : null}
                      <span className={i === lit ? "tour-pill now" : i < lit || lit >= where.length ? "tour-pill on" : "tour-pill"}>{w}</span>
                    </span>
                  ))}
                </div>
              </div>
            ) : null}
            {!hasDemo && page.points.length ? (
              <div className="tour-points td-skip">
                {page.points.map((pt) => <p key={pt} className="t-body-medium tour-point"><i className="k-dot" aria-hidden />{T(pt)}</p>)}
              </div>
            ) : null}
          </div>
          <div className="tour-stage" ref={stage}>
            <div ref={contentEl} key={`c${index}${pages.length}`} className={hasDemo ? "tour-demo" : "tour-disc"}>
              {hasDemo ? DEMOS[page.id]!() : (
                <span className="tour-icon">
                  {page.icon === "letty" ? <Art name="logo_letty" size={72} /> : <Icon name={page.icon} size={48} className="td-amber" />}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>
      <div className="tour-bottom" ref={bottom}>
        <div className="tour-dots" aria-hidden>{pages.map((_, i) => <i key={i} className={i === index ? "on" : ""} />)}</div>
        <Btn block icon={last ? undefined : "arrow_right"}
             onClick={() => { haptic("tick"); if (!last) go(index + 1); else finish(); }}>{last ? S("tour_done") : S("tour_next")}</Btn>
        {last && mode === "new" ? (
          <Btn kind="link" className="tour-full" onClick={() => { haptic("tick"); setMode("full"); setPages(full()); setDir(0); setIndex(0); }}>
            {S("tour_full")}
          </Btn>
        ) : null}
      </div>
    </div>
  );
}

