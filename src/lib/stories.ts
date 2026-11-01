/**
 * Stories — a line of text on a coloured card for 24 hours (server 3.3).
 * The tray is kept here once for the whole app, so every avatar can wear its ring:
 * bright amber = something new, soft = all seen (Android StoryRings).
 */
import { useEffect, useState } from "react";
import { api } from "./api.ts";
import type { Author, Reaction } from "./types.ts";

export interface Story {
  id: string;
  text: string;
  style: number;
  seen: boolean;
  createdAt: string;
  expiresAt: string;
  viewCount?: number;
  reactionCount?: number;
  myReaction?: Reaction | null;
}

export interface StoryGroup {
  author: Author;
  isMe: boolean;
  followed: boolean;
  hasUnseen: boolean;
  latestAt: string;
  stories: Story[];
}

export interface Viewer {
  alias: string;
  avatar: string;
  viewedAt: string;
  reaction: Reaction | null;
}

export const STORY_MAX = 100;
export const STYLES = [
  { bg: "story-0", label: "Plum night" },
  { bg: "story-1", label: "Paper" },
  { bg: "story-2", label: "Amber" },
  { bg: "story-3", label: "Sage" },
  { bg: "story-4", label: "Brick" },
] as const;
export const styleClass = (s: number) => STYLES[s >= 0 && s < STYLES.length ? s : 0]!.bg;

const enc = encodeURIComponent;
export const share = (text: string, style: number) => api<Story>("/stories", { method: "POST", body: { text, style } });
export const markSeen = (id: string) => api(`/stories/${enc(id)}/view`, { method: "POST" });
export const remove = (id: string) => api(`/stories/${enc(id)}`, { method: "DELETE" });
export const react = (id: string, reaction: Reaction) =>
  api<{ myReaction: Reaction | null }>(`/stories/${enc(id)}/react`, { method: "POST", body: { reaction } });
export const unreact = (id: string) => api<{ myReaction: null }>(`/stories/${enc(id)}/react`, { method: "DELETE" });
export const report = (id: string, reason: string) => api(`/stories/${enc(id)}/report`, { method: "POST", body: { reason } });
export const viewers = (id: string, cursor?: string | null) =>
  api<{ users: Viewer[]; viewCount: number; reactionCount: number; nextCursor: string | null }>(
    `/stories/${enc(id)}/viewers`, { query: { cursor: cursor ?? undefined } });

// ── The tray, shared ──────────────────────────────────────────────────────────

let tray: StoryGroup[] = [];
let loadedAt = 0;
let inflight: Promise<void> | null = null;
const listeners = new Set<(t: StoryGroup[]) => void>();

function publish(next: StoryGroup[]): void {
  // Expired stories disappear at once.
  const now = Date.now();
  tray = next
    .map((g) => ({ ...g, stories: g.stories.filter((s) => new Date(s.expiresAt).getTime() > now) }))
    .filter((g) => g.stories.length > 0)
    .map((g) => ({ ...g, hasUnseen: !g.isMe && g.stories.some((s) => !s.seen) }));
  listeners.forEach((l) => l(tray));
}

/** Fetch the tray (at most every 30 s unless forced). */
export function refreshTray(force = false): Promise<void> {
  if (!force && Date.now() - loadedAt < 30_000) return Promise.resolve();
  if (inflight) return inflight;
  inflight = api<{ people: StoryGroup[] }>("/stories")
    .then((r) => { loadedAt = Date.now(); publish(r.people); })
    .catch(() => undefined)
    .finally(() => { inflight = null; });
  return inflight;
}

export function currentTray(): StoryGroup[] {
  return tray;
}

export function useTray(): StoryGroup[] {
  const [t, setT] = useState(tray);
  useEffect(() => {
    listeners.add(setT);
    setT(tray);
    void refreshTray();
    return () => { listeners.delete(setT); };
  }, []);
  return t;
}

/** Local changes, so the ring dims the moment a story is seen. */
export function patchStory(storyId: string, patch: Partial<Story> | null): void {
  publish(tray.map((g) => ({
    ...g,
    stories: patch === null ? g.stories.filter((s) => s.id !== storyId)
      : g.stories.map((s) => (s.id === storyId ? { ...s, ...patch } : s)),
  })));
}

export function addMine(s: Story, me: Author): void {
  const mine = tray.find((g) => g.isMe);
  publish(mine
    ? tray.map((g) => (g.isMe ? { ...g, stories: [...g.stories, s], latestAt: s.createdAt } : g))
    : [{ author: me, isMe: true, followed: false, hasUnseen: false, latestAt: s.createdAt, stories: [s] }, ...tray]);
}

export function clearTray(): void {
  loadedAt = 0;
  publish([]);
}

export type Ring = "none" | "seen" | "unseen";
const key = (a: string) => a.replace(/^@/, "").toLowerCase();

export function ringOf(alias: string | null | undefined, t: StoryGroup[] = tray): Ring {
  if (!alias) return "none";
  const g = t.find((x) => key(x.author.alias) === key(alias));
  return !g ? "none" : g.hasUnseen ? "unseen" : "seen";
}

/** Where a story viewer opened for this alias starts: their first unseen story, else the first. */
export function startIndex(g: StoryGroup): number {
  if (g.isMe) return 0;
  const i = g.stories.findIndex((s) => !s.seen);
  return i < 0 ? 0 : i;
}
