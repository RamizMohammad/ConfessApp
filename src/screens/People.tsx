import { useEffect, useRef, useState } from "react";
import { ago, count } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { back, navigate } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { REACTIONS, type Comment, type Post } from "../lib/types.ts";
import { usePaged } from "../lib/usePaged.ts";
import * as U from "../lib/users.ts";
import { Aura } from "../components/Aura.tsx";
import { Close, Search as SearchIcon } from "../components/icons.tsx";
import { Empty, ListStatus } from "../components/list.tsx";
import { PersonRow } from "../components/people.tsx";
import { Button, Header } from "../components/ui.tsx";
import { PostList } from "./Profile.tsx";

/* ── Followers / following ──────────────────────────────────────────────── */

export function People({ kind }: { kind: "followers" | "following" }) {
  const list = usePaged<U.Person>(async (c) => {
    const r = await U.people(kind, c);
    return { items: r.users, next: r.nextCursor };
  }, (p) => p.alias, [kind]);
  return (
    <main className="screen">
      <Header title={kind === "followers" ? "Followers" : "Following"} onBack={back}
              right={<button className="icon-btn" aria-label="Find people" onClick={() => navigate({ name: "search" })}><SearchIcon /></button>} />
      <div className="segmented people-tabs" role="tablist">
        <button role="tab" aria-selected={kind === "following"} onClick={() => navigate({ name: "people", kind: "following" }, true)}>Following</button>
        <button role="tab" aria-selected={kind === "followers"} onClick={() => navigate({ name: "people", kind: "followers" }, true)}>Followers</button>
      </div>
      <div className="people-list">
        {list.items.map((p) => <PersonRow key={p.alias} person={p} />)}
      </div>
      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()}
                  empty={kind === "followers"
                    ? <Empty title="No followers yet" body="When someone follows you, they'll appear here." />
                    : (
                      <Empty title="You don't follow anyone yet" body="Open someone's profile and tap Follow.">
                        <Button variant="outline" onClick={() => navigate({ name: "search" })}>Find people</Button>
                      </Empty>
                    )} />
    </main>
  );
}

/* ── Search ─────────────────────────────────────────────────────────────── */

export function Search() {
  const [q, setQ] = useState("");
  const [users, setUsers] = useState<U.Person[] | null>(null);
  const [error, setError] = useState<string>();
  const seq = useRef(0);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => { input.current?.focus(); }, []);

  useEffect(() => {
    const term = q.trim().replace(/^@/, "");
    if (!term) { setUsers(null); setError(undefined); return; }
    const mine = ++seq.current;
    const t = window.setTimeout(async () => {
      try {
        const r = await U.search(term.slice(0, 20));
        if (mine === seq.current) { setUsers(r.users); setError(undefined); }
      } catch (e) {
        if (mine === seq.current) setError(errorMessage(e));
      }
    }, 250);
    return () => window.clearTimeout(t);
  }, [q]);

  const term = q.trim().replace(/^@/, "");
  return (
    <main className="screen">
      <Header title="Find people" onBack={back} />
      <div className="search-box">
        <SearchIcon />
        <input ref={input} className="input" type="search" placeholder="Search by alias" value={q} maxLength={21}
               autoCapitalize="none" autoCorrect="off" spellCheck={false} enterKeyHint="search"
               onChange={(e) => setQ(e.target.value)} aria-label="Search by alias" />
        {q ? <button className="icon-btn small" aria-label="Clear" onClick={() => setQ("")}><Close /></button> : null}
      </div>
      {error ? <p className="small error-text">{error}</p> : null}
      {!term ? <Empty title="Find someone." body="Type the start of an alias. Only aliases are searchable — never names or emails." /> : null}
      {term && users && !users.length ? <Empty title={`No one goes by “${term}”.`} body="Check the spelling — aliases are matched from the start." /> : null}
      <div className="people-list">
        {users?.map((p) => <PersonRow key={p.alias} person={p} showFollow={false} />)}
      </div>
    </main>
  );
}

/* ── My letters ─────────────────────────────────────────────────────────── */

export function MyLetters() {
  return (
    <main className="screen">
      <Header title="My letters" onBack={back} />
      <PostList fetch={(c) => U.myPosts(c)}
                empty={(
                  <Empty title="You haven't written anything yet." body="Your letters will live here — only your alias is shown.">
                    <Button onClick={() => navigate({ name: "write" })}>Write a confession</Button>
                  </Empty>
                )} />
    </main>
  );
}

/* ── Felt / responses on my letters ─────────────────────────────────────── */

