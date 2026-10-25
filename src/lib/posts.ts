/** Confessions, responses, reactions, reports, blocks — thin wrappers over api(). */
import { api } from "./api.ts";
import type { Comment, Gif, Mood, Post, Reaction } from "./types.ts";

export type Sort = "latest" | "top";
export type Scope = "all" | "following";

export const feed = (q: { sort: Sort; scope: Scope; mood?: Mood | null; cursor?: string | null }) =>
  api<{ posts: Post[]; nextCursor: string | null }>("/posts/feed", {
    query: { sort: q.sort, scope: q.scope, mood: q.mood ?? undefined, cursor: q.cursor ?? undefined },
  });

export const getPost = (id: string) => api<Post>(`/posts/${encodeURIComponent(id)}`);

export interface NewPost {
  text: string;
  commentsEnabled: boolean;
  mood?: Mood | null;
  letterTo?: string | null;
  poll?: { options: string[]; durationHours: 24 | 72 | 168 } | null;
  gif?: { id: string; width: number | null; height: number | null } | null;
}

export const createPost = (p: NewPost) => api<Post>("/posts", { method: "POST", body: p });
export const deletePost = (id: string) => api<{ deleted: true }>(`/posts/${encodeURIComponent(id)}`, { method: "DELETE" });

export interface ReactResult {
  liked: boolean;
  likeCount: number;
  myReaction: Reaction | null;
  reactions: Post["reactions"];
}
export const react = (id: string, reaction: Reaction) =>
  api<ReactResult>(`/posts/${encodeURIComponent(id)}/like`, { method: "POST", body: { reaction } });
export const unreact = (id: string) => api<ReactResult>(`/posts/${encodeURIComponent(id)}/like`, { method: "DELETE" });

export const vote = (id: string, option: number) =>
  api<Post>(`/posts/${encodeURIComponent(id)}/vote`, { method: "POST", body: { option } });

export const comments = (postId: string, cursor?: string | null) =>
  api<{ comments: Comment[]; nextCursor: string | null }>(`/posts/${encodeURIComponent(postId)}/comments`, {
    query: { cursor: cursor ?? undefined },
  });
export const addComment = (postId: string, text: string, gif?: Gif | null) =>
  api<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
    method: "POST",
    body: { text, gif: gif ? { id: gif.id, width: gif.width, height: gif.height } : undefined },
  });
export const deleteComment = (id: string) => api<{ deleted: true }>(`/comments/${encodeURIComponent(id)}`, { method: "DELETE" });

export const reportPost = (id: string, reason: string) =>
  api(`/posts/${encodeURIComponent(id)}/report`, { method: "POST", body: { reason } });
export const reportComment = (id: string, reason: string) =>
  api(`/comments/${encodeURIComponent(id)}/report`, { method: "POST", body: { reason } });
export const reportUser = (alias: string, reason: string) =>
  api(`/users/${encodeURIComponent(alias)}/report`, { method: "POST", body: { reason } });
export const blockUser = (alias: string) => api(`/users/${encodeURIComponent(alias)}/block`, { method: "POST" });

export const gifSearch = (q: string, offset = 0) =>
  api<{ gifs: Gif[]; nextOffset: number | null }>("/gifs/search", { query: { q, offset } });
export const gifTrending = (offset = 0) =>
  api<{ gifs: Gif[]; nextOffset: number | null }>("/gifs/trending", { query: { offset } });
