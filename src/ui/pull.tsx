/**
 * SwipeRefreshLayout for the web: pull down at the top of a list, a disc with the amber spinner
 * follows the finger (it draws its arc as you pull), and past the line it refreshes.
 * Touch only (a trackpad / mouse never pulls), and only while the list is scrolled to the top.
 */
import { useEffect, useRef, useState, type RefObject } from "react";
import { reducedMotion } from "./Art.tsx";
import { haptic } from "./kit.tsx";

const TRIGGER = 64;   // SwipeRefreshLayout's default distance to trigger (dp)
const MAX = 120;

export function usePullToRefresh(area: RefObject<HTMLElement | null>, onRefresh: () => Promise<unknown>, opt: {
  enabled: boolean;
  /** The element that scrolls (null: the page). */
  scroller?: () => HTMLElement | null;
}) {
  const [refreshing, setRefreshing] = useState(false);
  const disc = useRef<HTMLDivElement>(null);
  const live = useRef({ onRefresh, opt, refreshing });
  live.current = { onRefresh, opt, refreshing };

  const place = (d: number, animate: boolean) => {
    const el = disc.current;
    if (!el) return;
    el.classList.toggle("settle", animate);
    el.style.setProperty("--pull", `${d}px`);
    el.style.setProperty("--arc", String(Math.min(1, d / TRIGGER)));
    el.classList.toggle("armed", d >= TRIGGER);
  };

  useEffect(() => {
    const el = area.current;
    if (!el) return;
    let startY: number | null = null, startX = 0, pulling = false, dist = 0;
    const top = () => {
      const s = live.current.opt.scroller?.();
      return (s ? s.scrollTop : window.scrollY) <= 0;
    };
    const start = (e: TouchEvent) => {
      if (!live.current.opt.enabled || live.current.refreshing || e.touches.length !== 1 || !top()) { startY = null; return; }
      if ((e.target as Element).closest("[aria-modal='true'], .rx, .scroll-x")) { startY = null; return; }
      startY = e.touches[0]!.clientY;
      startX = e.touches[0]!.clientX;
      pulling = false;
      dist = 0;
    };
    const move = (e: TouchEvent) => {
      if (startY === null) return;
      const t = e.touches[0]!;
      const dy = t.clientY - startY, dx = t.clientX - startX;
      if (!pulling) {
        if (dy < 6 || Math.abs(dx) > Math.abs(dy) || !top()) { if (dy < 0 || Math.abs(dx) > 10) startY = null; return; }
        pulling = true;
      }
      e.preventDefault();
      const was = dist;
      dist = Math.min(MAX, dy * 0.5);
      if ((was < TRIGGER) !== (dist < TRIGGER)) haptic("tick");
      place(dist, false);
    };
    const end = () => {
      if (startY === null || !pulling) { startY = null; return; }
      startY = null;
      pulling = false;
      if (dist >= TRIGGER) {
        setRefreshing(true);
        place(TRIGGER, true);
        const t0 = performance.now();
        void Promise.resolve(live.current.onRefresh()).finally(() => {
          // Keep the spinner up a beat so it doesn't flash.
          window.setTimeout(() => { setRefreshing(false); place(0, true); }, Math.max(0, 450 - (performance.now() - t0)));
        });
      } else place(0, true);
      dist = 0;
    };
    el.addEventListener("touchstart", start, { passive: true });
    el.addEventListener("touchmove", move, { passive: false });
    el.addEventListener("touchend", end, { passive: true });
    el.addEventListener("touchcancel", end, { passive: true });
    return () => {
      el.removeEventListener("touchstart", start);
      el.removeEventListener("touchmove", move);
      el.removeEventListener("touchend", end);
      el.removeEventListener("touchcancel", end);
    };
  }, [area]);

  const indicator = (
    <div ref={disc} className={`ptr${refreshing ? " spinning" : ""}${reducedMotion() ? " still" : ""}`} aria-hidden={!refreshing}
         role={refreshing ? "progressbar" : undefined} aria-label={refreshing ? "Refreshing" : undefined}>
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden>
        <circle cx="12" cy="12" r="8.5" fill="none" strokeWidth="2.5" strokeLinecap="round" pathLength="1" />
      </svg>
    </div>
  );
  return { indicator, refreshing };
}
