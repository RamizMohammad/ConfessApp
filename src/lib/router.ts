/**
 * Tiny history router so the iPhone back-swipe and browser back work.
 *   /                    feed                       /u/<alias>          someone's profile
 *   /activity            activity (notifications)   /search             find people
 *   /me                  my page                    /me/letters         my confessions
 *   /me/followers        /me/following              /me/felt · /me/responses   what my letters received
 *   /p/<id>              a confession (share link)  /write · /drafts
 *   /settings[/<page>]   settings, and its pages    /settings/avatar    avatar studio
 *   /books               books (tab)                /b/<id>             a book       /b/<id>/<n>  reading chapter n
 *   /me/books            my books                   /books/new · /b/<id>/edit       book setup
 *   /b/<id>/desk         writer's desk              /b/<id>/write[/<n>] write / edit a chapter
 *   /s/<alias>           their stories              /story              share a story
 *   /burst/<id>[/following]  one writer's burst, oldest first
 */
import { useEffect, useState } from "react";
import { setNavDirection } from "./shell.ts";

export type SettingsPage = "security" | "notifications" | "devices" | "blocked" | "profile" | "feature" | "guidelines" | "appearance";
export const SETTINGS_PAGES: SettingsPage[] = ["security", "notifications", "devices", "blocked", "profile", "feature", "guidelines", "appearance"];

export type Route =
  | { name: "feed" }
  | { name: "post"; id: string }
  | { name: "write"; draftId?: string }
  | { name: "drafts" }
  | { name: "activity" }
  | { name: "me" }
  | { name: "letters" }
  | { name: "people"; kind: "followers" | "following" }
  | { name: "received"; kind: "felt" | "responses" }
  | { name: "profile"; alias: string }
  | { name: "search" }
  | { name: "settings"; page?: SettingsPage }
  | { name: "avatar" }
  | { name: "books" }
  | { name: "book"; id: string }
  | { name: "read"; id: string; no: number }
  | { name: "myBooks" }
  | { name: "bookSetup"; id?: string }
  | { name: "desk"; id: string }
  | { name: "chapter"; id: string; no?: number }
  | { name: "stories"; alias: string }
  | { name: "storyNew" }
  | { name: "burst"; id: string; scope: "all" | "following" }
  | { name: "design" }
  | { name: "screen" };

/** The screens with the bottom bar (HomeActivity's fragments: each stays mounted and keeps its place).
 *  Search is on the bar too, but it opens a screen; Activity opens from the feed's bell. */
export const TABS = ["feed", "books", "me"] as const;
export type Tab = (typeof TABS)[number];

export function parse(path: string): Route {
  const p = path.replace(/\/+$/, "") || "/";
  let m = /^\/p\/([0-9a-f-]{36})$/i.exec(p);
  if (m) return { name: "post", id: m[1]!.toLowerCase() };
  m = /^\/u\/([A-Za-z0-9_]{3,15})$/.exec(p);
  if (m) return { name: "profile", alias: m[1]! };
  const U = "([0-9a-f-]{36})";
  m = new RegExp(`^/b/${U}$`, "i").exec(p);
  if (m) return { name: "book", id: m[1]!.toLowerCase() };
  m = new RegExp(`^/b/${U}/(\\d{1,3})$`, "i").exec(p);
  if (m) return { name: "read", id: m[1]!.toLowerCase(), no: Number(m[2]) };
  m = new RegExp(`^/b/${U}/(desk|edit)$`, "i").exec(p);
  if (m) return m[2] === "desk" ? { name: "desk", id: m[1]!.toLowerCase() } : { name: "bookSetup", id: m[1]!.toLowerCase() };
  m = new RegExp(`^/b/${U}/write(?:/(\\d{1,3}))?$`, "i").exec(p);
  if (m) return { name: "chapter", id: m[1]!.toLowerCase(), no: m[2] ? Number(m[2]) : undefined };
  m = new RegExp(`^/burst/${U}(/following)?$`, "i").exec(p);
  if (m) return { name: "burst", id: m[1]!.toLowerCase(), scope: m[2] ? "following" : "all" };
  m = /^\/s\/([A-Za-z0-9_]{3,15})$/.exec(p);
  if (m) return { name: "stories", alias: m[1]! };
  m = /^\/settings(?:\/([a-z]+))?$/.exec(p);
  if (m) {
    if (!m[1]) return { name: "settings" };
    if (m[1] === "avatar") return { name: "avatar" };
    if ((SETTINGS_PAGES as string[]).includes(m[1])) return { name: "settings", page: m[1] as SettingsPage };
  }
  switch (p) {
    case "/write": return { name: "write" };
    case "/drafts": return { name: "drafts" };
    case "/activity": return { name: "activity" };
    case "/search": return { name: "search" };
    case "/me": return { name: "me" };
    case "/me/letters": return { name: "letters" };
    case "/me/followers": return { name: "people", kind: "followers" };
    case "/me/following": return { name: "people", kind: "following" };
    case "/me/felt": return { name: "received", kind: "felt" };
    case "/me/responses": return { name: "received", kind: "responses" };
    case "/books": return { name: "books" };
    case "/books/new": return { name: "bookSetup" };
    case "/me/books": return { name: "myBooks" };
    case "/story": return { name: "storyNew" };
    case "/dev/design": return { name: "design" };
    case "/dev/screen": return { name: "screen" };
    default: return { name: "feed" };
  }
}

