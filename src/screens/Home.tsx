import { useEffect } from "react";
import { refreshUnread, useUnread } from "../lib/activity.ts";
import { navigate, parse, switchTab, useRoute, type Tab } from "../lib/router.ts";
import { useSession } from "../lib/session.tsx";
import { Aura } from "../components/Aura.tsx";
import { Bell, BookIcon, Feather, Home as HomeIcon, Search as SearchIcon } from "../components/icons.tsx";
import { Lockup } from "../components/ui.tsx";
import { Activity } from "./Activity.tsx";
import { AvatarStudioScreen } from "./AvatarStudio.tsx";
import { Compose } from "./Compose.tsx";
import { Drafts } from "./Drafts.tsx";
import { Feed } from "./Feed.tsx";
import { MePage } from "./Me.tsx";
import { MyLetters, People, Received, Search } from "./People.tsx";
import { PostDetail } from "./PostDetail.tsx";
import { Profile } from "./Profile.tsx";
import { SettingsScreen } from "./Settings.tsx";
import { BookPage, BooksShelf, BookSetup, ChapterEditor, Desk, MyBooks } from "./Books.tsx";
import { Reader } from "./Reader.tsx";
import { StoryCompose, StoryViewer } from "./Stories.tsx";
import { BurstList } from "../components/bursts.tsx";
import { back } from "../lib/router.ts";
import { Header } from "../components/ui.tsx";

/**
 * Inside Confess. The feed stays mounted (keeps its place) while other screens open on top;
 * Feed · Activity · Me are tabs with the bar; everything else is a screen with a back button.
 */
export function Main() {
  const { me } = useSession();
  const route = useRoute();
  const unread = useUnread();
  const onFeed = route.name === "feed";
  const tab: Tab | null = route.name === "feed" || route.name === "books" || route.name === "activity" || route.name === "me" ? route.name : null;

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

  // A new screen starts at the top (the feed restores its own place).
  useEffect(() => {
    if (!onFeed) window.scrollTo(0, 0);
  }, [route, onFeed]);

  return (
    <>
      <main className="screen main" hidden={!onFeed}>
        <div className="topbar">
          <button className="icon-btn" aria-label="Find people" onClick={() => navigate({ name: "search" })}><SearchIcon /></button>
          <Lockup />
          <button className="icon-btn" aria-label="Me" onClick={() => switchTab("me")}>
            <Aura value={me?.avatar} size={32} />
          </button>
        </div>
        <Feed active={onFeed} />
      </main>
      {route.name === "activity" ? <main className="screen main"><Activity /></main> : null}
      {route.name === "me" ? <MePage /> : null}
      {route.name === "post" ? <PostDetail key={route.id} id={route.id} /> : null}
      {route.name === "write" ? <Compose key={route.draftId ?? "new"} draftId={route.draftId} /> : null}
      {route.name === "drafts" ? <Drafts /> : null}
      {route.name === "profile" ? <Profile key={route.alias.toLowerCase()} alias={route.alias} /> : null}
      {route.name === "people" ? <People key={route.kind} kind={route.kind} /> : null}
      {route.name === "received" ? <Received key={route.kind} kind={route.kind} /> : null}
      {route.name === "letters" ? <MyLetters /> : null}
      {route.name === "search" ? <Search /> : null}
      {route.name === "settings" ? <SettingsScreen key={route.page ?? "home"} page={route.page} /> : null}
      {route.name === "avatar" ? <AvatarStudioScreen /> : null}
      {route.name === "books" ? <main className="screen main"><BooksShelf /></main> : null}
      {route.name === "book" ? <BookPage key={route.id} id={route.id} /> : null}
      {route.name === "read" ? <Reader key={`${route.id}:${route.no}`} id={route.id} no={route.no} /> : null}
      {route.name === "myBooks" ? <MyBooks /> : null}
      {route.name === "bookSetup" ? <BookSetup key={route.id ?? "new"} id={route.id} /> : null}
      {route.name === "desk" ? <Desk key={route.id} id={route.id} /> : null}
      {route.name === "chapter" ? <ChapterEditor key={`${route.id}:${route.no ?? "new"}`} id={route.id} no={route.no} /> : null}
      {route.name === "stories" ? <StoryViewer key={route.alias.toLowerCase()} alias={route.alias} /> : null}
      {route.name === "storyNew" ? <StoryCompose /> : null}
      {route.name === "burst" ? <main className="screen"><Header title="Burst" onBack={back} /><BurstList id={route.id} scope={route.scope} /></main> : null}
      {tab ? (
        <nav className="tabbar" aria-label="Main">
          <button className={`tab tab-icon${tab === "feed" ? " on" : ""}`} aria-current={tab === "feed" ? "page" : undefined} aria-label="Feed"
                  onClick={() => (tab === "feed" ? window.scrollTo({ top: 0, behavior: "smooth" }) : switchTab("feed"))}><HomeIcon /></button>
          <button className={`tab tab-icon${tab === "books" ? " on" : ""}`} aria-current={tab === "books" ? "page" : undefined}
                  aria-label="Books" onClick={() => switchTab("books")}><BookIcon /></button>
          <button className="write-fab" aria-label="Write a confession" onClick={() => navigate({ name: "write" })}><Feather /></button>
          <button className={`tab tab-icon${tab === "activity" ? " on" : ""}`} aria-current={tab === "activity" ? "page" : undefined}
                  aria-label={unread ? `Activity, ${unread} new` : "Activity"} onClick={() => switchTab("activity")}>
            <Bell />{unread ? <span className="badge" aria-hidden>{unread > 99 ? "99+" : unread}</span> : null}
          </button>
          <button className={`tab tab-icon tab-me${tab === "me" ? " on" : ""}`} aria-current={tab === "me" ? "page" : undefined} aria-label="Me"
                  onClick={() => switchTab("me")}><Aura value={me?.avatar} size={26} /></button>
        </nav>
      ) : null}
    </>
  );
}
