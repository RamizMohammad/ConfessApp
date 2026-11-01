/**
 * Books — stories told chapter by chapter (server 3.6). Each chapter is a confession row, so
 * reactions, responses and reports use the normal /posts/{postId}/… calls.
 */
import { api } from "./api.ts";
import type { Author, Mood, Post } from "./types.ts";

export interface Book {
  bookId: string;
  title: string;
  blurb: string;
  cover: number;
  author: Author;
  isMine: boolean;
  status: "draft" | "ongoing" | "completed";
  chapterCount: number;
  followerCount: number;
  following: boolean;
  lastReadChapter: number | null;
  hasUnread: boolean;
  publishedAt: string | null;
  lastReleaseAt: string | null;
  createdAt: string;
  // mine only
  totalChapters?: number;
  scheduledCount?: number;
  draftCount?: number;
  nextReleaseAt?: string | null;
  hidden?: boolean;
}

export interface ChapterEntry {
  postId: string;
  chapterNo: number;
  title: string | null;
  excerpt: string;
  mood: Mood | null;
  likeCount: number;
  commentCount: number;
  releasedAt: string | null;
  state?: "released" | "scheduled" | "draft";
  publishAt?: string | null;
}

export interface ChapterRead {
  book: Book;
  chapter: Post;
  prev: number | null;
  next: number | null;
}

export type Release = "now" | "schedule" | "draft";

export interface ChapterIn {
  text: string;
  title?: string | null;
  mood?: Mood | null;
  commentsEnabled?: boolean;
  release: Release;
  publishAt?: string | null;
}

export type ShelfTab = "following" | "updated" | "new";

export const TITLE_MAX = 60, BLURB_MAX = 200, CHAPTER_MAX = 2000, CHAPTER_TITLE_MAX = 60, COVERS = 5;

const enc = encodeURIComponent;
type Full = { book: Book; chapters: ChapterEntry[] };

export const list = (tab: ShelfTab, cursor?: string | null, alias?: string) =>
  api<{ books: Book[]; nextCursor: string | null }>("/books", { query: { tab, cursor: cursor ?? undefined, alias } });
export const mine = () => api<{ books: Book[] }>("/books/mine");
export const get = (id: string) => api<Full>(`/books/${enc(id)}`);
export const chapter = (id: string, no: number, peek = false) =>
  api<ChapterRead>(`/books/${enc(id)}/chapters/${no}`, { query: { peek: peek ? "true" : undefined } });
export const follow = (id: string, on: boolean) => api<Book>(`/books/${enc(id)}/follow`, { method: on ? "POST" : "DELETE" });
export const report = (id: string, reason: string) => api(`/books/${enc(id)}/report`, { method: "POST", body: { reason } });

export const create = (b: { title: string; blurb?: string; cover: number }) => api<Full>("/books", { method: "POST", body: b });
export const update = (id: string, b: Partial<{ title: string; blurb: string; cover: number; completed: boolean }>) =>
  api<Full>(`/books/${enc(id)}`, { method: "PATCH", body: b });
export const destroy = (id: string) => api(`/books/${enc(id)}`, { method: "DELETE" });
export const addChapter = (id: string, c: ChapterIn) => api<Full>(`/books/${enc(id)}/chapters`, { method: "POST", body: { chapters: [c] } });
export const editChapter = (id: string, no: number, c: Partial<ChapterIn> & { clearTitle?: boolean }) =>
  api<Full>(`/books/${enc(id)}/chapters/${no}`, { method: "PATCH", body: c });
export const deleteChapter = (id: string, no: number) => api<Full>(`/books/${enc(id)}/chapters/${no}`, { method: "DELETE" });
export const reorder = (id: string, chapters: number[]) => api<Full>(`/books/${enc(id)}/order`, { method: "PUT", body: { chapters } });

export function chapterTitle(no: number, title: string | null | undefined): string {
  return title?.trim() ? title : `Chapter ${no}`;
}

/** "Sat 18 Oct · 9:00 pm" in this phone's time zone. */
export function when(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} · ${d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;
}

