import { useEffect, useMemo, useState, type ReactNode } from "react";
import { ApiError } from "../lib/api.ts";
import { resolveBanner } from "../lib/banner.ts";
import { count } from "../lib/format.ts";
import { parseStyle, type GraphData } from "../lib/graph.ts";
import * as P from "../lib/posts.ts";
import { apply, cardActions, remember, subscribe } from "../lib/postStore.ts";
import { back, navigate, openPost } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import type { Post } from "../lib/types.ts";
import { usePaged } from "../lib/usePaged.ts";
import * as U from "../lib/users.ts";
import { BannerArt, ConfessionGraph } from "../components/art.tsx";
import { Ban, Close, Flag, More } from "../components/icons.tsx";
import { Empty, ListStatus } from "../components/list.tsx";
import { FollowButton, onFollowChange } from "../components/people.tsx";
import { Confirm, PostCard, ReportSheet, Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { hasStory, RingAvatar } from "../components/stories.tsx";
import { BookCard } from "../components/books.tsx";
import * as Bk from "../lib/books.ts";
import { useTray } from "../lib/stories.ts";
import { BackIcon, Button } from "../components/ui.tsx";

const joined = (iso: string) => new Date(iso).toLocaleDateString(undefined, { month: "long", year: "numeric" });

/** Banner, avatar, alias, about and the three counts — the top of every profile and of the Me page. */
export function ProfileHero({ alias, avatar, about, banner, createdAt, letters, followers, following, top, actions, onCount }:
  { alias: string; avatar: string; about: string; banner: string; createdAt?: string; letters: number; followers: number;
    following: number; top?: ReactNode; actions?: ReactNode; onCount?: (k: "letters" | "followers" | "following") => void }) {
  const b = useMemo(() => resolveBanner(banner, avatar, alias), [banner, avatar, alias]);
  useTray();                                                    // re-render when their story ring changes
  const Count = ({ k, n, label }: { k: "letters" | "followers" | "following"; n: number; label: string }) =>
    onCount ? (
      <button className="count" onClick={() => onCount(k)}><b>{count(n)}</b><span>{label}</span></button>
    ) : <span className="count"><b>{count(n)}</b><span>{label}</span></span>;
  return (
    <header className="hero">
      <BannerArt banner={b} fadeFrom={0.62} animated className="hero-banner" />
      <div className="hero-top">{top}</div>
      <div className="hero-body">
        {hasStory(alias) ? (
          <button className="avatar-btn" aria-label={`@${alias}'s story`} onClick={() => navigate({ name: "stories", alias })}>
            <RingAvatar alias={alias} avatar={avatar} size={96} label={`@${alias}'s avatar`} />
          </button>
        ) : <RingAvatar alias={alias} avatar={avatar} size={96} label={`@${alias}'s avatar`} />}
        <h1 className="hero-alias">@{alias}</h1>
        {about ? <p className="hero-about">{about}</p> : null}
        {createdAt ? <span className="meta">Writing since {joined(createdAt)}</span> : null}
        <div className="counts">
          <Count k="letters" n={letters} label={letters === 1 ? "letter" : "letters"} />
          <Count k="followers" n={followers} label={followers === 1 ? "follower" : "followers"} />
          <Count k="following" n={following} label="following" />
        </div>
        {actions ? <div className="hero-actions">{actions}</div> : null}
      </div>
    </header>
  );
}

export function Profile({ alias }: { alias: string }) {
  const [p, setP] = useState<U.Profile | null>(null);
  const [gone, setGone] = useState(false);
  const [error, setError] = useState<string>();
  const [graph, setGraph] = useState<GraphData | null>(null);
  const [menu, setMenu] = useState(false);

  useEffect(() => {
    let stop = false;
    setP(null);
    setGone(false);
    U.profile(alias).then((x) => {
      if (stop) return;
      if (x.isMe) return navigate({ name: "me" }, true);
      setP(x);
    }).catch((e) => {
      if (stop) return;
      if (e instanceof ApiError && e.status === 404) setGone(true);
      else setError(errorMessage(e));
    });
    U.graphOf(alias).then((g) => !stop && setGraph("hidden" in g ? null : g)).catch(() => undefined);
    return () => { stop = true; };
  }, [alias]);

  useEffect(() => onFollowChange((a, following, followers) => {
    setP((cur) => (cur && cur.alias.toLowerCase() === a.toLowerCase() ? { ...cur, isFollowing: following, followers } : cur));
  }), []);

  const top = (
    <div className="topbar overlay-bar">
      <button className="icon-btn on-banner" onClick={back} aria-label="Back"><BackIcon /></button>
      <span className="tag on-banner-tag">Profile</span>
      {p ? <button className="icon-btn on-banner" onClick={() => setMenu(true)} aria-label={`More about @${p.alias}`}><More /></button>
        : <span className="spacer" />}
    </div>
  );

  if (gone) {
    return (
      <main className="screen">
        <div className="topbar"><button className="icon-btn" onClick={back} aria-label="Back"><BackIcon /></button></div>
        <Empty title="No one goes by that alias." body="They may have changed it or left Confess." />
      </main>
    );
  }
  if (!p) {
    return (
      <main className="screen">
        <div className="topbar"><button className="icon-btn" onClick={back} aria-label="Back"><BackIcon /></button></div>
        {error ? <Empty title="Couldn't open this profile." body={error} /> : <div className="list-loading"><span className="spinner" aria-label="Loading" /></div>}
      </main>
    );
  }

  return (
    <main className="screen profile-screen">
      <ProfileHero alias={p.alias} avatar={p.avatar} about={p.about} banner={p.banner} createdAt={p.createdAt}
                   letters={p.postCount} followers={p.followers} following={p.following} top={top}
                   actions={p.blockedByMe ? null : (
                     <FollowButton alias={p.alias} following={p.isFollowing}
                                   onChange={(f, n) => setP({ ...p, isFollowing: f, followers: n })} />
                   )} />
      {p.blockedByMe ? <BlockedNote alias={p.alias} onUnblocked={() => setP({ ...p, blockedByMe: false })} /> : (
        <>
          {graph && graph.total > 0 ? (
            <section className="plate graph-card" aria-label="Confession graph">
              <span className="tag">Confession graph</span>
              <ConfessionGraph data={graph} style={parseStyle(graph.style)} label={`@${p.alias}'s confession graph`} />
            </section>
          ) : null}
          <AuthorBooks alias={p.alias} />
          <PostList key={p.alias} title={`Letters (${count(p.postCount)})`}
                    fetch={(c) => U.postsBy(p.alias, c)}
                    empty={<Empty title="No letters yet." body={`@${p.alias} hasn't written anything that you can see.`} />}
                    onBlocked={() => back()} />
        </>
      )}
      {menu ? <ProfileMenu profile={p} onClose={() => setMenu(false)}
                           onBlocked={() => { setP({ ...p, blockedByMe: true, isFollowing: false }); }}
                           onUnblocked={() => setP({ ...p, blockedByMe: false })} /> : null}
    </main>
  );
}

function BlockedNote({ alias, onUnblocked }: { alias: string; onUnblocked: () => void }) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  return (
    <Empty title={`You blocked @${alias}.`} body="You don't see each other's confessions, responses or stories.">
      <Button variant="outline" busy={busy} onClick={async () => {
        setBusy(true);
        try { await U.unblock(alias); toast(`Unblocked @${alias}`); onUnblocked(); } catch (e) { toast(errorMessage(e)); setBusy(false); }
      }}>Unblock</Button>
    </Empty>
  );
}

