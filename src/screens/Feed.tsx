/**
 * fragment_feed + FeedFragment: Letty mark and the bell (unread dot), the story tray (scrolls
 * away), the pinned Everyone / Following toggle with its hairline, then the cards. Pull to
 * refresh, three skeleton cards while loading, the footer (more · retry · caught up), the
 * empty and error states. Latest only — the sort and mood chips aren't in the Android UI.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useUnread } from "../lib/activity.ts";
import { burstAsked, burstMode, markBurstAsked, onBurstMode, type BurstMode } from "../lib/bursts.ts";
import * as P from "../lib/posts.ts";
import { apply, cardActions, remember, subscribe } from "../lib/postStore.ts";
import { navigate, openPost } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { S } from "../lib/strings.ts";
import type { Post } from "../lib/types.ts";
import { BurstCard, BurstChooser } from "../components/bursts.tsx";
import { PostCard } from "../components/post.tsx";
import { StoryTray } from "../components/stories.tsx";
import { Art } from "../ui/Art.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, haptic, IconBtn, Spinner } from "../ui/kit.tsx";
import { usePullToRefresh } from "../ui/pull.tsx";
import { tourOpen } from "./Tour.tsx";

type Load = "idle" | "loading" | "more" | "error" | "moreError";
type Scope = P.Scope;

const SCOPE_KEY = "confess.feed_scope";
const savedScope = (): Scope => { try { return localStorage.getItem(SCOPE_KEY) === "following" ? "following" : "all"; } catch { return "all"; } };

let toTop: (() => void) | null = null;
/** Re-selecting the Feed tab / tapping the mark: back to the newest, stories too. */
export function scrollFeedToTop(): void {
  toTop?.();
}

/** The state view (stateIcon / stateTitle / stateBody / stateAction). */
function FeedState({ icon, title, body, action, actionIcon, onAction }:
  { icon: IconName; title: string; body: string; action: string; actionIcon: IconName; onAction: () => void }) {
  return (
    <div className="feed-state">
      <span className="icon-tile-56"><Icon name={icon} size={24} /></span>
      <h2 className="t-headline-small feed-state-title">{title}</h2>
      <p className="t-body-medium feed-state-body">{body}</p>
      <Btn kind="outlined" icon={actionIcon} className="feed-state-action" onClick={onAction}>{action}</Btn>
    </div>
  );
}

/** item_feed_skeleton. */
function FeedSkeleton() {
  return (
    <div className="fsk" aria-hidden>
      <div className="fsk-head"><i className="fsk-circle" /><span><i className="fsk-b w110 h10" /><i className="fsk-b w56 h8 mt8" /></span></div>
      <i className="fsk-b h14 mt16" /><i className="fsk-b h14 mt10" /><i className="fsk-b w180 h14 mt10" />
      <span className="fsk-foot"><i className="fsk-b w44 h10" /><i className="fsk-b w44 h10" /></span>
    </div>
  );
}