/** <input type="datetime-local"> value ↔ ISO with this phone's offset (the server needs the offset). */
export function localInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function withOffset(local: string): string | null {
  const d = new Date(local);
  if (Number.isNaN(d.getTime())) return null;
  const off = -d.getTimezoneOffset();
  const sign = off >= 0 ? "+" : "-";
  const p = (n: number) => String(Math.floor(Math.abs(n))).padStart(2, "0");
  return `${local.length === 16 ? `${local}:00` : local}${sign}${p(off / 60)}:${p(off % 60)}`;
}

// ── Reading settings (this device, every book) ────────────────────────────────

export interface ReaderPrefs {
  size: number;           // px, 14–30
  spacing: 0 | 1 | 2;     // compact · normal · airy
  font: number;           // ReadingFonts id
  theme: 0 | 1 | 2;       // paper · sepia · night
  justify: boolean;
  lines: boolean;
  turn: "slide" | "instant";
}

export const SPACING = [1.28, 1.48, 1.72] as const;
export const SIZE_MIN = 14, SIZE_MAX = 30;

/** Same ids as the Android ReadingFonts (they're what's saved). */
export const FONTS = [
  { id: 1, name: "Manrope", sub: "Clean and modern", cls: "f-manrope" },
  { id: 0, name: "Newsreader", sub: "Classic newspaper serif", cls: "f-newsreader" },
  { id: 2, name: "Literata", sub: "Made for long reads", cls: "f-literata" },
  { id: 3, name: "Lora", sub: "Warm, bookish serif", cls: "f-lora" },
  { id: 4, name: "Atkinson", sub: "Extra easy to read", cls: "f-atkinson" },
  { id: 5, name: "Nunito", sub: "Soft and rounded", cls: "f-nunito" },
  { id: 6, name: "Caveat", sub: "Handwritten", cls: "f-caveat" },
  { id: 7, name: "Typewriter", sub: "Like a typed letter", cls: "f-typewriter" },
] as const;

const DEFAULT_PREFS: ReaderPrefs = { size: 18, spacing: 1, font: 1, theme: 0, justify: true, lines: true, turn: "slide" };
const PREFS_KEY = "confess.reader";

export function loadPrefs(): ReaderPrefs {
  try {
    const raw = JSON.parse(localStorage.getItem(PREFS_KEY) ?? "{}") as Partial<ReaderPrefs>;
    const p = { ...DEFAULT_PREFS, ...raw };
    return {
      size: Math.min(SIZE_MAX, Math.max(SIZE_MIN, Math.round(Number(p.size) || 18))),
      spacing: ([0, 1, 2] as const).includes(p.spacing) ? p.spacing : 1,
      font: FONTS.some((f) => f.id === p.font) ? p.font : 1,
      theme: ([0, 1, 2] as const).includes(p.theme) ? p.theme : 0,
      justify: p.justify !== false,
      lines: p.lines !== false,
      turn: p.turn === "instant" ? "instant" : "slide",
    };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function savePrefs(p: ReaderPrefs): void {
  try { localStorage.setItem(PREFS_KEY, JSON.stringify(p)); } catch { /* private mode */ }
}

// ── My place in each book (this device only; ids and numbers, never titles) ───

export interface Place {
  bookId: string;
  chapter: number;
  /** How far through the chapter, 0–1 (survives text-size changes). */
  at: number;
  time: number;
}

const SHELF_KEY = "confess.shelf";

export function places(): Place[] {
  try {
    const v = JSON.parse(localStorage.getItem(SHELF_KEY) ?? "[]") as Place[];
    return Array.isArray(v) ? v.filter((p) => typeof p.bookId === "string" && /^[0-9a-f-]{36}$/i.test(p.bookId)) : [];
  } catch {
    return [];
  }
}

export function placeOf(bookId: string): Place | undefined {
  return places().find((p) => p.bookId === bookId);
}

export function savePlace(p: Omit<Place, "time">): void {
  const rest = places().filter((x) => x.bookId !== p.bookId);
  try { localStorage.setItem(SHELF_KEY, JSON.stringify([{ ...p, time: Date.now() }, ...rest].slice(0, 20))); } catch { /* ignore */ }
}

export function forgetPlace(bookId: string): void {
  try { localStorage.setItem(SHELF_KEY, JSON.stringify(places().filter((x) => x.bookId !== bookId))); } catch { /* ignore */ }
}

export function wipePlaces(): void {
  try { localStorage.removeItem(SHELF_KEY); } catch { /* ignore */ }
}
