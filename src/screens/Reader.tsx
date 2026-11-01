import { useCallback, useEffect, useLayoutEffect, useRef, useState, type PointerEvent as RPointerEvent } from "react";
import { ApiError } from "../lib/api.ts";
import * as B from "../lib/books.ts";
import { count } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { emit } from "../lib/postStore.ts";
import { back, navigate } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import { REACTIONS, type Post, type Reaction } from "../lib/types.ts";
import { BackIcon, Button, Portal } from "../components/ui.tsx";
import { Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";

const GAP = 56;   // between columns = twice the side margin

/**
 * The e-book reader. A chapter is laid out once in CSS columns, one column per page; turning
 * slides to the next column. Tap the right side / left side to turn, the middle for the bars;
 * swipe works too. After the last page comes the end page (react, respond, what's next).
 * My place (chapter + how far in) is kept on this phone; opening a chapter tells the server
 * how far I've got.
 */
export function Reader({ id, no }: { id: string; no: number }) {
  const [prefs, setPrefsState] = useState(B.loadPrefs);
  const [data, setData] = useState<B.ChapterRead | null>(null);
  const [index, setIndex] = useState<B.ChapterEntry[] | null>(null);
  const [error, setError] = useState<string>();
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(1);
  const [chrome, setChrome] = useState(false);
  const [sheet, setSheet] = useState<"settings" | "contents" | null>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const flow = useRef<HTMLDivElement>(null);
  const startAt = useRef<number | "end">(0);
  const toast = useToast();

  const setPrefs = (p: B.ReaderPrefs) => { setPrefsState(p); B.savePrefs(p); };

  // Load the chapter (and the index once).
  useEffect(() => {
    let stop = false;
    setData(null);
    setError(undefined);
    const place = B.placeOf(id);
    const fromEnd = sessionStorage.getItem("confess.reader.fromEnd") === `${id}:${no}`;
    sessionStorage.removeItem("confess.reader.fromEnd");
    startAt.current = fromEnd ? "end" : place && place.chapter === no ? place.at : 0;
    B.chapter(id, no).then((r) => { if (!stop) setData(r); }).catch((e) => {
      if (stop) return;
      if (e instanceof ApiError && e.status === 404) B.forgetPlace(id);
      setError(e instanceof ApiError && e.status === 404 ? "This chapter isn't available." : errorMessage(e));
    });
    return () => { stop = true; };
  }, [id, no]);

  useEffect(() => {
    B.get(id).then((r) => setIndex(r.chapters.filter((c) => !c.state || c.state === "released"))).catch(() => undefined);
  }, [id]);

  // Lay out: column width = the page, count the columns.
  const layout = useCallback(() => {
    const vp = viewport.current, fl = flow.current;
    if (!vp || !fl) return;
    const w = vp.clientWidth, h = vp.clientHeight;
    fl.style.setProperty("--page-w", `${w}px`);
    fl.style.setProperty("--page-h", `${h}px`);
    fl.style.setProperty("--gap", `${GAP}px`);
    const n = Math.max(1, Math.round((fl.scrollWidth + GAP) / (w + GAP)));
    setPages(n);
    return n;
  }, []);

  // First layout: my saved place. After a settings change: the same spot, re-measured.
  const laidOut = useRef(false);
  const where = useRef(0);
  where.current = page >= pages ? -1 : pages > 1 ? page / (pages - 1) : 0;
  useLayoutEffect(() => {
    if (!data) return;
    const n = layout() ?? 1;
    if (!laidOut.current) {
      laidOut.current = true;
      const s = startAt.current;
      setPage(s === "end" ? n - 1 : Math.min(n - 1, Math.round(s * (n - 1))));
    } else {
      setPage(where.current < 0 ? n : Math.min(n - 1, Math.round(where.current * (n - 1))));
    }
  }, [data, prefs, layout]);

  useEffect(() => {
    const onResize = () => {
      const before = pages > 1 ? page / (pages - 1) : 0;
      const n = layout() ?? 1;
      setPage(Math.min(n - 1, Math.round(before * (n - 1))));
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [layout, page, pages]);

  // Fonts arrive late on a first visit: lay out again once they're in.
  useEffect(() => { void document.fonts?.ready.then(() => layout()); }, [prefs.font, layout]);

  // Slide to the page.
  useEffect(() => {
    const fl = flow.current, vp = viewport.current;
    if (!fl || !vp) return;
    fl.style.setProperty("--x", `${-Math.min(page, pages - 1) * (vp.clientWidth + GAP)}px`);
  }, [page, pages]);

  // Remember my place.
  useEffect(() => {
    if (!data) return;
    const at = page >= pages ? 1 : pages > 1 ? page / (pages - 1) : 0;
    B.savePlace({ bookId: id, chapter: no, at: Math.min(1, +at.toFixed(3)) });
  }, [page, pages, data, id, no]);

  const atEnd = page >= pages;
  const turn = useCallback((dir: 1 | -1) => {
    if (!data) return;
    if (dir === 1) {
      if (page < pages) setPage(page + 1);
      else if (data.next) navigate({ name: "read", id, no: data.next }, true);
    } else if (page > 0) setPage(page - 1);
    else if (data.prev) {
      sessionStorage.setItem("confess.reader.fromEnd", `${id}:${data.prev}`);
      navigate({ name: "read", id, no: data.prev }, true);
    }
  }, [data, page, pages, id]);

  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (sheet) return;
      if (e.key === "ArrowRight" || e.key === " " || e.key === "PageDown") { e.preventDefault(); turn(1); }
      if (e.key === "ArrowLeft" || e.key === "PageUp") { e.preventDefault(); turn(-1); }
      if (e.key === "Escape") back();
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [turn, sheet]);

  // Taps and swipes.
  const touch = useRef<{ x: number; y: number; t: number } | null>(null);
  function down(e: RPointerEvent) {
    // Buttons on the end page are buttons, not page turns.
    touch.current = (e.target as HTMLElement).closest("button, a, input, textarea") ? null : { x: e.clientX, y: e.clientY, t: Date.now() };
  }
  function up(e: RPointerEvent) {
    const t = touch.current;
    touch.current = null;
    if (!t) return;
    const dx = e.clientX - t.x, dy = e.clientY - t.y;
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) { turn(dx < 0 ? 1 : -1); return; }
    if (Math.abs(dx) > 10 || Math.abs(dy) > 10 || Date.now() - t.t > 600) return;
    const w = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const fx = (e.clientX - w.left) / w.width;
    if (fx > 0.68) turn(1);
    else if (fx < 0.32) turn(-1);
    else setChrome((c) => !c);
  }

  const theme = ["paper", "sepia", "night"][prefs.theme];
  const font = B.FONTS.find((f) => f.id === prefs.font) ?? B.FONTS[0];
  const total = index?.length ?? data?.book.chapterCount ?? 0;
  const minutesLeft = data ? Math.ceil(((data.chapter.text.split(/\s+/).length) * (1 - (pages > 1 ? page / (pages - 1) : 1))) / 230) : 0;

  return (
    <Portal>
      <div className={`reader theme-${theme} ${font.cls} size-${prefs.size} space-${prefs.spacing}${prefs.justify ? " justify" : ""}${prefs.lines ? " lines" : ""}${prefs.turn === "instant" ? " instant" : ""}${chrome ? " chrome" : ""}`}
           role="document" aria-label={data ? `${data.book.title}, chapter ${no}` : "Reader"}>
        <div className="reader-top">
          <button className="icon-btn" aria-label="Back" onClick={back}><BackIcon /></button>
          <span className="reader-book clamp-1">{data?.book.title ?? ""}</span>
          <button className="icon-btn reader-aa" aria-label="Reading settings" onClick={() => setSheet("settings")}>Aa</button>
          <button className="icon-btn" aria-label="Contents" onClick={() => setSheet("contents")}>☰</button>
        </div>

        {error ? (
          <div className="reader-msg"><p>{error}</p><Button variant="outline" onClick={back}>Back to the book</Button></div>
        ) : !data ? (
          <div className="reader-msg"><span className="spinner" aria-label={`Turning to chapter ${no}…`} /></div>
        ) : (
          <div className="reader-stage" onPointerDown={down} onPointerUp={up}>
            <div className="reader-viewport" ref={viewport} aria-hidden={atEnd}>
              <div className="reader-flow" ref={flow} aria-live="polite">
                <p className="reader-label">CHAPTER {no}</p>
                <h1 className="reader-title">{B.chapterTitle(no, data.chapter.chapter?.title)}</h1>
                {data.chapter.text.split(/\n+/).map((para, i) => <p key={i} className="reader-p">{para}</p>)}
              </div>
            </div>
            {atEnd ? <EndPage data={data} id={id} no={no} nextTitle={data.next ? B.chapterTitle(data.next, index?.find((c) => c.chapterNo === data.next)?.title) : ""} onChange={(p) => setData({ ...data, chapter: p })}
                              onLibrary={(b) => setData({ ...data, book: b })} toast={toast} /> : null}
          </div>
        )}

        <div className="reader-foot" aria-live="off">
          {data ? (atEnd ? `End of chapter ${no}` : `Page ${page + 1} of ${pages} · Chapter ${no}${total ? ` of ${total}` : ""}`) : ""}
          {data && !atEnd && chrome ? ` · ${minutesLeft <= 1 ? "less than a minute" : `${minutesLeft} min`} left` : ""}
        </div>
        <div className="reader-progress" aria-hidden><i className={`w${Math.round(((atEnd ? pages : page + 1) / Math.max(1, pages)) * 20) * 5}`} /></div>

        {sheet === "settings" ? <ReaderSettings prefs={prefs} onChange={setPrefs} onClose={() => setSheet(null)} /> : null}
        {sheet === "contents" ? (
          <Sheet title="Contents" onClose={() => setSheet(null)}>
            <div className="stack-sm">
              <h2 className="title">{data?.book.title}</h2>
              {(index ?? []).map((c) => (
                <button key={c.chapterNo} className={`contents-row${c.chapterNo === no ? " current" : ""}`}
                        onClick={() => { setSheet(null); if (c.chapterNo !== no) navigate({ name: "read", id, no: c.chapterNo }, true); }}>
                  <span className="contents-no">{String(c.chapterNo).padStart(2, "0")}</span>
                  <span className="contents-title grow">{B.chapterTitle(c.chapterNo, c.title)}</span>
                </button>
              ))}
              <Button variant="ghost" onClick={() => { setSheet(null); navigate({ name: "book", id }); }}>Back to the book</Button>
            </div>
          </Sheet>
        ) : null}
      </div>
    </Portal>
  );
}

function EndPage({ data, id, no, nextTitle, onChange, onLibrary, toast }:
  { data: B.ChapterRead; id: string; no: number; nextTitle: string; onChange: (p: Post) => void; onLibrary: (b: B.Book) => void; toast: (m: string) => void }) {
  const p = data.chapter;
  const mine = p.myReaction ?? (p.likedByMe ? "felt" : null);
  const [busy, setBusy] = useState(false);

  async function react(r: Reaction) {
    const next = r === mine ? null : r;
    try {
      const res = next ? await P.react(p.postId, next) : await P.unreact(p.postId);
      const upd = { ...p, myReaction: res.myReaction, likedByMe: res.liked, likeCount: res.likeCount, reactions: res.reactions };
      onChange(upd);
      emit({ type: "update", post: upd });
    } catch (e) {
      toast(errorMessage(e));
    }
  }

  return (
    <div className="reader-end" role="region" aria-label={`End of chapter ${no}`}>
      <p className="reader-label">END OF CHAPTER {no}</p>
      <p className="reader-felt">{p.likeCount ? `${count(p.likeCount)} felt this chapter` : "How did this chapter feel?"}</p>
      <div className="story-reactions end-reactions" role="group" aria-label="React to this chapter">
        {REACTIONS.map((r) => (
          <button key={r.key} className={`story-react${mine === r.key ? " on" : ""}`} aria-pressed={mine === r.key}
                  aria-label={r.label} onClick={() => void react(r.key)}>{r.emoji}</button>
        ))}
      </div>
      {p.commentsEnabled || p.commentCount ? (
        <Button variant="outline" onClick={() => navigate({ name: "post", id: p.postId })}>
          {p.commentCount ? `Responses · ${count(p.commentCount)}` : "Respond"}
        </Button>
      ) : null}
      <hr className="reader-rule" />
      {data.next ? (
        <button className="reader-next" onClick={() => navigate({ name: "read", id, no: data.next! }, true)}>
          <span className="reader-label">NEXT</span>
          <span className="reader-next-title">{nextTitle}</span>
          <span className="meta">Turn the page →</span>
        </button>
      ) : data.book.status === "completed" ? (
        <div className="stack-sm center-text"><p className="reader-label">THE END</p><p>That's the whole story.</p></div>
      ) : (
        <div className="stack-sm center-text">
          <p className="reader-next-title">You're all caught up</p>
          <p className="small">The next chapter hasn't come out yet.</p>
          {!data.book.isMine && !data.book.following ? (
            <>
              <p className="small">Add this book to your library and we'll tell you when it does.</p>
              <Button busy={busy} onClick={async () => {
                setBusy(true);
                try { onLibrary(await B.follow(id, true)); toast("Added to your library — you'll hear about new chapters"); }
                catch (e) { toast(errorMessage(e)); }
                finally { setBusy(false); }
              }}>Add to library</Button>
            </>
          ) : null}
        </div>
      )}
      <Button variant="ghost" onClick={() => navigate({ name: "book", id })}>Back to the book</Button>
    </div>
  );
}

function ReaderSettings({ prefs, onChange, onClose }: { prefs: B.ReaderPrefs; onChange: (p: B.ReaderPrefs) => void; onClose: () => void }) {
  const set = (p: Partial<B.ReaderPrefs>) => onChange({ ...prefs, ...p });
  return (
    <Sheet title="Reading" onClose={onClose}>
      <div className="stack">
        <h2 className="title">Reading</h2>
        <div className="row spread">
          <span className="setting-label">Text size</span>
          <span className="row">
            <button className="icon-btn" aria-label="Smaller text" disabled={prefs.size <= B.SIZE_MIN} onClick={() => set({ size: prefs.size - 1 })}>A−</button>
            <span className="meta">{prefs.size}</span>
            <button className="icon-btn" aria-label="Bigger text" disabled={prefs.size >= B.SIZE_MAX} onClick={() => set({ size: prefs.size + 1 })}>A+</button>
          </span>
        </div>
        <span className="tag">Page</span>
        <div className="segmented three" role="radiogroup" aria-label="Page">
          {["Paper", "Sepia", "Night"].map((l, i) => (
            <button key={l} role="radio" aria-checked={prefs.theme === i} aria-selected={prefs.theme === i}
                    onClick={() => set({ theme: i as 0 | 1 | 2 })}>{l}</button>
          ))}
        </div>
        <span className="tag">Line spacing</span>
        <div className="segmented three" role="radiogroup" aria-label="Line spacing">
          {["Compact", "Normal", "Airy"].map((l, i) => (
            <button key={l} role="radio" aria-checked={prefs.spacing === i} aria-selected={prefs.spacing === i}
                    onClick={() => set({ spacing: i as 0 | 1 | 2 })}>{l}</button>
          ))}
        </div>
        <span className="tag">Typeface</span>
        <div className="font-list" role="radiogroup" aria-label="Typeface">
          {B.FONTS.map((f) => (
            <button key={f.id} role="radio" aria-checked={prefs.font === f.id} className={`font-pick ${f.cls}${prefs.font === f.id ? " on" : ""}`}
                    onClick={() => set({ font: f.id })}>
              <span className="font-name">{f.name}</span><span className="meta">{f.sub}</span>
            </button>
          ))}
        </div>
        <span className="tag">Page turn</span>
        <div className="segmented" role="radiogroup" aria-label="Page turn">
          {(["slide", "instant"] as const).map((t) => (
            <button key={t} role="radio" aria-checked={prefs.turn === t} aria-selected={prefs.turn === t} onClick={() => set({ turn: t })}>
              {t === "slide" ? "Slide" : "Instant"}
            </button>
          ))}
        </div>
        <label className="setting-row toggle-row">
          <span className="grow setting-label">Justify text</span>
          <input type="checkbox" role="switch" className="switch" checked={prefs.justify} onChange={(e) => set({ justify: e.target.checked })} />
        </label>
        <label className="setting-row toggle-row">
          <span className="grow setting-label">Ruled lines</span>
          <input type="checkbox" role="switch" className="switch" checked={prefs.lines} onChange={(e) => set({ lines: e.target.checked })} />
        </label>
      </div>
    </Sheet>
  );
}
