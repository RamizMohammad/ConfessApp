import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { type BurstMode, fetchBurst, setBurstMode, SMALL, spanLabel } from "../lib/bursts.ts";
import { apply, cardActions, remember, subscribe } from "../lib/postStore.ts";
import { navigate } from "../lib/router.ts";
import type { Mood, Post } from "../lib/types.ts";
import { Aura } from "./Aura.tsx";
import { PostCard, Sheet } from "./post.tsx";
import { Button } from "./ui.tsx";

export interface BurstCtx { scope: "all" | "following"; mood: Mood | null }

/** The feed card for a burst, in the reader's chosen look. */
export function BurstCard({ head, mode, ctx }: { head: Post; mode: BurstMode; ctx: BurstCtx }) {
  const total = head.burst?.count ?? 1;
  if (mode === "stack" && total > SMALL) return <BurstStack head={head} ctx={ctx} />;
  return <BurstSlider head={head} ctx={ctx} />;
}

function useBurst(head: Post, ctx: BurstCtx, eager: boolean) {
  const id = head.burst!.id;
  const [posts, setPosts] = useState<Post[]>([]);
  const [total, setTotal] = useState(head.burst!.count);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<"idle" | "loading" | "done" | "error">("idle");

  const load = useCallback(async (c: string | null) => {
    setState("loading");
    try {
      const r = await fetchBurst(id, { scope: ctx.scope, mood: ctx.mood, cursor: c });
      remember(r.posts);
      setPosts((cur) => (c ? [...cur, ...r.posts.filter((p) => !cur.some((x) => x.postId === p.postId))] : r.posts));
      setTotal(r.total);
      setCursor(r.nextCursor);
      setState(r.nextCursor ? "idle" : "done");
    } catch {
      setState("error");
    }
  }, [id, ctx.scope, ctx.mood]);

  useEffect(() => { if (eager) void load(null); }, [eager, load]);
  useEffect(() => subscribe((e) => setPosts((cur) => apply(cur, e))), []);
  return { posts, total, cursor, state, load };
}

/** Swipe through them in one card, oldest first. */
function BurstSlider({ head, ctx }: { head: Post; ctx: BurstCtx }) {
  const b = useBurst(head, ctx, true);
  const [at, setAt] = useState(0);
  const track = useRef<HTMLDivElement>(null);
  const actions = cardActions((p) => navigate({ name: "post", id: p.postId }));
  const shown = b.posts.length ? b.posts : [head];
  // When the burst arrives, start at the oldest (the newest was a placeholder at the end).
  const started = useRef(false);
  useLayoutEffect(() => {
    if (!started.current && b.posts.length && track.current) { started.current = true; track.current.scrollLeft = 0; setAt(0); }
  }, [b.posts.length]);

  function onScroll() {
    const el = track.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    setAt(i);
    if (i >= b.posts.length - 2 && b.state === "idle" && b.cursor) void b.load(b.cursor);
  }

  function go(dir: 1 | -1) {
    const el = track.current;
    if (el) el.scrollTo({ left: (at + dir) * el.clientWidth, behavior: "smooth" });
  }

  return (
    <section className="burst" aria-label={`@${head.author.alias} · ${b.total} confessions · swipe, oldest first`}>
      <div className="burst-head">
        <span className="meta">{b.total} in a row · oldest first</span>
        <span className="meta burst-count">{Math.min(at + 1, b.total)} / {b.total}</span>
      </div>
      <div className="burst-track" ref={track} onScroll={onScroll}>
        {shown.map((p) => <div className="burst-slide" key={b.posts.length ? p.postId : "placeholder"}><PostCard post={p} actions={actions} /></div>)}
        {b.state === "loading" && b.posts.length ? <div className="burst-slide burst-loading"><span className="spinner" aria-label="Loading" /></div> : null}
      </div>
      <div className="burst-nav">
        <button className="icon-btn small" aria-label="Previous" disabled={at <= 0} onClick={() => go(-1)}>‹</button>
        <span className="burst-dots" aria-hidden>
          {Array.from({ length: Math.min(b.total, 7) }, (_, i) => <i key={i} className={i === Math.min(at, 6) ? "on" : ""} />)}
        </span>
        <button className="icon-btn small" aria-label="Next" disabled={at >= shown.length - 1} onClick={() => go(1)}>›</button>
      </div>
    </section>
  );
}

