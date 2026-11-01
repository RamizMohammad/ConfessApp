/**
 * Bursts — one writer's confessions posted close together. The server groups them (posts.burst_id);
 * with bursts on, the feed sends one card per burst (its newest confession + `burst: {id, count}`)
 * and the burst is fetched a few at a time, oldest first.
 *
 * How a burst looks is the reader's choice (asked the first time one shows up):
 *   slider   — swipe through them in one card
 *   stack    — up to 3: the slider; more: one card that opens them, oldest first
 *   separate — every confession its own card (the feed is asked without bursts)
 */
import { api } from "./api.ts";
import type { Mood, Post } from "./types.ts";

export type BurstMode = "slider" | "stack" | "separate";
export const SMALL = 3;
const KEY = "confess.bursts";

export function burstMode(): BurstMode {
  try {
    const v = localStorage.getItem(KEY);
    return v === "stack" || v === "separate" ? v : "slider";
  } catch {
    return "slider";
  }
}

export function burstAsked(): boolean {
  try { return localStorage.getItem(KEY) !== null; } catch { return true; }
}

export function setBurstMode(m: BurstMode): void {
  try { localStorage.setItem(KEY, m); } catch { /* ignore */ }
  listeners.forEach((l) => l(m));
}

const listeners = new Set<(m: BurstMode) => void>();
export function onBurstMode(l: (m: BurstMode) => void): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

export const fetchBurst = (id: string, q: { scope: "all" | "following"; mood?: Mood | null; cursor?: string | null; limit?: number }) =>
  api<{ posts: Post[]; nextCursor: string | null; total: number }>(`/posts/bursts/${encodeURIComponent(id)}`, {
    query: { scope: q.scope, mood: q.mood ?? undefined, cursor: q.cursor ?? undefined, limit: q.limit ?? 5 },
  });

export function spanLabel(first: string, last: string): string {
  const min = Math.round((new Date(last).getTime() - new Date(first).getTime()) / 60000);
  if (min < 2) return "All at once · oldest first";
  if (min < 60) return `Within ${min} min · oldest first`;
  if (min < 48 * 60) return `Over ${Math.max(1, Math.round(min / 60))} h · oldest first`;
  return `Over ${Math.round(min / 1440)} days · oldest first`;
}