function ProfileMenu({ profile, onClose, onBlocked, onUnblocked }:
  { profile: U.Profile; onClose: () => void; onBlocked: () => void; onUnblocked: () => void }) {
  const [step, setStep] = useState<"menu" | "report" | "block">("menu");
  const toast = useToast();
  const a = profile.alias;
  if (step === "report") return <ReportSheet target={{ kind: "user", alias: a }} onClose={onClose} onDone={onClose} />;
  if (step === "block") {
    return (
      <Confirm title={`Block @${a}?`} danger confirm="Block" onClose={onClose}
               body="You won't see each other's confessions, responses or stories, and any follow between you is removed. They won't be told."
               onConfirm={async () => { await P.blockUser(a); toast(`Blocked @${a}`); onClose(); onBlocked(); }} />
    );
  }
  return (
    <Sheet title="Options" onClose={onClose}>
      <div className="menu-list">
        <button className="menu-item" onClick={() => setStep("report")}><Flag /> Report @{a}</button>
        {profile.blockedByMe ? (
          <button className="menu-item" onClick={async () => {
            try { await U.unblock(a); toast(`Unblocked @${a}`); onUnblocked(); } catch (e) { toast(errorMessage(e)); }
            onClose();
          }}><Ban /> Unblock @{a}</button>
        ) : <button className="menu-item danger" onClick={() => setStep("block")}><Ban /> Block @{a}</button>}
        <button className="menu-item" onClick={onClose}><Close /> Cancel</button>
      </div>
    </Sheet>
  );
}

/** A paged list of confessions that stays in step with every other list (reactions, deletes, blocks). */
export function PostList({ title, fetch, empty, onBlocked }:
  { title?: string; fetch: (cursor: string | null) => Promise<{ posts: Post[]; nextCursor: string | null }>;
    empty: ReactNode; onBlocked?: (alias: string) => void }) {
  const list = usePaged<Post>(async (c) => {
    const r = await fetch(c);
    remember(r.posts);
    return { items: r.posts, next: r.nextCursor };
  }, (x) => x.postId);
  const { setItems } = list;
  useEffect(() => subscribe((e) => {
    setItems((cur) => apply(cur, e));
    if (e.type === "blocked") onBlocked?.(e.alias);
  }), [setItems, onBlocked]);
  const actions = cardActions((x, r) => openPost(x.postId, r));
  return (
    <section className="post-list" aria-label={title ?? "Letters"}>
      {title && list.items.length ? <span className="tag list-title">{title}</span> : null}
      <div className="cards">{list.items.map((x) => <PostCard key={x.postId} post={x} actions={actions} />)}</div>
      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()} empty={empty}
                  end={<span className="meta">That's every letter.</span>} />
    </section>
  );
}

/** Their published books, if any. */
export function AuthorBooks({ alias, title = "Books" }: { alias: string; title?: string }) {
  const [books, setBooks] = useState<Bk.Book[]>([]);
  useEffect(() => {
    let stop = false;
    Bk.list("updated", null, alias).then((r) => { if (!stop) setBooks(r.books); }).catch(() => undefined);
    return () => { stop = true; };
  }, [alias]);
  if (!books.length) return null;
  return (
    <section className="author-books" aria-label={title}>
      <span className="tag list-title">{title}</span>
      <div className="continue-row scroll-x">{books.map((b) => <div className="author-book" key={b.bookId}><BookCard book={b} /></div>)}</div>
    </section>
  );
}
