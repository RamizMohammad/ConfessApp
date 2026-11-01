/**
 * The confession graph's look — a port of the Android GraphStyle. Saved on the server as
 * "g1:<palette>.<shape>" ("" = default: amber, rounded) and shown to everyone who sees the graph.
 * Keep in sync with streak_service.PALETTES / SHAPES on the server.
 */
export type PaletteKey = "amber" | "ember" | "ocean" | "mint" | "rose" | "violet" | "mono";
export type ShapeKey = "round" | "square" | "circle" | "heart" | "diamond";

/** Levels 1–4 as [#rrggbb, opacity]; level 0 (an empty day) is the same for every palette. */
export const PALETTES: { key: PaletteKey; name: string; levels: [string, number][] }[] = [
  { key: "amber", name: "Amber", levels: [["#E8A04C", 0x59 / 255], ["#E8A04C", 0x8c / 255], ["#E8A04C", 0xc7 / 255], ["#F6C76A", 1]] },
  { key: "ember", name: "Ember", levels: [["#4D2416", 1], ["#8A3A1E", 1], ["#D9733A", 1], ["#FFB067", 1]] },
  { key: "ocean", name: "Ocean", levels: [["#1C3554", 1], ["#2A5C8C", 1], ["#3E8EC2", 1], ["#86CDF5", 1]] },
  { key: "mint", name: "Mint", levels: [["#1C4436", 1], ["#2B7458", 1], ["#43B285", 1], ["#95E6C3", 1]] },
  { key: "rose", name: "Rose", levels: [["#45192E", 1], ["#7C2B51", 1], ["#C4497F", 1], ["#F495BB", 1]] },
  { key: "violet", name: "Violet", levels: [["#2B2150", 1], ["#473388", 1], ["#7457C9", 1], ["#B7A2F5", 1]] },
  { key: "mono", name: "Mono", levels: [["#34333A", 1], ["#5F5D67", 1], ["#9E9BA6", 1], ["#ECE8E1", 1]] },
];

export const SHAPES: { key: ShapeKey; name: string }[] = [
  { key: "round", name: "Rounded" },
  { key: "square", name: "Square" },
  { key: "circle", name: "Dots" },
  { key: "heart", name: "Hearts" },
  { key: "diamond", name: "Diamonds" },
];

export const EMPTY: [string, number] = ["#ECE8E1", 0x14 / 255];

export interface GraphStyle {
  palette: PaletteKey;
  shape: ShapeKey;
}

export const DEFAULT_STYLE: GraphStyle = { palette: "amber", shape: "round" };

export function parseStyle(spec: string | null | undefined): GraphStyle {
  if (!spec || !spec.startsWith("g1:") || !spec.includes(".")) return { ...DEFAULT_STYLE };
  const [p, s] = spec.slice(3).split(".");
  return {
    palette: PALETTES.some((x) => x.key === p) ? (p as PaletteKey) : "amber",
    shape: SHAPES.some((x) => x.key === s) ? (s as ShapeKey) : "round",
  };
}

/** "" for the default look, so the server keeps it as "automatic". */
export function styleSpec(s: GraphStyle): string {
  return s.palette === "amber" && s.shape === "round" ? "" : `g1:${s.palette}.${s.shape}`;
}

/** Confessions in a day → level 0–4 (ConfessionGraphView.level). */
export function level(n: number): number {
  return n <= 0 ? 0 : n === 1 ? 1 : n === 2 ? 2 : n <= 4 ? 3 : 4;
}

export function colorOf(s: GraphStyle, lvl: number): [string, number] {
  if (lvl <= 0) return EMPTY;
  return PALETTES.find((p) => p.key === s.palette)!.levels[Math.min(4, lvl) - 1]!;
}

/** SVG path for a cell of `shape` in the box (x, y, size). `ring` = the outline drawn around today. */
export function cellPath(shape: ShapeKey, x: number, y: number, s: number, ring = false): string {
  const f = (n: number) => +n.toFixed(2);
  switch (shape) {
    case "square": return rounded(x, y, s, s * 0.08);
    case "circle": {
      const r = s / 2;
      return `M${f(x)} ${f(y + r)}a${f(r)} ${f(r)} 0 1 0 ${f(s)} 0a${f(r)} ${f(r)} 0 1 0 ${f(-s)} 0Z`;
    }
    case "diamond": {
      const cx = x + s / 2, cy = y + s / 2, h = (s / 2) * (ring ? 1.08 : 1.12);
      return `M${f(cx)} ${f(cy - h)}L${f(cx + h)} ${f(cy)}L${f(cx)} ${f(cy + h)}L${f(cx - h)} ${f(cy)}Z`;
    }
    case "heart": {
      const p = (a: number, b: number) => `${f(x + s * a)} ${f(y + s * b)}`;
      return `M${p(0.5, 0.92)}C${p(0.08, 0.62)} ${p(-0.02, 0.3)} ${p(0.18, 0.14)}C${p(0.32, 0.02)} ${p(0.46, 0.08)} ${p(0.5, 0.24)}` +
        `C${p(0.54, 0.08)} ${p(0.68, 0.02)} ${p(0.82, 0.14)}C${p(1.02, 0.3)} ${p(0.92, 0.62)} ${p(0.5, 0.92)}Z`;
    }
    default: return rounded(x, y, s, s * 0.28);
  }

  function rounded(x0: number, y0: number, size: number, r: number): string {
    const e = x0 + size, b = y0 + size;
    return `M${f(x0 + r)} ${f(y0)}H${f(e - r)}Q${f(e)} ${f(y0)} ${f(e)} ${f(y0 + r)}V${f(b - r)}Q${f(e)} ${f(b)} ${f(e - r)} ${f(b)}` +
      `H${f(x0 + r)}Q${f(x0)} ${f(b)} ${f(x0)} ${f(b - r)}V${f(y0 + r)}Q${f(x0)} ${f(y0)} ${f(x0 + r)} ${f(y0)}Z`;
  }
}

export interface GraphData {
  start: string;           // a Monday, YYYY-MM-DD
  end: string;
  days: number[];
  total: number;
  activeDays: number;
  style: string;
  public: boolean;
  streak?: { days: number; best: number; today: boolean };
  hidden?: undefined;
}

/** Local date (no time zone shift) for "YYYY-MM-DD" + n days. */
export function addDays(iso: string, n: number): Date {
  const [y, m, d] = iso.split("-").map(Number) as [number, number, number];
  return new Date(y, m - 1, d + n);
}
