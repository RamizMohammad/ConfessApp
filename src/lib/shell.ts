/**
 * The iPhone / iPad app shell: what makes the Home Screen web app behave like the native one.
 *
 *  - html classes: .standalone (opened from the Home Screen), .ios, .kb-open (keyboard up)
 *  - CSS variables: --kb (keyboard height), --vh (visible height) — set through the CSSOM
 *  - edge swipe-back (standalone has no browser back gesture): drag from the left edge → back()
 *  - html[data-nav] = push | pop | replace, so screens play Android's open / close animations
 *  - lockScroll(): no page scroll behind sheets and dialogs
 *  - useWide(): the iPad / landscape breakpoint (600 dp, Android's sw600dp)
 */
import { useEffect, useState } from "react";
import { isIOS, isStandalone } from "./device.ts";

const root = () => document.documentElement;

let locks = 0;
/** Freeze the page behind a layer. Returns the unlock function (use as an effect cleanup). */
export function lockScroll(): () => void {
  locks += 1;
  root().classList.add("scroll-locked");
  let done = false;
  return () => {
    if (done) return;
    done = true;
    locks = Math.max(0, locks - 1);
    if (!locks) root().classList.remove("scroll-locked");
  };
}

export const WIDE = "(min-width: 600px)";

export function useWide(): boolean {
  const q = typeof matchMedia === "function" ? matchMedia(WIDE) : null;
  const [wide, setWide] = useState(!!q?.matches);
  useEffect(() => {
    if (!q) return;
    const on = () => setWide(q.matches);
    q.addEventListener("change", on);
    return () => q.removeEventListener("change", on);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return wide;
}

/** Mark how the last navigation happened, for the screen animations in kit.css. */
export function setNavDirection(d: "push" | "pop" | "replace"): void {
  if (typeof document !== "undefined") root().dataset.nav = d;
}

function keyboard(): void {
  const vv = window.visualViewport;
  if (!vv) return;
  const update = () => {
    const kb = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    root().style.setProperty("--kb", `${kb}px`);
    root().style.setProperty("--vh", `${Math.round(vv.height)}px`);
    root().classList.toggle("kb-open", kb > 80);
  };
  vv.addEventListener("resize", update);
  vv.addEventListener("scroll", update);
  update();
}

/**
 * Edge swipe-back, like UINavigationController: a drag that starts within 20 px of the left edge,
 * moves mostly sideways, and ends past a third of the screen (or flicks) goes back.
 * The page follows the finger through --swipe; html.swiping turns transitions off meanwhile.
 */
function swipeBack(canGoBack: () => boolean, goBack: () => void): void {
  let start: { x: number; y: number; t: number } | null = null;
  let active = false;
  const reset = () => {
    start = null;
    active = false;
    root().classList.remove("swiping");
    root().style.setProperty("--swipe", "0px");
  };
  window.addEventListener("touchstart", (e) => {
    const t = e.touches[0];
    if (e.touches.length !== 1 || !t || t.clientX > 20 || !canGoBack() || root().classList.contains("scroll-locked")) return;
    start = { x: t.clientX, y: t.clientY, t: performance.now() };
  }, { passive: true });
  window.addEventListener("touchmove", (e) => {
    const t = e.touches[0];
    if (!start || !t) return;
    const dx = t.clientX - start.x, dy = t.clientY - start.y;
    if (!active) {
      if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) { start = null; return; }
      if (dx < 10) return;
      active = true;
      root().classList.add("swiping");
    }
    root().style.setProperty("--swipe", `${Math.max(0, dx)}px`);
  }, { passive: true });
  window.addEventListener("touchend", (e) => {
    const t = e.changedTouches[0];
    if (!start || !active || !t) { reset(); return; }
    const dx = t.clientX - start.x;
    const v = dx / Math.max(1, performance.now() - start.t);
    const go = dx > window.innerWidth / 3 || (v > 0.5 && dx > 40);
    if (go) {
      root().classList.remove("swiping");
      root().classList.add("swiped");
      setNavDirection("pop");
      goBack();
      window.setTimeout(() => { root().classList.remove("swiped"); root().style.setProperty("--swipe", "0px"); }, 30);
      start = null;
      active = false;
    } else reset();
  }, { passive: true });
  window.addEventListener("touchcancel", reset, { passive: true });
}

/** Called once at start-up (main.tsx). */
export function installShell(nav: { canGoBack: () => boolean; back: () => void }): void {
  const r = root();
  r.classList.toggle("ios", isIOS);
  r.classList.toggle("standalone", isStandalone());
  keyboard();
  if (isStandalone()) swipeBack(nav.canGoBack, nav.back);
  // Long-press on links / images shouldn't open Safari's preview inside the app.
  if (isStandalone()) r.classList.add("no-callout");
}
