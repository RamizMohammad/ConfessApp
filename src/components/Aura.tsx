/**
 * Every avatar in the app: the 12 preset "auras" (same colours, focus and grain as the Android
 * AuraAvatarView) and designed avatars ("av1:…", drawn by AvatarArt). Uploaded photos (old
 * accounts) show their aura — the page only loads images from itself and GIPHY.
 */
import { useId } from "react";
import { AURAS, resolveAura } from "../lib/auras.ts";
import { javaHash as hash } from "../lib/javaRandom.ts";
import { isSpec } from "../lib/avatar.ts";
import { AvatarArt } from "./art.tsx";

export { AURAS, resolveAura };
export type { AuraDef } from "../lib/auras.ts";

/** Deterministic grain so it never shimmers between renders. */
function grain(seed: number, count: number): { x: number; y: number; a: number; light: boolean }[] {
  let s = seed >>> 0 || 1;
  const rnd = () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296);
  return Array.from({ length: count }, (_, i) => {
    const ang = rnd() * Math.PI * 2;
    const dist = Math.sqrt(rnd()) * 50;
    return { x: 50 + Math.cos(ang) * dist, y: 50 + Math.sin(ang) * dist, a: (10 + Math.floor(rnd() * 26)) / 255, light: i % 2 === 0 };
  });
}

export function Aura({ value, size = 40, label }: { value: string | null | undefined; size?: number; label?: string }) {
  return isSpec(value) ? <AvatarArt value={value!} size={size} label={label} /> : <AuraDisc value={value} size={size} label={label} />;
}

function AuraDisc({ value, size, label }: { value: string | null | undefined; size: number; label?: string }) {
  const a = resolveAura(value);
  const uid = useId().replace(/:/g, "");
  const dots = grain(hash(a.id), 60);
  return (
    <svg className="aura" width={size} height={size} viewBox="0 0 100 100" role={label ? "img" : undefined}
         aria-label={label} aria-hidden={label ? undefined : true}>
      <defs>
        <radialGradient id={`g${uid}`} cx={50 + a.fx * 50} cy={50 + a.fy * 50} r={57.5} gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor={a.glow} />
          <stop offset="0.36" stopColor={a.mid} />
          <stop offset="1" stopColor={a.base} />
        </radialGradient>
        <radialGradient id={`h${uid}`} cx={32.5} cy={29} r={35} gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#fff" stopOpacity={40 / 255} />
          <stop offset="1" stopColor="#fff" stopOpacity={0} />
        </radialGradient>
      </defs>
      <circle cx="50" cy="50" r="50" fill={`url(#g${uid})`} />
      <circle cx="50" cy="50" r="50" fill={`url(#h${uid})`} />
      {dots.map((d, i) => (
        <circle key={i} cx={d.x} cy={d.y} r={0.8} fill={d.light ? "#fff" : "#000"} fillOpacity={d.a} />
      ))}
      <circle cx="50" cy="50" r="49.6" fill="none" stroke="#fff" strokeOpacity={28 / 255} strokeWidth="0.8" />
    </svg>
  );
}
