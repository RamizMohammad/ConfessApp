/** Keeps every open list in step: a reaction, report, block or delete on one screen shows everywhere. */
import type { Post } from "./types.ts";

export type PostEvent =
  | { type: "update"; post: Post }
  | { type: "remove"; postId: string }
  | { type: "blocked"; alias: string }
  | { type: "created"; post: Post };

type L = (e: PostEvent) => void;
const listeners = new Set<L>();
const cache = new Map<string, Post>();

export function emit(e: PostEvent): void {
  if (e.type === "update" || e.type === "created") cache.set(e.post.postId, e.post);
  if (e.type === "remove") cache.delete(e.postId);
  listeners.forEach((l) => l(e));
}

export function subscribe(l: L): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

export function remember(posts: Post[]): void {
  for (const p of posts) cache.set(p.postId, p);
}

export function cached(id: string): Post | undefined {
  return cache.get(id);
}

export function apply(list: Post[], e: PostEvent): Post[] {
  switch (e.type) {
    case "update": return list.map((p) => (p.postId === e.post.postId ? e.post : p));
    case "remove": return list.filter((p) => p.postId !== e.postId);
    case "blocked": return list.filter((p) => p.author.alias.toLowerCase() !== e.alias.toLowerCase());
    case "created": return [e.post, ...list.filter((p) => p.postId !== e.post.postId)];
  }
}

export const cardActions = (open?: (p: Post) => void) => ({
  onChange: (post: Post) => emit({ type: "update", post }),
  onRemove: (postId: string) => emit({ type: "remove", postId }),
  onBlocked: (alias: string) => emit({ type: "blocked", alias }),
  onOpen: open,
});
