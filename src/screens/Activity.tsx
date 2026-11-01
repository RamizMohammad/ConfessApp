import { useEffect, useState } from "react";
import * as A from "../lib/activity.ts";
import { ago, moderationReason } from "../lib/format.ts";
import { navigate } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import { usePaged } from "../lib/usePaged.ts";
import * as WP from "../lib/webpush.ts";
import { Aura } from "../components/Aura.tsx";
import { Empty, ListStatus } from "../components/list.tsx";
import { useToast } from "../components/toast.tsx";
import { Button } from "../components/ui.tsx";

type Filter = "all" | "like" | "comment" | "follow";
const FILTERS: { key: Filter; label: string }[] = [
  { key: "all", label: "All" }, { key: "like", label: "Felt" }, { key: "comment", label: "Responses" }, { key: "follow", label: "Follows" },
];

const REVIEW = ["post_removed", "post_sensitive"];

function line(n: A.Notification): string {
  switch (n.type) {
    case "like": return " felt your confession";
    case "comment": return " responded to your confession";
    case "follow": return " started following you";
    case "post": return " wrote a new letter";
    case "post_removed": return "We removed your confession";
    case "post_sensitive": return "Your confession is out — blurred until tapped";
    default: return " did something new";
  }
}

/** Whispers & Echoes — who felt, answered or followed, and new letters from people I follow. */
export function Activity() {
  const [scope, setScope] = useState<"all" | "following">("all");
  const [filter, setFilter] = useState<Filter>("all");
  const [marking, setMarking] = useState(false);
  const toast = useToast();
  const list = usePaged<A.Notification>(async (c) => {
    const r = await A.list(scope, c);
    return { items: r.notifications, next: r.nextCursor };
  }, (n) => n.id, [scope]);

  const shown = list.items.filter((n) => filter === "all" || n.type === filter);
  const anyUnread = list.items.some((n) => !n.read);

  async function markAll() {
    setMarking(true);
    try {
      await A.markRead();
      list.setItems((cur) => cur.map((n) => ({ ...n, read: true })));
      A.readLocally("all");
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setMarking(false);
    }
  }

  function open(n: A.Notification) {
    if (!n.read) {
      list.setItems((cur) => cur.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      A.readLocally(1);
      void A.markRead([n.id]).catch(() => undefined);
    }
    if (n.type === "post_removed") navigate({ name: "settings", page: "guidelines" });
    else if (n.type === "follow" || !n.postId) navigate({ name: "profile", alias: n.actor.alias });
    else navigate({ name: "post", id: n.postId });
  }

  const groups: { title: string; items: A.Notification[] }[] = [];
  for (const n of shown) {
    const t = A.section(n.createdAt);
    const g = groups[groups.length - 1];
    if (g && g.title === t) g.items.push(n);
    else groups.push({ title: t, items: [n] });
  }

  return (
    <section className="activity" aria-label="Activity">
      <div className="activity-head">
        <h1 className="headline">Whispers &amp; Echoes</h1>
        {anyUnread ? <Button variant="ghost" busy={marking} onClick={markAll}>Mark all read</Button> : null}
      </div>
      <PushOffer />
      <div className="feed-controls">
        <div className="segmented" role="tablist">
          <button role="tab" aria-selected={scope === "all"} onClick={() => setScope("all")}>Everyone</button>
          <button role="tab" aria-selected={scope === "following"} onClick={() => setScope("following")}>Following</button>
        </div>
        <div className="chips scroll-x">
          {FILTERS.map((f) => (
            <button key={f.key} className={`chip${filter === f.key ? " on" : ""}`} aria-pressed={filter === f.key}
                    onClick={() => setFilter(f.key)}>{f.label}</button>
          ))}
        </div>
      </div>

      {groups.map((g) => {
        const fresh = g.items.filter((n) => !n.read).length;
        return (
          <div key={g.title} className="notif-group">
            <div className="row spread notif-section">
              <span className="tag">{g.title}</span>
              {fresh ? <span className="meta amber">{fresh} new</span> : null}
            </div>
            {g.items.map((n) => (
              <button key={n.id} className={`notif${n.read ? "" : " unread"}`} onClick={() => open(n)}
                      aria-label={`${REVIEW.includes(n.type) ? "" : `@${n.actor.alias}`}${line(n)}, ${ago(n.createdAt)}${n.read ? "" : ", new"}`}>
                <Aura value={n.actor.avatar} size={40} />
                <span className="grow stack-xs">
                  {REVIEW.includes(n.type) ? <span className="notif-line"><b>{line(n)}</b></span>
                    : <span className="notif-line"><b>@{n.actor.alias}</b>{line(n)}</span>}
                  {n.type === "post_removed"
                    ? <span className="small">{moderationReason(`(${(n.reason ?? "").replace(/_/g, " ")})`)}</span> : null}
                  {n.type === "post_sensitive"
                    ? <span className="small">It may be hard to read, so others tap to see it. Nothing else changes.</span> : null}
                  {n.type === "comment" && n.commentExcerpt ? <span className="notif-quote clamp-1">“{n.commentExcerpt}”</span> : null}
                  {n.postExcerpt && n.type !== "follow"
                    ? <span className="small clamp-1">{n.type === "comment" ? `On “${n.postExcerpt.trim()}”` : n.postExcerpt}</span> : null}
                  <span className="meta">{ago(n.createdAt)}</span>
                </span>
                {n.read ? null : <span className="dot" aria-hidden />}
              </button>
            ))}
          </div>
        );
      })}

      {list.state === "idle" && list.items.length > 0 && !shown.length
        ? <Empty title="Nothing of this kind yet." /> : null}

      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()}
                  empty={scope === "following"
                    ? <Empty title="Quiet from the people you follow" body="Felt, responses and follows from people you follow will show here." />
                    : <Empty title="Quiet nights are good too." body="When someone feels or answers your confessions, it shows up here." />} />
    </section>
  );
}

/** Once, on the installed app: ask from a tap (iOS won't let us ask any other way). */
function PushOffer() {
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  useEffect(() => { void WP.shouldOfferPush().then(setShow); }, []);
  if (!show) return null;
  return (
    <div className="plate push-offer">
      <span className="tag">Notifications</span>
      <h2 className="title">Know when your words land</h2>
      <p className="small">A quiet nudge when someone feels your confession, responds to it or follows you. You choose which ones in Settings. Private by default — the lock screen won't show what it's about.</p>
      <div className="row">
        <Button busy={busy} onClick={async () => {
          setBusy(true);
          try {
            const st = await WP.enablePush();
            toast(st === "on" ? "Notifications are on. You'll know when your words land." : "Okay — you can turn them on in Settings any time.");
          } catch (e) {
            toast(errorMessage(e));
          }
          setShow(false);
        }}>Allow notifications</Button>
        <Button variant="ghost" onClick={() => { WP.dismissPushOffer(); setShow(false); }}>Not now</Button>
      </div>
    </div>
  );
}