export function href(r: Route): string {
  switch (r.name) {
    case "post": return `/p/${r.id}`;
    case "write": return "/write";
    case "drafts": return "/drafts";
    case "activity": return "/activity";
    case "search": return "/search";
    case "me": return "/me";
    case "letters": return "/me/letters";
    case "people": return `/me/${r.kind}`;
    case "received": return `/me/${r.kind}`;
    case "profile": return `/u/${r.alias}`;
    case "settings": return r.page ? `/settings/${r.page}` : "/settings";
    case "avatar": return "/settings/avatar";
    case "books": return "/books";
    case "book": return `/b/${r.id}`;
    case "read": return `/b/${r.id}/${r.no}`;
    case "myBooks": return "/me/books";
    case "bookSetup": return r.id ? `/b/${r.id}/edit` : "/books/new";
    case "desk": return `/b/${r.id}/desk`;
    case "chapter": return r.no ? `/b/${r.id}/write/${r.no}` : `/b/${r.id}/write`;
    case "stories": return `/s/${r.alias}`;
    case "storyNew": return "/story";
    case "design": return "/dev/design";
    case "screen": return "/dev/screen";
    case "burst": return r.scope === "following" ? `/burst/${r.id}/following` : `/burst/${r.id}`;
    default: return "/";
  }
}

/** One screen on the back stack. `key` stays the same while it's on the stack, so it stays mounted. */
export interface Entry { route: Route; key: number }

let listeners: ((r: Route) => void)[] = [];
let stackListeners: (() => void)[] = [];
let current: Route = parse(typeof location !== "undefined" ? location.pathname : "/");
let seq = 1;
/** How many screens we pushed on top of the first one (so back() never leaves the app). */
let depth = 0;
/** The back stack, index = depth (Android's activity stack). Gaps are entries from before a reload. */
let stack: (Entry | undefined)[] = [];

if (typeof history !== "undefined") {
  const st = (history.state ?? {}) as { depth?: number; key?: number };
  depth = typeof st.depth === "number" && st.depth >= 0 && st.depth < 200 ? st.depth : 0;
  const key = typeof st.key === "number" ? st.key : seq++;
  seq = Math.max(seq, key + 1);
  stack[depth] = { route: current, key };
  if (current.name === "write" && (st as { draftId?: string }).draftId) current = stack[depth]!.route = { ...current, draftId: (st as { draftId?: string }).draftId };
}

function emit(): void {
  listeners.forEach((l) => l(current));
  stackListeners.forEach((l) => l());
}

