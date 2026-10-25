/** "4m", "3h", "2d", then a date. */
export function ago(iso: string, now = Date.now()): string {
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  if (s < 86400) return `${Math.floor(s / 3600)}h`;
  if (s < 7 * 86400) return `${Math.floor(s / 86400)}d`;
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/** 999 · 1.2k · 14k · 1.4M */
export function count(n: number): string {
  if (n < 1000) return String(n);
  if (n < 10_000) return `${(n / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  if (n < 1_000_000) return `${Math.floor(n / 1000)}k`;
  return `${(n / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
}

/** Characters as people count them (emoji = 1), same as the server's len(). */
export function length(s: string): number {
  return [...s].length;
}

/** Turns "… (religious hate)" from a 451 into the Android wording. */
export function moderationReason(message: string): string {
  const label = /\(([^)]+)\)\s*$/.exec(message)?.[1]?.replace(/ /g, "_") ?? "";
  return ({
    personal_info: "It reveals someone's private details — a number, an address or a real name.",
    religious_hate: "It targets people for their religion.",
    sexual_threat: "It reads as sexual harassment, a threat or blackmail.",
    national_offense: "It promotes hatred against a nation or terrorism.",
    child_safety: "It involves a minor in a sexual way. This is never allowed on Confess.",
    violent_threat: "It reads as a threat to hurt someone.",
    self_harm_promotion: "It encourages self-harm. If you're struggling, Tele-MANAS is free and open 24×7 on 14416.",
  } as Record<string, string>)[label] ?? "It breaks our community guidelines.";
}
