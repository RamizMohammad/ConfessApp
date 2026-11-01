/**
 * The 12 preset "aura" colours — same colours and focus as the Android Aura enum, in the same
 * order (banners and designed avatars refer to them by position).
 */
import { javaHash } from "./javaRandom.ts";

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

/** Never-null aura for any avatar value (mirrors Aura.resolve on Android). */
export function resolveAura(value: string | null | undefined): AuraDef {
  const exact = AURAS.find((a) => a.id === value?.trim().toLowerCase());
  if (exact) return exact;
  if (value?.startsWith("av1:")) {
    const d = /(?:^av1:|\.)d(\d{1,2})/.exec(value);
    return AURAS[(d ? Number(d[1]) : 3) % AURAS.length]!;
  }
  const h = javaHash(value ?? "");
  return AURAS[((h % AURAS.length) + AURAS.length) % AURAS.length]!;
}

