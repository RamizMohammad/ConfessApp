import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { refreshUnread } from "../lib/activity.ts";
import type { NavTab } from "../lib/appearance.ts";
import { back, isTab, navigate, parse, switchTab, useStack, type Route, type Tab } from "../lib/router.ts";
import { ScreenShown } from "../lib/screen.ts";
import { lockScroll } from "../lib/shell.ts";
import { S } from "../lib/strings.ts";
import { NavBar } from "../components/NavBar.tsx";
import { BurstReader } from "../components/bursts.tsx";
import { Header } from "../components/ui.tsx";
import { Icon } from "../ui/Icon.tsx";
import { Activity } from "./Activity.tsx";
import { AvatarStudioScreen } from "./AvatarStudio.tsx";
import { BookPage, BooksShelf, BookSetup, ChapterEditor, Desk, MyBooks } from "./Books.tsx";
import { Compose } from "./Compose.tsx";
import { Drafts } from "./Drafts.tsx";
import { Feed, scrollFeedToTop } from "./Feed.tsx";
import { MePage } from "./Me.tsx";
import { MyLetters, People, Received, Search } from "./People.tsx";
import { PostDetail } from "./PostDetail.tsx";
import { Profile } from "./Profile.tsx";
import { Reader } from "./Reader.tsx";
import { SettingsScreen } from "./Settings.tsx";
import { StoryCompose, StoryViewer } from "./Stories.tsx";
import { Tour, tourDue } from "./Tour.tsx";

/** iPad+ (plan R2): feed list on the left, the open confession on the right, from this width. */
const SPLIT = "(min-width: 900px)";
/** Compose opens as a centred sheet from here (iPad, wide Split View). */
const SHEET = "(min-width: 600px)";

function useMedia(q: string): boolean {
  const [on, setOn] = useState(() => typeof matchMedia !== "undefined" && matchMedia(q).matches);
  useEffect(() => {
    const m = matchMedia(q);
    const f = () => setOn(m.matches);
    m.addEventListener("change", f);
    f();
    return () => m.removeEventListener("change", f);
  }, [q]);
  return on;
}

/** One pushed screen (an Android activity). */
function screenFor(r: Route): ReactNode {
  switch (r.name) {
    case "post": return <PostDetail key={r.id} id={r.id} />;
    case "write": return <Compose key={r.draftId ?? "new"} draftId={r.draftId} />;
    case "drafts": return <Drafts />;
    case "activity": return <main className="screen"><Header title="Activity" onBack={back} /><Activity /></main>;
    case "profile": return <Profile key={r.alias.toLowerCase()} alias={r.alias} />;
    case "people": return <People key={r.kind} kind={r.kind} />;
    case "received": return <Received key={r.kind} kind={r.kind} />;
    case "letters": return <MyLetters />;
    case "search": return <Search />;
    case "settings": return <SettingsScreen key={r.page ?? "home"} page={r.page} />;
    case "avatar": return <AvatarStudioScreen />;
    case "book": return <BookPage key={r.id} id={r.id} />;
    case "read": return <Reader key={`${r.id}:${r.no}`} id={r.id} no={r.no} />;
    case "myBooks": return <MyBooks />;
    case "bookSetup": return <BookSetup key={r.id ?? "new"} id={r.id} />;
    case "desk": return <Desk key={r.id} id={r.id} />;
    case "chapter": return <ChapterEditor key={`${r.id}:${r.no ?? "new"}`} id={r.id} no={r.no} />;
    case "stories": return <StoryViewer key={r.alias.toLowerCase()} alias={r.alias} />;
    case "storyNew": return <StoryCompose />;
    case "burst": return <BurstReader key={r.id} id={r.id} scope={r.scope} />;
    default: return null;
  }
}

/**
 * Inside Confess (HomeActivity). Feed · Books · Me are the bar's tabs: each stays mounted once
 * opened and keeps its place. Every other screen is pushed on a back stack and stays mounted
 * underneath while something is on top of it, so going back is instant and keeps the scroll.
 */
