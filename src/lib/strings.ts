/**
 * The Android app's English strings, with Android's formatting rules, so the web app says exactly
 * what the app says. Source: src/android/strings.ts (generated — run scripts/android_sync.py).
 *
 *   S("notif_on", "Hello")            → "On “Hello”"        (%1$s, %2$d, %s, %d, %%)
 *   P("books_chapters", 3)            → "3 chapters"        (the count fills %d / %1$d)
 *   A("tour_titles")                  → string[]
 */
import { arrays, plurals, strings } from "../android/strings.ts";

const english = new Intl.PluralRules("en");

/** Android's String.format subset used in res/values: %s %d %1$s %2$d %% (and %,d / %.1f just in case). */
export function format(template: string, args: readonly unknown[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?([,.\d]*)([sdf%])/g, (_m, pos: string | undefined, flags: string, kind: string) => {
    if (kind === "%") return "%";
    const i = pos ? Number(pos) - 1 : next++;
    const v = args[i];
    if (v === undefined) return "";
    if (kind === "d") {
      if (flags.includes(",")) return Number(v).toLocaleString("en");
      const n = String(Math.trunc(Number(v)));
      const pad = /^0(\d+)$/.exec(flags);
      return pad ? n.padStart(Number(pad[1]), "0") : n;
    }
    if (kind === "f") {
      const m = /\.(\d+)/.exec(flags);
      return Number(v).toFixed(m ? Number(m[1]) : 6);
    }
    return String(v);
  });
}

export function has(key: string): boolean {
  return key in strings;
}

export function S(key: string, ...args: unknown[]): string {
  const t = strings[key];
  if (t === undefined) {
    if (import.meta.env?.DEV) console.warn(`missing Android string: ${key}`);
    return key;
  }
  return args.length ? format(t, args) : t;
}

export function P(key: string, count: number, ...args: unknown[]): string {
  const forms = plurals[key];
  if (!forms) return key;
  const t = forms[english.select(count)] ?? forms.other ?? "";
  return format(t, args.length ? args : [count]);
}

export function A(key: string): string[] {
  return arrays[key] ?? [];
}
