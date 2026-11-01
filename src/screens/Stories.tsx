import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as RPointerEvent } from "react";
import { ApiError } from "../lib/api.ts";
import { ago, count, length, moderationReason } from "../lib/format.ts";
import { back, navigate } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import * as S from "../lib/stories.ts";
import { REACTIONS, REPORT_REASONS, type Reaction } from "../lib/types.ts";
import { Aura } from "../components/Aura.tsx";
import { Close, Flag, Trash } from "../components/icons.tsx";
import { Confirm, Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, Notice, Portal } from "../components/ui.tsx";

const HOLD_MS = 180;   // each story plays 5 s (the .run bar animation in app.css)

/**
 * Full-screen stories, one person after another (tray order). 5 s each; tap right → next,
 * left → back, hold → pause, swipe down → close. Mine: who saw it; theirs: react or report.
 */
export function StoryViewer({ alias }: { alias: string }) {
  const tray = S.useTray();
  const toast = useToast();
  const [loaded, setLoaded] = useState(S.currentTray().length > 0);
  const gi = tray.findIndex((g) => g.author.alias.toLowerCase() === alias.toLowerCase());
  const group = gi >= 0 ? tray[gi] : undefined;
  const [index, setIndex] = useState(() => (group ? S.startIndex(group) : 0));
  const [held, setHeld] = useState(false);
  const [sheet, setSheet] = useState<"viewers" | "delete" | "report" | null>(null);
  const story = group?.stories[Math.min(index, (group?.stories.length ?? 1) - 1)];
  const order = useRef<string[]>([]);

  useEffect(() => {
    void S.refreshTray(true).then(() => setLoaded(true));
  }, []);

  // Who to play after this person: the tray as it was when the viewer opened.
  useEffect(() => {
    if (!order.current.length && tray.length) order.current = tray.map((g) => g.author.alias.toLowerCase());
  }, [tray]);

  // Mark seen (others' stories), and dim the ring at once.
  useEffect(() => {
    if (!story || !group || group.isMe || story.seen) return;
    S.patchStory(story.id, { seen: true });
    void S.markSeen(story.id).catch(() => undefined);
  }, [story, group]);

  const close = useCallback(() => back(), []);

  const nextPerson = useCallback(() => {
    const at = order.current.indexOf(alias.toLowerCase());
    const live = S.currentTray();
    for (const a of order.current.slice(at + 1)) {
      const g = live.find((x) => x.author.alias.toLowerCase() === a);
      if (g && !g.isMe) { setIndex(S.startIndex(g)); navigate({ name: "stories", alias: g.author.alias }, true); return; }
    }
    close();
  }, [alias, close]);

  const next = useCallback(() => {
    if (!group) return;
    if (index < group.stories.length - 1) setIndex(index + 1);
    else nextPerson();
  }, [group, index, nextPerson]);

  const prev = useCallback(() => {
    if (index > 0) setIndex(index - 1);
  }, [index]);

  // Pointer: hold pauses, tap sides turns, swipe down closes.
  const press = useRef<{ x: number; y: number; t: number; timer: number } | null>(null);
  function down(e: RPointerEvent) {
    if ((e.target as HTMLElement).closest("button, a, input, textarea")) return;
    const timer = window.setTimeout(() => setHeld(true), HOLD_MS);
    press.current = { x: e.clientX, y: e.clientY, t: Date.now(), timer };
  }
  function up(e: RPointerEvent) {
    const p = press.current;
    press.current = null;
    if (!p) return;
    window.clearTimeout(p.timer);
    const wasHeld = held;
    setHeld(false);
    if (e.clientY - p.y > 90) return close();
    if (wasHeld || Date.now() - p.t > 450) return;
    const w = (e.currentTarget as HTMLElement).getBoundingClientRect();
    if (e.clientX - w.left < w.width / 3) prev(); else next();
  }

  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
      if (e.key === "ArrowRight") next();
      if (e.key === "ArrowLeft") prev();
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [close, next, prev]);

  async function react(r: Reaction) {
    if (!story) return;
    const mine = story.myReaction === r ? null : r;
    S.patchStory(story.id, { myReaction: mine });
    try {
      if (mine) { await S.react(story.id, mine); toast(`Sent ${REACTIONS.find((x) => x.key === mine)?.emoji}`); }
      else await S.unreact(story.id);
    } catch (e) {
      S.patchStory(story.id, { myReaction: story.myReaction ?? null });
      toast(errorMessage(e));
    }
  }

  if (!group || !story) {
    return (
      <Portal>
        <div className="story-viewer story-0">
          <button className="icon-btn story-x" aria-label="Close" onClick={close}><Close /></button>
          <div className="story-text">{loaded ? "This story has ended" : ""}</div>
        </div>
      </Portal>
    );
  }

  const paused = held || sheet !== null;
  return (
    <Portal>
      <div className={`story-viewer ${S.styleClass(story.style)}`} role="dialog" aria-modal="true"
           aria-label={`@${group.author.alias}'s story, ${index + 1} of ${group.stories.length}`}>
        <div className="story-bars" aria-hidden>
          {group.stories.map((s, i) => (
            <span key={s.id} className="story-bar">
              <i key={`${s.id}-${i === index ? "on" : "off"}`}
                 className={i < index ? "done" : i === index ? `run${paused ? " paused" : ""}` : ""}
                 onAnimationEnd={i === index ? next : undefined} />
            </span>
          ))}
        </div>
        <div className="story-head">
          <Aura value={group.author.avatar} size={34} />
          <span className="stack-xs grow">
            <span className="alias">{group.isMe ? "Your story" : `@${group.author.alias}`}</span>
            <span className="meta">{ago(story.createdAt)}</span>
          </span>
          <button className="icon-btn" aria-label={group.isMe ? "Delete story" : "Report story"}
                  onClick={() => setSheet(group.isMe ? "delete" : "report")}>{group.isMe ? <Trash /> : <Flag />}</button>
          <button className="icon-btn" aria-label="Close" onClick={close}><Close /></button>
        </div>
        <div className="story-stage" onPointerDown={down} onPointerUp={up}
             onPointerCancel={() => { if (press.current) window.clearTimeout(press.current.timer); press.current = null; setHeld(false); }}
             onContextMenu={(e) => e.preventDefault()}>
          <p className="story-text">{story.text}</p>
        </div>
        <div className="story-foot">
          {group.isMe ? (
            <button className="story-seen" onClick={() => setSheet("viewers")}>
              {story.viewCount ? `Seen by ${count(story.viewCount)}${story.reactionCount ? ` · ${count(story.reactionCount)} reacted` : ""}` : "No views yet"}
            </button>
          ) : (
            <div className="story-reactions" role="group" aria-label="React">
              {REACTIONS.map((r) => (
                <button key={r.key} className={`story-react${story.myReaction === r.key ? " on" : ""}`} aria-pressed={story.myReaction === r.key}
                        aria-label={`React ${r.label}`} onClick={() => void react(r.key)}>{r.emoji}</button>
              ))}
            </div>
          )}
        </div>
        {sheet === "viewers" ? <ViewersSheet storyId={story.id} onClose={() => setSheet(null)} /> : null}
        {sheet === "delete" ? (
          <Confirm title="Delete this story?" body="It disappears for everyone right away." confirm="Delete story" danger
                   onClose={() => setSheet(null)}
                   onConfirm={async () => {
                     await S.remove(story.id);
                     toast("Story deleted");
                     setSheet(null);
                     const left = group.stories.length - 1;
                     S.patchStory(story.id, null);
                     if (!left) close(); else setIndex(Math.min(index, left - 1));
                   }} />
        ) : null}
        {sheet === "report" ? (
          <StoryReport storyId={story.id} onClose={() => setSheet(null)}
                       onDone={() => { setSheet(null); S.patchStory(story.id, null); if (group.stories.length <= 1) nextPerson(); }} />
        ) : null}
      </div>
    </Portal>
  );
}

