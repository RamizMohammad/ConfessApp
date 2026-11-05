/**
 * dialog_gif_picker + GifPicker.java: Letty × GIPHY header, search pill, TRENDING / RESULTS FOR,
 * a two-column staggered grid (item_gif_tile), "Powered by GIPHY". Searches through the Confess
 * server (/gifs/*), so the GIPHY key never reaches the phone.
 */
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import * as P from "../lib/posts.ts";
import { S } from "../lib/strings.ts";
import type { Gif } from "../lib/types.ts";
import { Art } from "../ui/Art.tsx";
import { Icon } from "../ui/Icon.tsx";
import { haptic, Sheet, Spinner } from "../ui/kit.tsx";
import { GifView } from "./post.tsx";

/** GiphyClient.normalise: trimmed, single spaces, at most 50. */
const normalise = (s: string) => s.trim().replace(/\s+/g, " ").slice(0, 50);

export function GifPicker({ open, onPick, onClose }: { open: boolean; onPick: (g: Gif) => void; onClose: () => void }) {
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  const [gifs, setGifs] = useState<Gif[]>([]);
  const [next, setNext] = useState<number | null>(null);
  const [state, setState] = useState<"loading" | "ok" | "error" | "off">("loading");
  const seq = useRef(0);
  const grid = useRef<HTMLDivElement>(null);
  const loading = useRef(false);

  async function load(q: string, offset: number) {
    const mine = ++seq.current;
    loading.current = true;
    if (offset === 0) { setState("loading"); setGifs([]); setNext(null); }
    try {
      const r = q ? await P.gifSearch(q, offset) : await P.gifTrending(offset);
      if (mine !== seq.current) return;
      setGifs((cur) => (offset === 0 ? r.gifs : [...cur, ...r.gifs.filter((g) => !cur.some((x) => x.id === g.id))]));
      setNext(r.nextOffset);
      setState("ok");
      if (offset === 0) grid.current?.scrollTo(0, 0);
    } catch (e) {
      if (mine !== seq.current) return;
      setNext(null);
      setState(e instanceof ApiError && e.status === 422 && /available/i.test(e.message) ? "off" : "error");
    } finally {
      if (mine === seq.current) loading.current = false;
    }
  }

  useEffect(() => {
    if (!open) return;
    setText("");
    setQuery("");
    void load("", 0);
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // SEARCH_DELAY_MS 400 (straight away when cleared).
  useEffect(() => {
    if (!open) return;
    const q = normalise(text);
    if (q === query) return;
    const t = window.setTimeout(() => { setQuery(q); void load(q, 0); }, q ? 400 : 0);
    return () => window.clearTimeout(t);
  }, [text]); // eslint-disable-line react-hooks/exhaustive-deps

  const onScroll = () => {
    const g = grid.current;
    if (!g || loading.current || next === null || state !== "ok") return;
    if (g.scrollTop + g.clientHeight > g.scrollHeight - 600) void load(query, next);
  };

  // Two columns, filled in order like StaggeredGridLayoutManager (each tile goes to the shorter column).
  const cols: Gif[][] = [[], []];
  const h = [0, 0];
  for (const g of gifs) {
    const r = g.width && g.height ? Math.min(1.6, g.height / g.width) : 1;
    const c = h[0]! <= h[1]! ? 0 : 1;
    cols[c]!.push(g);
    h[c]! += r;
  }

  const message = state === "off" ? S("gif_not_set_up") : state === "error" && !gifs.length ? S("gif_error")
    : state === "ok" && !gifs.length ? S("gif_none") : null;

  return (
    <Sheet open={open} onClose={onClose} label={S("cd_pick_gif")} className="gifp-sheet">
      <div className="gifp">
        <div className="gifp-head">
          <span className="gifp-brand">
            <Art name="logo_letty" size={30} />
            <span className="t-body gifp-x">×</span>
            <span className="gifp-giphy">{S("giphy")}</span>
          </span>
          <button type="button" className="gifp-close" aria-label={S("action_close")} onClick={onClose}><Icon name="close" size={24} /></button>
        </div>
        <label className="gifp-search">
          <Icon name="search" size={24} />
          <input type="search" value={text} maxLength={50} placeholder={S("gif_search_hint")} aria-label={S("gif_search_hint")}
                 enterKeyHint="search" autoComplete="off" disabled={state === "off"}
                 onChange={(e) => setText(e.target.value)}
                 onKeyDown={(e) => {
                   if (e.key !== "Enter") return;
                   e.preventDefault();
                   const q = normalise(text);
                   (e.target as HTMLInputElement).blur();
                   if (q !== query) { setQuery(q); void load(q, 0); }
                 }} />
        </label>
        <span className="t-meta-small gifp-heading">{query ? S("gif_results", query) : S("gif_trending")}</span>
        <div className="gifp-grid-wrap">
          <div className="gifp-grid k-sheet-scroll" ref={grid} onScroll={onScroll}>
            {cols.map((col, i) => (
              <div key={i} className="gifp-col">
                {col.map((g) => (
                  <GifView key={g.id} gif={g} thumb maxRatio={1.6} className="gifp-tile"
                           onClick={() => { haptic(); onPick(g); onClose(); }} />
                ))}
              </div>
            ))}
          </div>
          {state === "loading" && !gifs.length ? <span className="gifp-center"><Spinner /></span> : null}
          {message ? <p className="gifp-center t-body-medium gifp-state">{message}</p> : null}
        </div>
        <span className="t-meta-small gifp-powered">{S("gif_powered_by")}</span>
      </div>
    </Sheet>
  );
}
