/**
 * Tiny history router so the iPhone back-swipe and browser back work.
 *   /            feed
 *   /p/<id>      a confession and its responses (also a share link)
 *   /write       compose
 *   /drafts      drafts
 */
import { useEffect, useState } from "react";

export type Route =
  | { name: "feed" }
  | { name: "post"; id: string }
  | { name: "write"; draftId?: string }
  | { name: "drafts" };

export function parse(path: string): Route {
  const m = /^\/p\/([0-9a-f-]{36})\/?$/i.exec(path);
  if (m) return { name: "post", id: m[1]!.toLowerCase() };
  if (path === "/write") return { name: "write" };
  if (path === "/drafts") return { name: "drafts" };
  return { name: "feed" };
}

export function href(r: Route): string {
  switch (r.name) {
    case "post": return `/p/${r.id}`;
    case "write": return "/write";
    case "drafts": return "/drafts";
    default: return "/";
  }
}

let listeners: ((r: Route) => void)[] = [];
let current: Route = parse(typeof location !== "undefined" ? location.pathname : "/");
/** How many screens we pushed on top of the first one (so back() never leaves the app). */
let depth = 0;

function emit(): void {
  listeners.forEach((l) => l(current));
}

export function navigate(r: Route, replace = false): void {
  const st = { draftId: r.name === "write" ? r.draftId : undefined, depth: replace ? depth : depth + 1 };
  if (replace) history.replaceState(st, "", href(r));
  else {
    depth += 1;
    history.pushState(st, "", href(r));
  }
  current = r;
  emit();
}

/** Back within the app; from a deep link straight to a screen, back goes to the feed. */
export function back(): void {
  if (depth > 0) history.back();
  else navigate({ name: "feed" }, true);
}

if (typeof window !== "undefined") {
  window.addEventListener("popstate", (e) => {
    const st = (e.state ?? {}) as { draftId?: string; depth?: number };
    depth = st.depth ?? 0;
    const r = parse(location.pathname);
    current = r.name === "write" && st.draftId ? { ...r, draftId: st.draftId } : r;
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
