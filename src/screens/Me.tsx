import { useEffect, useState } from "react";
import { useOnResume } from "../lib/screen.ts";
import { count } from "../lib/format.ts";
import { DEFAULT_STYLE, PALETTES, parseStyle, SHAPES, styleSpec, type GraphData, type GraphStyle } from "../lib/graph.ts";
import { listDrafts } from "../lib/drafts.ts";
import { navigate } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import * as U from "../lib/users.ts";
import { ConfessionGraph, ShapeSwatch } from "../components/art.tsx";
import { BookIcon, Flame, Heart, Letters, Message, Pencil, Settings as Gear, Users } from "../components/icons.tsx";
import { onFollowChange } from "../components/people.tsx";
import { Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, NavRow, Notice, Toggle } from "../components/ui.tsx";
import { ProfileHero } from "./Profile.tsx";

/** The Me tab: my profile on its banner, my graph, and the way into everything that's mine. */
export function MePage() {
  const { me, refreshMe, patchMe } = useSession();
  const [graph, setGraph] = useState<GraphData | null>(null);
  const [graphError, setGraphError] = useState(false);
  const [styleSheet, setStyleSheet] = useState(false);
  const [drafts, setDrafts] = useState<number | null>(null);

  useEffect(() => {
    void refreshMe();
    U.myGraph().then(setGraph).catch(() => setGraphError(true));
    listDrafts().then((d) => setDrafts(d.length)).catch(() => setDrafts(null));
  }, [refreshMe]);

  useOnResume(() => {
    void refreshMe();
    listDrafts().then((d) => setDrafts(d.length)).catch(() => setDrafts(null));
  });

  // Following someone elsewhere moves my "following" count here too.
  useEffect(() => onFollowChange(() => void refreshMe()), [refreshMe]);

  if (!me) return null;
  const style = parseStyle(me.graphStyle ?? graph?.style);
  const streak = me.streak ?? graph?.streak;

  return (
    <main className="screen me-screen" aria-label="Me">
      <ProfileHero alias={me.alias} avatar={me.avatar} about={me.about} banner={me.banner} createdAt={me.createdAt}
                   letters={me.postCount} followers={me.followers} following={me.following}
                   top={(
                     <div className="topbar overlay-bar">
                       <span className="spacer" />
                       <span className="tag on-banner-tag">Your profile</span>
                       <button className="icon-btn on-banner" aria-label="Settings" onClick={() => navigate({ name: "settings" })}><Gear /></button>
                     </div>
                   )}
                   onCount={(k) => navigate(k === "letters" ? { name: "letters" } : { name: "people", kind: k })}
                   actions={(
                     <Button variant="outline" className="btn-small" onClick={() => navigate({ name: "settings", page: "profile" })}>
                       <Pencil width={16} height={16} /> Edit profile
                     </Button>
                   )} />

      <section className="plate graph-card" aria-label="Confession graph">
        <div className="row spread">
          <span className="tag">Confession graph</span>
          <button className="link small-link" onClick={() => setStyleSheet(true)}>Customise</button>
        </div>
        {streak ? (
          <div className="streak">
            <Flame className={streak.days ? "flame on" : "flame"} />
            <span>
              <b>{streak.days ? `${streak.days}-day streak` : "Start a streak"}</b>
              <span className="meta"> · best {streak.best} day{streak.best === 1 ? "" : "s"}{streak.days && !streak.today ? " · open Confess today to keep it" : ""}</span>
            </span>
          </div>
        ) : null}
        {graph ? <ConfessionGraph data={graph} style={style} label="Your confession graph" />
          : graphError ? <p className="small">Couldn't load your graph.</p> : <div className="graph-placeholder" />}
      </section>

      <section className="settings-group" aria-label="Yours">
        <NavRow icon={<Users />} label="Following & followers" value={`${count(me.following)} · ${count(me.followers)}`}
                onClick={() => navigate({ name: "people", kind: "following" })} />
        <NavRow icon={<Letters />} label="Letters" sub="Everything you've written" value={count(me.postCount)}
                onClick={() => navigate({ name: "letters" })} />
        <NavRow icon={<BookIcon />} label="Your books" sub="Write chapters, release them when you choose"
                onClick={() => navigate({ name: "myBooks" })} />
        <NavRow icon={<Pencil />} label="Drafts" sub="Unsent, only on this device" value={drafts ? String(drafts) : undefined}
                onClick={() => navigate({ name: "drafts" })} />
        <NavRow icon={<Heart />} label="Felt" value={`${count(me.likesReceived ?? 0)} received`}
                onClick={() => navigate({ name: "received", kind: "felt" })} />
        <NavRow icon={<Message />} label="Responses" value={`${count(me.commentsReceived ?? 0)} received`}
                onClick={() => navigate({ name: "received", kind: "responses" })} />
        <NavRow icon={<Gear />} label="Settings" sub="Lock, notifications, account" onClick={() => navigate({ name: "settings" })} />
      </section>

      {styleSheet ? (
        <GraphStyleSheet initial={style} isPublic={me.graphPublic ?? graph?.public ?? false} graph={graph}
                         onClose={() => setStyleSheet(false)}
                         onSaved={(s, pub) => { patchMe({ graphStyle: s, graphPublic: pub }); if (graph) setGraph({ ...graph, style: s, public: pub }); }} />
      ) : null}
    </main>
  );
}

function GraphStyleSheet({ initial, isPublic, graph, onClose, onSaved }:
  { initial: GraphStyle; isPublic: boolean; graph: GraphData | null; onClose: () => void; onSaved: (style: string, pub: boolean) => void }) {
  const [style, setStyle] = useState<GraphStyle>(initial);
  const [pub, setPub] = useState(isPublic);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();

  async function save() {
    setBusy(true);
    setError(undefined);
    try {
      const r = await U.setGraph({ style: styleSpec(style), public: pub });
      onSaved(r.style, r.public);
      toast("Graph updated");
      onClose();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Sheet title="Your graph's look" onClose={onClose}>
      <div className="stack">
        <div className="stack-sm">
          <h2 className="title">Your graph's look</h2>
          <p className="small">Everyone who sees your graph sees it this way.</p>
        </div>
        {graph ? <ConfessionGraph data={graph} style={style} label="Preview" /> : null}
        <span className="tag">Colour</span>
        <div className="chips">
          {PALETTES.map((p) => (
            <button key={p.key} className={`chip swatch-chip${style.palette === p.key ? " on" : ""}`} aria-pressed={style.palette === p.key}
                    onClick={() => setStyle({ ...style, palette: p.key })}>
              <ShapeSwatch style={{ palette: p.key, shape: "circle" }} size={10} /> {p.name}
            </button>
          ))}
        </div>
        <span className="tag">Shape</span>
        <div className="chips">
          {SHAPES.map((s) => (
            <button key={s.key} className={`chip swatch-chip${style.shape === s.key ? " on" : ""}`} aria-pressed={style.shape === s.key}
                    onClick={() => setStyle({ ...style, shape: s.key })}>
              <ShapeSwatch style={{ palette: style.palette, shape: s.key }} size={10} /> {s.name}
            </button>
          ))}
        </div>
        <Toggle label="Show on my profile" sub="Others see how many confessions you posted each day — never your streak."
                on={pub} onChange={setPub} />
        <Notice>{error}</Notice>
        <Button block busy={busy} onClick={save}>Save look</Button>
        {styleSpec(style) !== styleSpec(DEFAULT_STYLE) ? (
          <Button variant="ghost" block onClick={() => setStyle({ ...DEFAULT_STYLE })}>Back to amber, rounded</Button>
        ) : null}
      </div>
    </Sheet>
  );
}
