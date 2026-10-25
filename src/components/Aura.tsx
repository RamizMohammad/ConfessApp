/**
 * The 12 preset "aura" avatars — same colours, focus and grain as the Android AuraAvatarView.
 * Designed avatars ("av1:…") show their backdrop aura until the avatar studio renderer
 * is ported (phase 4) — the same placeholder the Android app shows while one loads.
 */
import { useId } from "react";

export const AURAS = [
  { id: "aura_01", name: "Deep Indigo", glow: "#8C7CF0", mid: "#3A2F86", base: "#120F2A", fx: -0.2, fy: -0.25 },
  { id: "aura_02", name: "Dusk Emerald", glow: "#7FD3A8", mid: "#1F6B4E", base: "#08201A", fx: 0.15, fy: -0.2 },
  { id: "aura_03", name: "Sapphire Ring", glow: "#7FC4F0", mid: "#1E4E86", base: "#0A1630", fx: 0, fy: 0 },
  { id: "aura_04", name: "Eclipsed Amber", glow: "#FFD08A", mid: "#E8A04C", base: "#3A2208", fx: -0.15, fy: -0.2 },
  { id: "aura_05", name: "Nocturnal Cobalt", glow: "#6E8BFF", mid: "#22307A", base: "#0A0E2A", fx: 0.2, fy: 0.15 },
  { id: "aura_06", name: "Bronze Twilight", glow: "#E0A070", mid: "#7A4426", base: "#24120A", fx: 0.25, fy: -0.25 },
  { id: "aura_07", name: "Amethyst Fog", glow: "#C79BF0", mid: "#6A3A8C", base: "#1C0F28", fx: -0.1, fy: 0.1 },
  { id: "aura_08", name: "Charcoal Pearl", glow: "#D9D6DE", mid: "#55525C", base: "#16151A", fx: -0.25, fy: -0.3 },
  { id: "aura_09", name: "Smoky Topaz", glow: "#E8C27A", mid: "#6B5426", base: "#1E1708", fx: 0.1, fy: 0.2 },
  { id: "aura_10", name: "Midnight Teal", glow: "#6FD6D0", mid: "#1B5F64", base: "#071C1E", fx: -0.2, fy: 0.2 },
  { id: "aura_11", name: "Ember Moss", glow: "#C9D27A", mid: "#556226", base: "#141A08", fx: 0.2, fy: -0.1 },
  { id: "aura_12", name: "Plum Ash", glow: "#E08AB0", mid: "#6A2E4C", base: "#1E0C16", fx: -0.05, fy: -0.3 },
] as const;

export type AuraDef = (typeof AURAS)[number];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;   // Java String.hashCode
  return h;
}

/** Never-null aura for any avatar value (mirrors Aura.resolve on Android). */
export function resolveAura(value: string | null | undefined): AuraDef {
  const exact = AURAS.find((a) => a.id === value?.trim().toLowerCase());
  if (exact) return exact;
  if (value?.startsWith("av1:")) {
    const d = /(?:^av1:|\.)d(\d{1,2})/.exec(value);
    return AURAS[(d ? Number(d[1]) : 3) % AURAS.length]!;
  }
  const h = hash(value ?? "");
  return AURAS[((h % AURAS.length) + AURAS.length) % AURAS.length]!;
}

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