export function Main() {
  // The feature tour: once after sign-in (or when a version adds pages), and from Settings.
  const [tour, setTour] = useState<"full" | "new" | null>(() => tourDue());
  useEffect(() => {
    const open = () => setTour("full");
    window.addEventListener("confess:tour", open);
    return () => window.removeEventListener("confess:tour", open);
  }, []);
  useEffect(() => (tour ? lockScroll() : undefined), [tour]);

  const { entries, top } = useStack();
  const topEntry = entries.find((e) => e.index === top)?.entry ?? entries[entries.length - 1]!.entry;
  const route = topEntry.route;
  const below = entries.find((e) => e.index === top - 1)?.entry.route;
  const wide = useMedia(SHEET);
  const splitWide = useMedia(SPLIT);
  const tab: Tab | null = isTab(route) ? route.name : null;

  // iPad+: the feed stays on the left while a confession is open on the right.
  const split = splitWide && (tab === "feed" || (route.name === "post" && below?.name === "feed"));
  // Compose as a sheet over the screen it came from.
  const composeSheet = wide && route.name === "write";
  const showBelow = composeSheet || split;
  const visibleTab: Tab | null = tab ?? (showBelow && below && isTab(below) ? below.name : null);

  useLayoutEffect(() => {
    document.documentElement.classList.toggle("split", split);
    return () => document.documentElement.classList.remove("split");
  }, [split]);

  // Tabs mount the first time they're shown, then stay.
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set(visibleTab ? [visibleTab] : []));
  useEffect(() => {
    if (visibleTab && !visited.has(visibleTab)) setVisited((v) => new Set(v).add(visibleTab));
  }, [visibleTab, visited]);

  // Each screen keeps its own scroll position (the window scrolls; one screen shows at a time).
  const scrolls = useRef(new Map<string, number>());
  const topId = tab ? `tab:${tab}` : `s:${topEntry.key}`;
  const shownId = useRef(topId);
  useEffect(() => {
    const save = () => { scrolls.current.set(shownId.current, window.scrollY); };
    window.addEventListener("scroll", save, { passive: true });
    return () => window.removeEventListener("scroll", save);
  }, []);
  useLayoutEffect(() => {
    if (shownId.current === topId) return;
    shownId.current = topId;
    if (composeSheet) return;
    window.scrollTo(0, scrolls.current.get(topId) ?? 0);
  }, [topId, composeSheet]);
  // Forget screens that left the stack.
  useEffect(() => {
    const live = new Set(entries.map((e) => `s:${e.entry.key}`));
    for (const k of scrolls.current.keys()) if (k.startsWith("s:") && !live.has(k)) scrolls.current.delete(k);
  }, [entries]);

  // Tapped a notification while Confess was open: the service worker asks us to go there.
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const onMsg = (e: MessageEvent) => {
      const d = e.data as { type?: string; url?: string } | null;
      if (d?.type === "confess:open" && typeof d.url === "string" && d.url.startsWith("/") && !d.url.startsWith("//")) {
        navigate(parse(d.url));
        void refreshUnread();
      }
    };
    navigator.serviceWorker.addEventListener("message", onMsg);
    return () => navigator.serviceWorker.removeEventListener("message", onMsg);
  }, []);

  // HomeActivity's back: from Books / Me back to the feed first.
  useEffect(() => {
    if (tab !== "books" && tab !== "me") return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !document.querySelector("[aria-modal='true']")) switchTab("feed"); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [tab]);

  const onTab = (t: NavTab) => {
    if (t === "search") { navigate({ name: "search" }); return; }
    if (t === tab) {
      if (t === "feed") scrollFeedToTop();
      else window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    if (split && route.name === "post" && t === "feed") { back(); return; }
    switchTab(t);
  };

  const navVisible = !!tab || split;

  return (
    <>
      {tour ? <Tour mode={tour} onDone={() => setTour(null)} /> : null}

      {(["feed", "books", "me"] as Tab[]).filter((t) => visited.has(t) || t === visibleTab).map((t) => (
        <div key={t} className={`tab-layer tab-${t}`} hidden={t !== visibleTab}>
          <ScreenShown.Provider value={t === visibleTab && (!!tab || split)}>
            {t === "feed" ? <Feed active={t === visibleTab} split={split} /> : null}
            {t === "books" ? <main className="screen main"><BooksShelf /></main> : null}
            {t === "me" ? <MePage /> : null}
          </ScreenShown.Provider>
        </div>
      ))}

      {entries.filter((e) => !isTab(e.entry.route)).map(({ entry, index }) => {
        const shown = index === top || (showBelow && index === top - 1 && !isTab(entry.route));
        return (
          <div key={entry.key} hidden={!shown}
               className={`stack-layer${index === top && composeSheet ? " sheet-layer" : ""}${index === top && split ? " pane-layer" : ""}`}>
            <ScreenShown.Provider value={index === top}>{screenFor(entry.route)}</ScreenShown.Provider>
          </div>
        );
      })}

      {split && tab === "feed" ? (
        <div className="split-empty" aria-hidden>
          <span className="split-empty-tile"><Icon name="mail_open" size={24} /></span>
          <span className="t-meta-small">{S("detail_title")}</span>
        </div>
      ) : null}

      {navVisible ? (
        <NavBar selected={(tab ?? (split ? "feed" : null)) as NavTab | null} onTab={onTab}
                onCompose={() => navigate({ name: "write" })} />
      ) : null}
    </>
  );
}
