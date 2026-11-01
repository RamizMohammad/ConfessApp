/** Profiles, follows, people lists, account settings — thin wrappers over api(). */
import { api } from "./api.ts";
import type { GraphData } from "./graph.ts";
import type { Post, Reaction } from "./types.ts";

const enc = encodeURIComponent;

export interface Profile {
  alias: string;
  about: string;
  avatar: string;
  banner: string;
  createdAt: string;
  postCount: number;
  followers: number;
  following: number;
  isFollowing: boolean;
  isMe: boolean;
  blockedByMe: boolean;
}

export interface Person {
  alias: string;
  about: string;
  avatar: string;
  followedByMe?: boolean;
  followedAt?: string;
}

export interface Blocked {
  alias: string;
  avatar: string;
  blockedAt: string;
}

export interface DeviceSession {
  id: string;
  current: boolean;
  platform: string | null;
  device: string | null;
  appVersion: string | null;
  createdAt: string | null;
  lastUsedAt: string | null;
}

export interface Liker {
  alias: string;
  avatar: string;
  likedAt: string;
  reaction: Reaction;
}

export type NotificationPrefs = Record<
  "likes" | "comments" | "follows" | "followingPosts" | "newPosts" | "nudges" | "bookChapters", boolean>;

export type PeopleKind = "followers" | "following";

export const profile = (alias: string) => api<Profile>(`/users/${enc(alias)}`);
export const postsBy = (alias: string, cursor?: string | null) =>
  api<{ posts: Post[]; nextCursor: string | null }>(`/users/${enc(alias)}/posts`, { query: { cursor: cursor ?? undefined } });
export const myPosts = (cursor?: string | null) =>
  api<{ posts: Post[]; nextCursor: string | null }>("/posts/mine", { query: { cursor: cursor ?? undefined } });

export const graphOf = (alias: string, weeks = 26) =>
  api<GraphData | { hidden: true }>(`/users/${enc(alias)}/graph`, { query: { weeks } });
export const myGraph = (weeks = 26) => api<GraphData>("/users/me/graph", { query: { weeks } });
export const setGraph = (body: { style?: string; public?: boolean }) =>
  api<{ style: string; public: boolean }>("/users/me/graph", { method: "POST", body });

export const follow = (alias: string) => api<{ following: boolean; followers: number }>(`/users/${enc(alias)}/follow`, { method: "POST" });
export const unfollow = (alias: string) => api<{ following: boolean; followers: number }>(`/users/${enc(alias)}/follow`, { method: "DELETE" });
export const unblock = (alias: string) => api(`/users/${enc(alias)}/block`, { method: "DELETE" });

export const people = (kind: PeopleKind, cursor?: string | null) =>
  api<{ users: Person[]; nextCursor: string | null }>(`/users/me/${kind}`, { query: { cursor: cursor ?? undefined } });
export const blocked = (cursor?: string | null) =>
  api<{ users: Blocked[]; nextCursor: string | null }>("/users/me/blocked", { query: { cursor: cursor ?? undefined } });
export const search = (q: string) => api<{ users: Person[] }>("/users/search", { query: { q } });

export const likers = (postId: string, cursor?: string | null) =>
  api<{ users: Liker[]; nextCursor: string | null }>(`/posts/${enc(postId)}/likes`, { query: { cursor: cursor ?? undefined } });

export const setAbout = (about: string) => api<{ about: string }>("/users/me/about", { method: "POST", body: { about } });
export const setAvatar = (avatar: string) => api<{ avatar: string }>("/users/me/avatar", { method: "POST", body: { avatar } });
export const setBanner = (banner: string) => api<{ banner: string }>("/users/me/banner", { method: "POST", body: { banner } });

export const sessions = () => api<{ sessions: DeviceSession[] }>("/users/me/sessions");
export const revokeSession = (id: string) => api(`/users/me/sessions/${enc(id)}`, { method: "DELETE" });
export const revokeOthers = () => api<{ revoked: number }>("/users/me/sessions/revoke-others", { method: "POST" });

export const setNotificationPrefs = (p: Partial<NotificationPrefs>) =>
  api<NotificationPrefs>("/users/me/notifications", { method: "POST", body: p });

export const setPassword = (password: string) => api("/users/me/password/set", { method: "POST", body: { password } });
export const changePassword = (oldPassword: string, newPassword: string) =>
  api("/users/me/password/change", { method: "POST", body: { oldPassword, newPassword } });
export const removePassword = (password: string) => api("/users/me/password/remove", { method: "POST", body: { password } });

export const deleteAccount = () => api<{ deleted: true }>("/users/me", { method: "DELETE" });

export const requestFeature = (category: string, text: string) =>
  api("/feedback/feature", { method: "POST", body: { category, text } });

/** "iPhone · Safari", "Pixel 8 · Confess 5.2" … */
export function deviceName(s: DeviceSession): string {
  const base = s.device?.trim() || (s.platform === "web" ? "A browser" : s.platform === "android" ? "An Android phone" : "A device");
  return s.appVersion && s.platform !== "web" ? `${base} · Confess ${s.appVersion}` : base;
}