export function navigate(r: Route, replace = false, direction?: "push" | "pop" | "replace"): void {
  const key = seq++;
  const st = { draftId: r.name === "write" ? r.draftId : undefined, depth: replace ? depth : depth + 1, key };
  setNavDirection(direction ?? (replace ? "replace" : "push"));
  if (replace) history.replaceState(st, "", href(r));
  else {
    depth += 1;
    history.pushState(st, "", href(r));
  }
  stack = stack.slice(0, depth);
  stack[depth] = { route: r, key };
  current = r;
  emit();
}

/** Switch tabs: replaces the history entry, so back from a tab doesn't walk through tabs. */
export function switchTab(t: Tab): void {
  if (current.name === t) return;
  navigate({ name: t } as Route, true);
}

/** Pending reset: once history has walked back to the first entry, show this there. */
let resetTarget: Route | null = null;

/** Close everything on top and show `r` at the bottom of the stack (compose → released → feed). */
export function resetTo(r: Route): void {
  if (depth > 0) {
    resetTarget = r;
    history.go(-depth);
  } else navigate(r, true, "pop");
}

/** Back within the app; from a deep link straight to a screen, back goes to the feed. */
export function back(): void {
  if (depth > 0) history.back();
  else navigate({ name: "feed" }, true, "pop");
}

/** Is there somewhere to go back to (the edge swipe uses this)? Tabs are roots. */
export function canGoBack(): boolean {
  return !(TABS as readonly string[]).includes(current.name);
}

export function currentRoute(): Route {
  return current;
}

export function isTab(r: Route): r is { name: Tab } {
  return (TABS as readonly string[]).includes(r.name);
}

if (typeof window !== "undefined") {
  window.addEventListener("popstate", (e) => {
    const st = (e.state ?? {}) as { draftId?: string; depth?: number; key?: number };
    const to = typeof st.depth === "number" ? st.depth : 0;
    setNavDirection(to < depth ? "pop" : "push");
    depth = to;
    const r = parse(location.pathname);
    current = r.name === "write" && st.draftId ? { ...r, draftId: st.draftId } : r;
    const had = stack[depth];
    stack = stack.slice(0, depth);
    stack[depth] = had && had.key === st.key ? had : { route: current, key: typeof st.key === "number" ? st.key : seq++ };
    if (resetTarget && depth === 0) {
      const r2 = resetTarget;
      resetTarget = null;
      if (r2.name !== current.name) { navigate(r2, true, "pop"); return; }
    }
    emit();
  });
}

export function useRoute(): Route {
  const [r, setR] = useState<Route>(current);
  useEffect(() => {
    listeners.push(setR);
    setR(current);
    return () => {
      listeners = listeners.filter((l) => l !== setR);
    };
  }, []);
  return r;
}

/** The whole back stack (bottom → top) and the top's index, for keeping screens mounted. */
export function useStack(): { entries: { entry: Entry; index: number }[]; top: number } {
  const snap = () => ({ entries: stack.flatMap((e, i) => (e ? [{ entry: e, index: i }] : [])), top: depth });
  const [s, setS] = useState(snap);
  useEffect(() => {
    const l = () => setS(snap());
    stackListeners.push(l);
    l();
    return () => { stackListeners = stackListeners.filter((x) => x !== l); };
  }, []);
  return s;
}

/** Open a confession. iPad split view: replaces the one open on the right instead of stacking. */
let jumpTo: string | null = null;
export function openPost(id: string, toResponses = false): void {
  jumpTo = toResponses ? id : null;
  const split = typeof document !== "undefined" && document.documentElement.classList.contains("split");
  if (split && current.name === "post") {
    if (current.id !== id) navigate({ name: "post", id }, true, "replace");
    else if (toResponses) window.dispatchEvent(new CustomEvent("confess:jump-responses", { detail: id }));
    return;
  }
  navigate({ name: "post", id });
}
/** PostDetail asks once: was it opened from the responses button (jump there, keyboard up)? */
export function takeJump(id: string): boolean {
  const yes = jumpTo === id;
  if (yes) jumpTo = null;
  return yes;
}
