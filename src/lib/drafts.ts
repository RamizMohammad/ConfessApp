/**
 * Drafts — unsent confessions, kept ONLY on this device and never sent anywhere.
 *
 * Stored in IndexedDB, encrypted with AES-GCM under a key the browser generates as
 * non-extractable (JavaScript can use it but can never read or export it). Everything is
 * wiped on sign-out. Like the Android app: "Only on this phone · encrypted".
 */
import type { Gif, Mood } from "./types.ts";

export interface Draft {
  id: string;
  text: string;
  commentsEnabled: boolean;
  mood: Mood | null;
  letterTo: string | null;
  poll: { options: string[]; durationHours: 24 | 72 | 168 } | null;
  gif: Gif | null;
  updatedAt: number;
}

const DB = "confess-drafts";
const MAX = 20;

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => {
      req.result.createObjectStore("keys");
      req.result.createObjectStore("drafts", { keyPath: "id" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function tx<T>(db: IDBDatabase, store: string, mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    const r = fn(db.transaction(store, mode).objectStore(store));
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

async function key(db: IDBDatabase): Promise<CryptoKey> {
  const existing = await tx<CryptoKey | undefined>(db, "keys", "readonly", (s) => s.get("k") as IDBRequest<CryptoKey | undefined>);
  if (existing) return existing;
  const k = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  await tx(db, "keys", "readwrite", (s) => s.put(k, "k"));
  return k;
}

interface Sealed { id: string; updatedAt: number; iv: Uint8Array<ArrayBuffer>; data: ArrayBuffer }

export async function listDrafts(): Promise<Draft[]> {
  try {
    const db = await open();
    const k = await key(db);
    const rows = await tx<Sealed[]>(db, "drafts", "readonly", (s) => s.getAll() as IDBRequest<Sealed[]>);
    const out: Draft[] = [];
    for (const r of rows) {
      try {
        const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: r.iv }, k, r.data);
        out.push(JSON.parse(new TextDecoder().decode(plain)) as Draft);
      } catch {
        /* unreadable (key was reset) — skip */
      }
    }
    return out.sort((a, b) => b.updatedAt - a.updatedAt);
  } catch {
    return [];
  }
}

export async function getDraft(id: string): Promise<Draft | undefined> {
  return (await listDrafts()).find((d) => d.id === id);
}

export async function saveDraft(d: Draft): Promise<void> {
  try {
    const db = await open();
    const k = await key(db);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, k, new TextEncoder().encode(JSON.stringify(d)));
    await tx(db, "drafts", "readwrite", (s) => s.put({ id: d.id, updatedAt: d.updatedAt, iv, data } satisfies Sealed));
    const all = await tx<Sealed[]>(db, "drafts", "readonly", (s) => s.getAll() as IDBRequest<Sealed[]>);
    for (const old of all.sort((a, b) => b.updatedAt - a.updatedAt).slice(MAX)) {
      await tx(db, "drafts", "readwrite", (s) => s.delete(old.id));
    }
  } catch {
    /* storage unavailable (private mode) — drafts just aren't kept */
  }
}

export async function deleteDraft(id: string): Promise<void> {
  try {
    const db = await open();
    await tx(db, "drafts", "readwrite", (s) => s.delete(id));
  } catch {
    /* ignore */
  }
}

/** Sign-out: drafts and the key are gone. */
export async function wipeDrafts(): Promise<void> {
  try {
    indexedDB.deleteDatabase(DB);
  } catch {
    /* ignore */
  }
}

export function newDraftId(): string {
  return crypto.randomUUID();
}
