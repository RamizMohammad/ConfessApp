import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../lib/session.tsx";
import * as P from "../lib/posts.ts";
import { apply, cardActions, remember, subscribe } from "../lib/postStore.ts";
import { navigate } from "../lib/router.ts";
import { MOODS, type Mood, type Post } from "../lib/types.ts";
import { PostCard } from "../components/post.tsx";
import { Button } from "../components/ui.tsx";

type Load = "idle" | "loading" | "more" | "error" | "moreError";

export function Feed({ active }: { active: boolean }) {
  const [sort, setSort] = useState<P.Sort>("latest");
  const [scope, setScope] = useState<P.Scope>("all");
  const [mood, setMood] = useState<Mood | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<Load>("loading");
  const [error, setError] = useState<string>();
  const seq = useRef(0);
  const sentinel = useRef<HTMLDivElement>(null);
  const scrollY = useRef(0);

  const load = useCallback(async (more: boolean, c: string | null) => {
    const mine = ++seq.current;
    setState(more ? "more" : "loading");
    try {
      const r = await P.feed({ sort, scope, mood, cursor: more ? c : null });
      if (mine !== seq.current) return;
      remember(r.posts);
      setPosts((cur) => (more ? [...cur, ...r.posts.filter((p) => !cur.some((x) => x.postId === p.postId))] : r.posts));
      setCursor(r.nextCursor);
      setState("idle");
    } catch (e) {
      if (mine !== seq.current) return;
      setError(errorMessage(e));
      setState(more ? "moreError" : "error");
    }
  }, [sort, scope, mood]);

  useEffect(() => {
    void load(false, null);
  }, [load]);

  useEffect(() => subscribe((e) => setPosts((cur) => apply(cur, e))), []);

  // Keep the reading position when coming back from a confession.
  useEffect(() => {
    if (active) window.scrollTo(0, scrollY.current);
    else return;
    const save = () => { scrollY.current = window.scrollY; };
    window.addEventListener("scroll", save, { passive: true });
    return () => window.removeEventListener("scroll", save);
  }, [active]);

  // Infinite scroll.
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !active) return;
    const io = new IntersectionObserver((entries) => {
      if (entries.some((x) => x.isIntersecting) && cursor && state === "idle") void load(true, cursor);
    }, { rootMargin: "800px" });
    io.observe(el);
    return () => io.disconnect();
  }, [cursor, state, load, active]);

  function pickSort(s: P.Sort) {
    if (s === sort) {
      window.scrollTo({ top: 0, behavior: "smooth" });
      void load(false, null);
    } else setSort(s);
  }

  const actions = cardActions((p) => navigate({ name: "post", id: p.postId }));

  return (
    <section className="feed" hidden={!active} aria-label="Feed">
      <div className="feed-controls">
        <div className="segmented" role="tablist">
          <button role="tab" aria-selected={sort === "latest"} onClick={() => pickSort("latest")}>Latest</button>
          <button role="tab" aria-selected={sort === "top"} onClick={() => pickSort("top")}>Most felt</button>
        </div>
        <div className="chips scroll-x">
          <button className={`chip${scope === "following" ? " on" : ""}`} onClick={() => setScope(scope === "all" ? "following" : "all")}>
            Following
          </button>
          {MOODS.map((m) => (
            <button key={m.key} className={`chip${mood === m.key ? " on" : ""}`} onClick={() => setMood(mood === m.key ? null : m.key)}>
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {state === "loading" && !posts.length ? <div className="feed-skeleton" aria-busy="true"><div /><div /><div /></div> : null}

      {state === "error" && !posts.length ? (
        <div className="empty">
          <h2 className="title">The room is quiet right now.</h2>
          <p className="small">{error}</p>
          <Button variant="outline" onClick={() => void load(false, null)}>Try again</Button>
        </div>
      ) : null}

      {state === "idle" && !posts.length ? (
        <div className="empty">
          <h2 className="title">{sort === "top" ? "Nothing has been felt this week yet." : scope === "following" ? "No letters from people you follow yet." : "No confessions yet."}</h2>
          <p className="small">Be the first to say the thing out loud.</p>
          <Button onClick={() => navigate({ name: "write" })}>Write a confession</Button>
        </div>
      ) : null}

      <div className="cards">
        {posts.map((p) => <PostCard key={p.postId} post={p} actions={actions} />)}
      </div>

      <div ref={sentinel} className="feed-end">
        {state === "more" ? <span className="spinner" aria-label="Loading more" /> : null}
        {state === "moreError" ? <Button variant="ghost" onClick={() => void load(true, cursor)}>Couldn't load more. Try again</Button> : null}
        {state === "idle" && posts.length > 0 && !cursor ? (
          <div className="caught-up"><p className="quote">You're all caught up.</p><span className="meta">Sit with it for a moment.</span></div>
        ) : null}
      </div>
    </section>
  );
}
