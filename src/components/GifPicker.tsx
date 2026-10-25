/** GIF picker. Searches through the Confess server (/gifs/*), so the GIPHY key never reaches the phone. */
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import * as P from "../lib/posts.ts";
import type { Gif } from "../lib/types.ts";
import { Sheet } from "./post.tsx";
import { Button } from "./ui.tsx";

export function GifPicker({ onPick, onClose }: { onPick: (g: Gif) => void; onClose: () => void }) {
  const [q, setQ] = useState("");
  const [gifs, setGifs] = useState<Gif[]>([]);
  const [next, setNext] = useState<number | null>(null);
  const [state, setState] = useState<"loading" | "ok" | "error" | "off">("loading");
  const seq = useRef(0);

  async function load(query: string, offset: number) {
    const mine = ++seq.current;
    if (offset === 0) setState("loading");
    try {
      const r = query ? await P.gifSearch(query, offset) : await P.gifTrending(offset);
      if (mine !== seq.current) return;
      setGifs((cur) => (offset === 0 ? r.gifs : [...cur, ...r.gifs]));
      setNext(r.nextOffset);
      setState("ok");
    } catch (e) {
      if (mine !== seq.current) return;
      setState(e instanceof ApiError && e.status === 422 && /available/.test(e.message) ? "off" : "error");
    }
  }

  useEffect(() => {
    const t = window.setTimeout(() => void load(q.trim(), 0), q ? 350 : 0);
    return () => window.clearTimeout(t);
  }, [q]);

  return (
    <Sheet title="Add a GIF" onClose={onClose}>
      <div className="stack">
        <input className="input" type="search" placeholder="Search GIPHY" value={q} maxLength={50} autoFocus
               aria-label="Search GIPHY" onChange={(e) => setQ(e.target.value)} enterKeyHint="search" />
        <span className="tag">{q.trim() ? `Results for “${q.trim()}”` : "Trending"}</span>
        {state === "off" ? <p className="small">GIFs aren't switched on yet.</p> : null}
        {state === "error" ? <p className="small">Couldn't reach GIPHY. Check your connection and try again.</p> : null}
        {state === "ok" && !gifs.length ? <p className="small">No GIFs for that. Try another word.</p> : null}
        {state === "loading" && !gifs.length ? <span className="meta">Loading…</span> : null}
        <div className="gif-grid">
          {gifs.map((g) => (
            <button key={g.id} className="gif-pick" onClick={() => onPick(g)} aria-label={g.title || "GIF"}>
              <img src={g.previewUrl} alt="" loading="lazy" referrerPolicy="no-referrer" width={g.width ?? undefined} height={g.height ?? undefined} />
            </button>
          ))}
        </div>
        {next !== null && state === "ok" ? <Button variant="ghost" onClick={() => void load(q.trim(), next)}>More</Button> : null}
        <span className="meta giphy">Powered by GIPHY</span>
      </div>
    </Sheet>
  );
}
