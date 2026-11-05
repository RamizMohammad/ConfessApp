/**
 * The Android app's own icons (res/drawable/ic_*.xml), drawn from the generated vector data.
 * Tinted icons take the text colour (currentColor), exactly as Android's colorControlNormal tint.
 * Presentation attributes only — the CSP forbids inline styles.
 */
import type { ReactNode } from "react";
import { icons, type IconName, type VNode } from "../android/icons.ts";

export type { IconName };

function nodes(list: readonly VNode[], key = ""): ReactNode[] {
  return list.map((n, i) => {
    const k = `${key}${i}`;
    if ("g" in n) return <g key={k} transform={n.g || undefined}>{nodes(n.c, `${k}.`)}</g>;
    if ("clip" in n) return null;  // none of the app's icons clip; kept for completeness
    return (
      <path key={k} d={n.d} fill={n.fill} fillOpacity={n.fo} fillRule={n.rule}
            stroke={n.stroke} strokeWidth={n.sw} strokeOpacity={n.so}
            strokeLinecap={n.cap as "round" | "butt" | "square" | undefined}
            strokeLinejoin={n.join as "round" | "miter" | "bevel" | undefined} />
    );
  });
}

/** size in dp (= CSS px). Defaults to the drawable's own size (24). */
export function Icon({ name, size, className, label }: { name: IconName; size?: number; className?: string; label?: string }) {
  const v = icons[name];
  const s = size ?? v.w;
  return (
    <svg viewBox={v.vb} width={s} height={s} className={className ? `icon ${className}` : "icon"}
         role={label ? "img" : undefined} aria-label={label} aria-hidden={label ? undefined : true} focusable="false">
      {nodes(v.c as readonly VNode[])}
    </svg>
  );
}

export const iconNames = Object.keys(icons) as IconName[];