function ViewersSheet({ storyId, onClose }: { storyId: string; onClose: () => void }) {
  const [rows, setRows] = useState<S.Viewer[] | null>(null);
  const [next, setNext] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const load = useCallback(async (cursor: string | null) => {
    try {
      const r = await S.viewers(storyId, cursor);
      setRows((cur) => [...(cursor ? cur ?? [] : []), ...r.users]);
      setNext(r.nextCursor);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [storyId]);
  useEffect(() => { void load(null); }, [load]);
  return (
    <Sheet title="Seen by" onClose={onClose}>
      <div className="stack">
        <h2 className="title">Seen by</h2>
        <Notice>{error}</Notice>
        {rows === null && !error ? <span className="spinner center-self" aria-label="Loading" /> : null}
        {rows && !rows.length ? <p className="small">No views yet.</p> : null}
        <div className="people-list">
          {rows?.map((v) => (
            <button key={v.alias} className="received-row" onClick={() => { onClose(); navigate({ name: "profile", alias: v.alias }); }}>
              <Aura value={v.avatar} size={36} /><span className="alias grow">@{v.alias}</span>
              {v.reaction ? <span aria-label={v.reaction}>{REACTIONS.find((x) => x.key === v.reaction)?.emoji}</span> : null}
              <span className="meta">{ago(v.viewedAt)}</span>
            </button>
          ))}
        </div>
        {next ? <Button variant="ghost" onClick={() => void load(next)}>Show more</Button> : null}
      </div>
    </Sheet>
  );
}

function StoryReport({ storyId, onClose, onDone }: { storyId: string; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();
  return (
    <Sheet title="Report this story?" onClose={onClose}>
      <div className="stack">
        <div className="stack-sm"><h2 className="title">Report this story?</h2><p className="small">It's hidden for you right away. What's wrong with it?</p></div>
        <div className="choice-list" role="radiogroup">
          {REPORT_REASONS.map((r) => (
            <label key={r.key} className={`choice${reason === r.key ? " on" : ""}`}>
              <input type="radio" name="story-reason" checked={reason === r.key} onChange={() => setReason(r.key)} /><span>{r.label}</span>
            </label>
          ))}
        </div>
        <Notice>{error}</Notice>
        <Button block variant="danger" busy={busy} disabled={!reason} onClick={async () => {
          setBusy(true);
          try { await S.report(storyId, reason!); toast("Reported. Thanks for keeping this place kind."); onDone(); }
          catch (e) { setError(errorMessage(e)); setBusy(false); }
        }}>Report and hide</Button>
      </div>
    </Sheet>
  );
}

/** /story — a line, a colour, 24 hours. */
export function StoryCompose() {
  const { me } = useSession();
  const [text, setText] = useState("");
  const [style, setStyle] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [discard, setDiscard] = useState(false);
  const toast = useToast();
  const n = length(text.trim());
  const lines = useMemo(() => text.split("\n").length, [text]);
  const ok = n > 0 && n <= S.STORY_MAX && lines <= 4;

  async function share() {
    if (!me) return;
    setBusy(true);
    setError(undefined);
    try {
      const s = await S.share(text.trim(), style);
      S.addMine(s, { alias: me.alias, avatar: me.avatar });
      toast("Story shared — it fades in 24 hours");
      back();
    } catch (e) {
      setBusy(false);
      if (e instanceof ApiError && e.status === 451) setError(`This can't be shared. ${moderationReason(e.message)}`);
      else if (e instanceof ApiError && e.status === 422 && /10/.test(e.message)) setError("You can have up to 10 stories at a time.");
      else setError(errorMessage(e));
    }
  }

  return (
    <Portal>
      <div className={`story-viewer story-compose ${S.styleClass(style)}`} role="dialog" aria-modal="true" aria-label="Add a story">
        <div className="story-head">
          <button className="icon-btn" aria-label="Close" onClick={() => (text.trim() ? setDiscard(true) : back())}><Close /></button>
          <span className="grow" />
          <span className="meta">{n} / {S.STORY_MAX}</span>
        </div>
        <div className="story-stage">
          <textarea className="story-input" autoFocus rows={4} maxLength={S.STORY_MAX + 20} value={text}
                    placeholder="Say it in a line…" aria-label="Your story"
                    onChange={(e) => setText(e.target.value.split("\n").slice(0, 4).join("\n"))} />
        </div>
        <div className="story-foot stack-sm">
          <div className="story-styles" role="radiogroup" aria-label="Background">
            {S.STYLES.map((s, i) => (
              <button key={s.bg} role="radio" aria-checked={style === i} aria-label={`Background ${i + 1}: ${s.label}`}
                      className={`story-swatch ${s.bg}${style === i ? " on" : ""}`} onClick={() => setStyle(i)} />
            ))}
          </div>
          <Notice>{error}</Notice>
          <Button block busy={busy} disabled={!ok} onClick={share}>{busy ? "Checking…" : "Share for 24h"}</Button>
        </div>
        {discard ? <Confirm title="Discard this story?" body="What you wrote won't be kept." confirm="Discard" danger
                            onClose={() => setDiscard(false)} onConfirm={async () => back()} /> : null}
      </div>
    </Portal>
  );
}