export function Received({ kind }: { kind: "felt" | "responses" }) {
  const { me } = useSession();
  const list = usePaged<Post>(async (c) => {
    // Only letters that received something; skip ahead past pages with none (a few at most).
    let cursor = c, items: Post[] = [], next: string | null = null;
    for (let i = 0; i < 5; i++) {
      const r = await U.myPosts(cursor);
      items = items.concat(r.posts.filter((p) => (kind === "felt" ? p.likeCount > 0 : p.commentCount > 0)));
      next = cursor = r.nextCursor;
      if (items.length || !next) break;
    }
    return { items, next };
  }, (p) => p.postId, [kind]);
  const summary = kind === "felt"
    ? `${count(me?.likesReceived ?? 0)} felt received · ${count(me?.likesGiven ?? 0)} given`
    : `${count(me?.commentsReceived ?? 0)} responses received · ${count(me?.commentsMade ?? 0)} made`;
  return (
    <main className="screen">
      <Header title={kind === "felt" ? "Felt on your letters" : "Responses to your letters"} onBack={back} />
      <div className="segmented people-tabs" role="tablist">
        <button role="tab" aria-selected={kind === "felt"} onClick={() => navigate({ name: "received", kind: "felt" }, true)}>Felt</button>
        <button role="tab" aria-selected={kind === "responses"} onClick={() => navigate({ name: "received", kind: "responses" }, true)}>Responses</button>
      </div>
      <p className="meta received-summary">{summary}</p>
      <div className="stack">
        {list.items.map((p) => <ReceivedCard key={p.postId} post={p} kind={kind} />)}
      </div>
      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()}
                  empty={<Empty title={kind === "felt" ? "No one has felt your letters yet." : "No one has responded yet."}
                                body="When they do, you'll see who — letter by letter." />} />
    </main>
  );
}

function ReceivedCard({ post, kind }: { post: Post; kind: "felt" | "responses" }) {
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<(U.Liker | Comment)[] | null>(null);
  const [next, setNext] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function load(cursor: string | null) {
    setBusy(true);
    setError(undefined);
    try {
      if (kind === "felt") {
        const r = await U.likers(post.postId, cursor);
        setRows((cur) => [...(cursor ? cur ?? [] : []), ...r.users]);
        setNext(r.nextCursor);
      } else {
        const r = await P.comments(post.postId, cursor);
        setRows((cur) => [...(cursor ? cur ?? [] : []), ...r.comments.filter((c) => !c.isMine)]);
        setNext(r.nextCursor);
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const n = kind === "felt" ? post.likeCount : post.commentCount;
  return (
    <article className="plate received-card">
      <button className="received-head" aria-expanded={open}
              onClick={() => { const o = !open; setOpen(o); if (o && rows === null) void load(null); }}>
        <span className="grow stack-xs">
          <span className="clamp received-quote">{post.text || "GIF"}</span>
          <span className="meta">{ago(post.createdAt)} · {count(n)} {kind === "felt" ? "felt" : n === 1 ? "response" : "responses"}</span>
        </span>
        <span className="chev" aria-hidden>{open ? "⌃" : "⌄"}</span>
      </button>
      {open ? (
        <div className="received-rows">
          {rows?.map((r) => "likedAt" in r ? (
            <button key={r.alias} className="received-row" onClick={() => navigate({ name: "profile", alias: r.alias })}>
              <Aura value={r.avatar} size={30} /><span className="alias grow">@{r.alias}</span>
              <span aria-label={r.reaction}>{REACTIONS.find((x) => x.key === r.reaction)?.emoji ?? "❤️"}</span>
              <span className="meta">{ago(r.likedAt)}</span>
            </button>
          ) : (
            <button key={r.commentId} className="received-row" onClick={() => navigate({ name: "profile", alias: r.author.alias })}>
              <Aura value={r.author.avatar} size={30} />
              <span className="grow stack-xs"><span className="alias">@{r.author.alias}</span><span className="small clamp-1">{r.text || "GIF"}</span></span>
              <span className="meta">{ago(r.createdAt)}</span>
            </button>
          ))}
          {busy ? <span className="spinner center-self" aria-label="Loading" /> : null}
          {error ? <Button variant="ghost" onClick={() => void load(null)}>Couldn't load. Tap to try again.</Button> : null}
          {!busy && next ? <Button variant="ghost" onClick={() => void load(next)}>Show more</Button> : null}
          <Button variant="ghost" onClick={() => navigate({ name: "post", id: post.postId })}>Open letter</Button>
        </div>
      ) : null}
    </article>
  );
}