/** More than 3: one card that opens them full screen, oldest first. */
function BurstStack({ head, ctx }: { head: Post; ctx: BurstCtx }) {
  const total = head.burst!.count;
  const open = () => navigate({ name: "burst", id: head.burst!.id, scope: ctx.scope });
  return (
    <button className="burst-stack" onClick={open}
            aria-label={`@${head.author.alias} made ${total} confessions. Opens them, oldest first.`}>
      <span className="stack-sheet s3" aria-hidden /><span className="stack-sheet s2" aria-hidden />
      <span className="paper card stack-top">
        <span className="card-head">
          <Aura value={head.author.avatar} size={32} />
          <span className="card-who"><span className="alias">@{head.author.alias} made {total} confessions</span><span className="meta">Oldest first</span></span>
        </span>
        <span className="quote clamp">{head.text}</span>
        <span className="burst-read">Read from the first →</span>
      </span>
    </button>
  );
}

/** /burst/<id> — the whole run, oldest first. */
export function BurstList({ id, scope }: { id: string; scope: "all" | "following" }) {
  const [posts, setPosts] = useState<Post[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<"loading" | "idle" | "done" | "error">("loading");
  const sentinel = useRef<HTMLDivElement>(null);

  const load = useCallback(async (c: string | null) => {
    setState("loading");
    try {
      const r = await fetchBurst(id, { scope, cursor: c, limit: 10 });
      remember(r.posts);
      setPosts((cur) => (c ? [...cur, ...r.posts.filter((p) => !cur.some((x) => x.postId === p.postId))] : r.posts));
      setTotal(r.total);
      setCursor(r.nextCursor);
      setState(r.nextCursor ? "idle" : "done");
    } catch {
      setState("error");
    }
  }, [id, scope]);
  useEffect(() => { void load(null); }, [load]);
  useEffect(() => subscribe((e) => setPosts((cur) => apply(cur, e))), []);
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => { if (e.some((x) => x.isIntersecting) && state === "idle" && cursor) void load(cursor); }, { rootMargin: "600px" });
    io.observe(el);
    return () => io.disconnect();
  }, [state, cursor, load]);

  const actions = cardActions((p) => navigate({ name: "post", id: p.postId }));
  const first = posts[0], last = posts[posts.length - 1];
  return (
    <>
      {first ? (
        <div className="stack-xs burst-list-head">
          <span className="title">@{first.author.alias} made {total} confessions</span>
          <span className="meta">{last ? spanLabel(first.createdAt, last.createdAt) : "Oldest first"}</span>
        </div>
      ) : null}
      <div className="cards">
        {posts.map((p, i) => (
          <div key={p.postId} className="stack-xs">
            <span className="meta burst-pos">{i + 1} of {total}</span>
            <PostCard post={p} actions={actions} />
          </div>
        ))}
      </div>
      <div ref={sentinel} className="feed-end">
        {state === "loading" ? <span className="spinner" aria-label="Loading" /> : null}
        {state === "error" ? <Button variant="ghost" onClick={() => void load(cursor)}>Couldn't load. Try again</Button> : null}
        {state === "done" && posts.length ? <span className="meta">That's all of them.</span> : null}
      </div>
    </>
  );
}

/** Asked once, the first time a burst shows up (also Settings → Appearance). */
export function BurstChooser({ onClose, current }: { onClose: () => void; current?: BurstMode }) {
  const [mode, setMode] = useState<BurstMode>(current ?? "slider");
  const options: { key: BurstMode; label: string; sub: string }[] = [
    { key: "slider", label: "Slider", sub: "Swipe through them" },
    { key: "stack", label: "Stack", sub: "More than 3: one card, tap to read" },
    { key: "separate", label: "One by one", sub: "Every card on its own" },
  ];
  return (
    <Sheet title="When someone posts a lot at once" onClose={() => { setBurstMode(current ?? "slider"); onClose(); }}>
      <div className="stack">
        <div className="stack-sm">
          <h2 className="title">When someone posts a lot at once</h2>
          <p className="small">Their confessions can come as one group, read from the first. Pick how you'd like to see them.</p>
        </div>
        <div className="choice-list" role="radiogroup">
          {options.map((o) => (
            <label key={o.key} className={`choice release-choice${mode === o.key ? " on" : ""}`}>
              <input type="radio" name="burst-mode" checked={mode === o.key} onChange={() => setMode(o.key)} />
              <span className={`burst-demo demo-${o.key}`} aria-hidden><i /><i /><i /></span>
              <span className="stack-xs"><span>{o.label}</span><span className="small">{o.sub}</span></span>
            </label>
          ))}
        </div>
        <p className="small">You can change this any time in Settings → Appearance.</p>
        <Button block onClick={() => { setBurstMode(mode); onClose(); }}>Use this</Button>
      </div>
    </Sheet>
  );
}
