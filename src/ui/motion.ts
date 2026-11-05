/**
 * Android's View.animate() for the web: tween an element's translation, scale, rotation, alpha
 * and blur with any Android interpolator (sampled into keyframes, so springs and overshoots are
 * exact). Runs on the Web Animations API — no inline style attributes, which the CSP forbids.
 *
 *   tween(el, { y: [14, 0], alpha: [0, 1] }, 520, { delay: 80 * i, ease: EASE.decelerate_quad })
 */
import { EASE, reducedMotion } from "./Art.tsx";

export type Range = [number, number];
export type Props = {
  x?: Range; y?: Range; scale?: Range; scaleX?: Range; scaleY?: Range; rotate?: Range; alpha?: Range; blur?: Range;
};
export type Ease = (t: number) => number;

/** AccelerateInterpolator(f) and DecelerateInterpolator(f). */
export const accelerate = (f = 1): Ease => (t) => t ** (2 * f);
export const decelerate = (f = 1): Ease => (t) => 1 - (1 - t) ** (2 * f);

const lerp = (r: Range | undefined, e: number, fallback: number) => (r ? r[0] + (r[1] - r[0]) * e : fallback);

function frame(p: Props, e: number): Keyframe {
  const sx = lerp(p.scaleX ?? p.scale, e, 1), sy = lerp(p.scaleY ?? p.scale, e, 1);
  const k: Keyframe = {
    transform: `translate(${lerp(p.x, e, 0)}px, ${lerp(p.y, e, 0)}px) rotate(${lerp(p.rotate, e, 0)}deg) scale(${sx}, ${sy})`,
  };
  if (p.alpha) k.opacity = lerp(p.alpha, e, 1);
  if (p.blur) k.filter = `blur(${Math.max(0, lerp(p.blur, e, 0))}px)`;
  return k;
}

/**
 * Animate el from the first to the second value of each range. Resolves when done (or at once
 * with Reduce Motion, after jumping to the end). The end state is kept (fill: forwards).
 */
export function tween(el: Element | null | undefined, p: Props, ms: number,
  opt: { delay?: number; ease?: Ease; steps?: number } = {}): Promise<void> {
  if (!el) return Promise.resolve();
  const ease = opt.ease ?? EASE.accelerate_decelerate!;
  const n = opt.steps ?? Math.max(12, Math.round(ms / 16));
  const frames: Keyframe[] = [];
  for (let i = 0; i <= n; i++) frames.push({ ...frame(p, ease(i / n)), offset: i / n });
  const quick = reducedMotion();
  const a = el.animate(frames, {
    duration: quick ? 1 : ms, delay: quick ? 0 : opt.delay ?? 0, fill: "both", easing: "linear",
  });
  return a.finished.then(() => undefined, () => undefined);
}

/** Drop every running tween on el (back to its stylesheet state). */
export function settle(el: Element | null | undefined): void {
  el?.getAnimations().forEach((a) => a.cancel());
}
