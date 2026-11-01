/**
 * Activity (in-app notifications) + the unread badge.
 *
 * The badge polls /notifications/unread-count once a minute while the app is open and visible
 * (and whenever it comes back to the front). The Home Screen icon badge follows it.
 */
import { useEffect, useState } from "react";
import { api } from "./api.ts";
import type { Author } from "./types.ts";
import { setAppBadge } from "./webpush.ts";

export type NotificationType = "like" | "comment" | "follow" | "post" | "post_removed" | "post_sensitive";

export interface Notification {
  id: string;
  type: NotificationType | string;
  actor: Author;
  postId: string | null;
  postExcerpt: string | null;
  commentExcerpt: string | null;
  /** post_removed: the rule it broke. */
  reason?: string | null;
  read: boolean;
  createdAt: string;
}

export const list = (scope: "all" | "following", cursor?: string | null) =>
  api<{ notifications: Notification[]; nextCursor: string | null }>("/notifications", {
    query: { scope, cursor: cursor ?? undefined, include: "review" },
  });

export const markRead = (ids?: string[]) =>
  api<{ marked: number }>("/notifications/read", { method: "POST", body: ids?.length ? { ids } : {} });

// ── Badge ─────────────────────────────────────────────────────────────────────

let unread = 0;
const listeners = new Set<(n: number) => void>();
let timer: number | undefined;
let inflight = false;

function set(n: number): void {
  unread = Math.max(0, n);
  listeners.forEach((l) => l(unread));
  setAppBadge(unread);
}

export async function refreshUnread(): Promise<void> {
  if (inflight || document.visibilityState !== "visible") return;
  inflight = true;
  try {
    set((await api<{ unread: number }>("/notifications/unread-count", { query: { include: "review" } })).unread);
  } catch {
    /* keep the last count */
  } finally {
    inflight = false;
  }
}

/** Seen these locally (opened Activity / marked read): the badge drops at once. */
export function readLocally(count: number | "all"): void {
  set(count === "all" ? 0 : unread - count);
}

/** Badge count; starts / stops the minute poll with the first / last user. */
export function useUnread(): number {
  const [n, setN] = useState(unread);
  useEffect(() => {
    listeners.add(setN);
    if (listeners.size === 1) {
      void refreshUnread();
      timer = window.setInterval(() => void refreshUnread(), 60_000);
      document.addEventListener("visibilitychange", onVisible);
    }
    return () => {
      listeners.delete(setN);
      if (!listeners.size) {
        window.clearInterval(timer);
        document.removeEventListener("visibilitychange", onVisible);
      }
    };
  }, []);
  return n;
}

function onVisible(): void {
  if (document.visibilityState === "visible") void refreshUnread();
}

/** "Today" / "This week" / "Earlier" — the Android sections. */
export function section(iso: string, now = new Date()): "Today" | "This week" | "Earlier" {
  const d = new Date(iso);
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (d >= start) return "Today";
  if (d >= new Date(start.getTime() - 6 * 86400_000)) return "This week";
  return "Earlier";
}