export function Feed({ active, split = false }: { active: boolean; split?: boolean }) {
  const { me } = useSession();
  const unread = useUnread();
  const [scope, setScope] = useState<Scope>(savedScope);
  const [bursts, setBursts] = useState<BurstMode>(burstMode);
  const [chooser, setChooser] = useState(false);
  const [posts, setPosts] = useState<Post[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<Load>("loading");
  const [error, setError] = useState<{ text: string; offline: boolean }>();
  const [entered, setEntered] = useState(false);
  const seq = useRef(0);
  const root = useRef<HTMLElement>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  const list = useRef<HTMLDivElement>(null);

  const scroller = useCallback(() => (split ? (root.current?.closest(".tab-layer") as HTMLElement | null) : null), [split]);

  const load = useCallback(async (more: boolean, c: string | null) => {
    const mine = ++seq.current;
    setState(more ? "more" : "loading");
    try {
      const r = await P.feed({ sort: "latest", scope, cursor: more ? c : null, bursts: bursts !== "separate" });
      if (mine !== seq.current) return;
      remember(r.posts);
      setPosts((cur) => (more ? [...cur, ...r.posts.filter((p) => !cur.some((x) => x.postId === p.postId))] : r.posts));
      setCursor(r.nextCursor);
      setState("idle");
    } catch (e) {
      if (mine !== seq.current) return;
      setError({ text: errorMessage(e), offline: typeof navigator !== "undefined" && navigator.onLine === false });
      setState(more ? "moreError" : "error");
    }
  }, [scope, bursts]);

  useEffect(() => onBurstMode(setBursts), []);
  const anyBurst = posts.some((p) => (p.burst?.count ?? 1) > 1);
  // Other prompts wait while the feature tour is up (Tour.maybeShow on Android).
  const [tourUp, setTourUp] = useState(tourOpen);
  useEffect(() => {
    const on = () => setTourUp(tourOpen());
    window.addEventListener("confess:tour-state", on);
    on();
    return () => window.removeEventListener("confess:tour-state", on);
  }, []);
  useEffect(() => {
    if (active && anyBurst && !tourUp && !burstAsked() && bursts !== "separate") setChooser(true);
  }, [active, anyBurst, tourUp, bursts]);

  useEffect(() => { void load(false, null); }, [load]);
  useEffect(() => subscribe((e) => setPosts((cur) => apply(cur, e))), []);

  // The list enters once (Motion.enterList), the first time it has cards.
  useLayoutEffect(() => {
    if (!entered && state === "idle" && posts.length) {
      setEntered(true);
      list.current?.classList.add("m-layout-enter");
      window.setTimeout(() => list.current?.classList.remove("m-layout-enter"), 900);
    }
  }, [state, posts.length, entered]);

  const scrollTop = useCallback((smooth = true) => {
    const s = scroller();
    (s ?? window).scrollTo({ top: 0, behavior: smooth ? "smooth" : "auto" });
  }, [scroller]);
  useEffect(() => {
    if (!active) return;
    toTop = () => scrollTop(true);
    return () => { if (toTop) toTop = null; };
  }, [active, scrollTop]);

  // Released from the composer: Everyone, from the top (the new one is first).
  useEffect(() => {
    const onReleased = () => {
      if (scope !== "all") { try { localStorage.setItem(SCOPE_KEY, "all"); } catch { /* */ } setScope("all"); }
      window.setTimeout(() => scrollTop(false), 0);
    };
    window.addEventListener("confess:released", onReleased);
    return () => window.removeEventListener("confess:released", onReleased);
  }, [scope, scrollTop]);

  // Infinite scroll (PREFETCH: a few cards before the end).
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !active) return;
    const io = new IntersectionObserver((entries) => {
      if (entries.some((x) => x.isIntersecting) && cursor && state === "idle") void load(true, cursor);
    }, { rootMargin: "900px" });
    io.observe(el);
    return () => io.disconnect();
  }, [cursor, state, load, active]);

  const { indicator } = usePullToRefresh(root, () => load(false, null), { enabled: active && !split, scroller });

  function selectScope(next: Scope) {
    if (next === scope) { scrollTop(true); return; }
    haptic("tick");
    try { localStorage.setItem(SCOPE_KEY, next); } catch { /* private mode */ }
    setScope(next);
    setPosts([]);
    setEntered(false);
    scrollTop(false);
  }

  const actions = cardActions((p, r) => openPost(p.postId, r));
  const empty = state === "idle" && !posts.length;
  const followsNobody = !me || me.following === 0;

  return (
    <section ref={root} className="feed-a" hidden={!active} aria-label={S("tab_feed")}>
      <header className="feed-top">
        <button type="button" className="feed-mark" aria-label={S("app_name")} onClick={() => scrollTop(true)}>
          <Art name="logo_letty" height={30} />
        </button>
        <span className="feed-bell">
          <IconBtn icon="bell" label={unread ? `${S("cd_notifications")}, ${unread}` : S("cd_notifications")}
                   onClick={() => navigate({ name: "activity" })} />
          {unread ? <i className="unread-dot" aria-hidden /> : null}
        </span>
      </header>

      <StoryTray active={active} />

      <div className="feed-scope">
        <div className="scope-wide" role="radiogroup" aria-label="Show">
          {(["all", "following"] as Scope[]).map((k) => (
            <button key={k} type="button" role="radio" aria-checked={scope === k}
                    className={`scope-wide-opt t-meta-small${scope === k ? " on" : ""}`} onClick={() => selectScope(k)}>
              {S(k === "all" ? "scope_everyone" : "scope_following")}
            </button>
          ))}
        </div>
        <span className="feed-scope-rule" />
      </div>

      {indicator}

      <div className="feed-list">
        {state === "loading" && !posts.length ? <div className="feed-skeletons"><FeedSkeleton /><FeedSkeleton /><FeedSkeleton /></div> : null}

        {state === "error" && !posts.length ? (
          <FeedState icon={error?.offline ? "wifi_off" : "alert"} title={S("feed_error_title")} body={error?.text ?? ""}
                     action={S("action_retry")} actionIcon="refresh" onAction={() => void load(false, null)} />
        ) : null}

        {empty && scope === "following" ? (
          <FeedState icon="user" title={S(followsNobody ? "feed_following_none_title" : "feed_following_empty_title")}
                     body={S(followsNobody ? "feed_following_none_body" : "feed_following_empty_body")}
                     action={S("feed_following_find")} actionIcon="search" onAction={() => navigate({ name: "search" })} />
        ) : null}
        {empty && scope === "all" ? (
          <FeedState icon="mail_open" title={S("feed_empty_title")} body={S("feed_empty_body")}
                     action={S("feed_empty_action")} actionIcon="compose" onAction={() => navigate({ name: "write" })} />
        ) : null}

        <div ref={list} className="feed-cards">
          {posts.map((p) => (p.burst && p.burst.count > 1 && bursts !== "separate"
            ? <BurstCard key={`burst:${p.burst.id}`} head={p} mode={bursts} ctx={{ scope, mood: null }} />
            : <PostCard key={p.postId} post={p} actions={actions} />))}
        </div>

        {posts.length || state === "more" || state === "moreError" ? (
          <div ref={sentinel} className="feed-footer">
            {state === "more" || (state === "idle" && cursor) ? <Spinner size={24} /> : null}
            {state === "moreError" ? (
              <div className="feed-footer-error">
                <span className="t-body-medium">{S("feed_more_failed")}</span>
                <Btn kind="link" icon="refresh" onClick={() => void load(true, cursor)}>{S("action_retry")}</Btn>
              </div>
            ) : null}
            {state === "idle" && posts.length > 0 && !cursor ? (
              <div className="feed-footer-end">
                <i className="feed-footer-rule" />
                <span className="feed-caught-up">{S("feed_caught_up")}</span>
                <span className="t-meta feed-caught-sub">{S("feed_caught_up_sub")}</span>
              </div>
            ) : null}
          </div>
        ) : <div ref={sentinel} />}
      </div>
      <BurstChooser open={chooser} onClose={() => { markBurstAsked(); setChooser(false); }} />
    </section>
  );
}
