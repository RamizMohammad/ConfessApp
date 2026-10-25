import { useEffect, useRef, useState, type ReactNode } from "react";
import { ApiError } from "../lib/api.ts";
import { ago, count } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { errorMessage } from "../lib/session.tsx";
import { MOODS, REACTIONS, REPORT_REASONS, type Gif, type Post, type Reaction } from "../lib/types.ts";
import { Aura } from "./Aura.tsx";
import { Ban, Close, Flag, Heart, Message, MessageOff, More, Trash, UserX } from "./icons.tsx";
import { useToast } from "./toast.tsx";
import { Button, Notice, Portal } from "./ui.tsx";

const emojiOf = (k: Reaction | null) => REACTIONS.find((r) => r.key === k)?.emoji ?? "❤️";
const labelOf = (k: Reaction | null) => REACTIONS.find((r) => r.key === k)?.label ?? "Felt";

/** Others' reactions, most used first (Android Reactions.top). */
function topReactions(p: Post, n: number): Reaction[] {
  return REACTIONS.map((r) => r.key)
    .filter((k) => (p.reactions[k] ?? 0) > 0)
    .sort((a, b) => (p.reactions[b] ?? 0) - (p.reactions[a] ?? 0))
    .slice(0, n);
}

export function GifView({ gif, compact }: { gif: Gif; compact?: boolean }) {
  const ratio = gif.width && gif.height ? gif.height / gif.width : 0.6;
  return (
    <div className={`gif${compact ? " compact" : ""}`} data-ratio={ratio > 1 ? "tall" : "wide"}>
      <img src={compact ? gif.previewUrl : gif.url} alt={gif.title || "GIF"} loading="lazy" decoding="async"
           referrerPolicy="no-referrer" width={gif.width ?? undefined} height={gif.height ?? undefined} />
      <span className="gif-badge">GIF</span>
    </div>
  );
}

/* ── Reactions ──────────────────────────────────────────────────────────── */

function useLongPress(onLong: () => void, ms = 450) {
  const t = useRef<number | undefined>(undefined);
  const fired = useRef(false);
  return {
    fired,
    handlers: {
      onPointerDown: () => {
        fired.current = false;
        t.current = window.setTimeout(() => { fired.current = true; onLong(); }, ms);
      },
      onPointerUp: () => window.clearTimeout(t.current),
      onPointerLeave: () => window.clearTimeout(t.current),
      onContextMenu: (e: React.MouseEvent) => { e.preventDefault(); onLong(); },
    },
  };
}

function ReactionPicker({ mine, onPick, onClose }: { mine: Reaction | null; onPick: (r: Reaction) => void; onClose: () => void }) {
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
  }, [onClose]);
  return (
    <>
      <div className="picker-scrim" onClick={onClose} />
      <div className="reaction-picker" role="menu" aria-label="React">
        {REACTIONS.map((r) => (
          <button key={r.key} role="menuitemradio" aria-checked={mine === r.key} className={mine === r.key ? "on" : ""}
                  onClick={() => onPick(r.key)} aria-label={r.label}>
            <span aria-hidden>{r.emoji}</span><small>{r.label}</small>
          </button>
        ))}
      </div>
    </>
  );
}

/* ── Poll ───────────────────────────────────────────────────────────────── */

function timeLeft(iso: string): string {
  const h = (new Date(iso).getTime() - Date.now()) / 3_600_000;
  if (h <= 1) return "Closing soon";
  if (h < 24) return `${Math.floor(h)}h left`;
  const d = Math.floor(h / 24);
  return d === 1 ? "1 day left" : `${d} days left`;
}

function PollView({ post, onChange }: { post: Post; onChange: (p: Post) => void }) {
  const poll = post.poll!;
  const [busy, setBusy] = useState<number | null>(null);
  const toast = useToast();
  const showResults = poll.closed || poll.myVote !== null || post.isMine;
  async function choose(i: number) {
    setBusy(i);
    try {
      onChange(await P.vote(post.postId, i));
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }
  return (
    <div className="poll" onClick={(e) => e.stopPropagation()}>
      {poll.options.map((o, i) => {
        const pct = poll.totalVotes ? Math.round(((o.votes ?? 0) / poll.totalVotes) * 100) : 0;
        return showResults ? (
          <div key={i} className={`poll-result${poll.myVote === i ? " mine" : ""}`} aria-label={`${o.text}, ${pct} percent`}>
            <span className="bar" data-pct={pct}><i className={`w${Math.round(pct / 5) * 5}`} /></span>
            <span className="label">{o.text}</span><span className="pct">{pct}%</span>
          </div>
        ) : (
          <button key={i} className="poll-option" disabled={busy !== null} onClick={() => void choose(i)}>
            {busy === i ? <span className="spinner" /> : null}{o.text}
          </button>
        );
      })}
      <span className="meta">
        {count(poll.totalVotes)} vote{poll.totalVotes === 1 ? "" : "s"} ·{" "}
        {poll.closed ? "Final results" : showResults ? timeLeft(poll.closesAt) : "Vote to see results"}
      </span>
    </div>
  );
}

/* ── Card ───────────────────────────────────────────────────────────────── */

export interface CardActions {
  onChange: (p: Post) => void;
  /** Gone for me: reported, deleted. */
  onRemove: (postId: string) => void;
  /** I blocked this person: drop all their cards. */
  onBlocked: (alias: string) => void;
  onOpen?: (p: Post) => void;
}

export function PostCard({ post, detail, actions }: { post: Post; detail?: boolean; actions: CardActions }) {
  const toast = useToast();
  const [revealed, setRevealed] = useState(!post.sensitive || post.isMine);
  const [picker, setPicker] = useState(false);
  const [menu, setMenu] = useState(false);
  const [burst, setBurst] = useState(false);
  const mine = post.myReaction ?? (post.likedByMe ? "felt" : null);
  const lastTap = useRef(0);

  async function setReaction(next: Reaction | null) {
    const before = post;
    const counts = { ...post.reactions };
    if (mine) counts[mine] = Math.max(0, (counts[mine] ?? 1) - 1);
    if (next) counts[next] = (counts[next] ?? 0) + 1;
    actions.onChange({ ...post, myReaction: next, likedByMe: !!next, reactions: counts,
                       likeCount: post.likeCount + (next && !mine ? 1 : !next && mine ? -1 : 0) });
    if (next) setBurst(true);
    try {
      const r = next ? await P.react(post.postId, next) : await P.unreact(post.postId);
      actions.onChange({ ...before, myReaction: r.myReaction, likedByMe: r.liked, likeCount: r.likeCount, reactions: r.reactions });
    } catch (e) {
      actions.onChange(before);
      if (e instanceof ApiError && e.status === 404) actions.onRemove(post.postId);
      else toast(errorMessage(e));
    }
  }

  const press = useLongPress(() => setPicker(true));
  const others = topReactions(post, 3).filter((k) => k !== mine && k !== "felt");
  const mood = MOODS.find((m) => m.key === post.mood);

  function onCardClick() {
    const now = Date.now();
    if (now - lastTap.current < 300) {
      lastTap.current = 0;
      if (!mine) void setReaction("felt");
      else setBurst(true);
      return;
    }
    lastTap.current = now;
    if (!detail && actions.onOpen) {
      window.setTimeout(() => { if (lastTap.current === now) actions.onOpen?.(post); }, 300);
    }
  }

  return (
    <article className={`paper card${detail ? " detail" : ""}`} aria-label={`Confession by ${post.author.alias}, ${ago(post.createdAt)}`}>
      <header className="card-head">
        <Aura value={post.author.avatar} size={32} />
        <div className="card-who">
          <span className="alias">@{post.author.alias}{post.isMine ? <em> · you</em> : null}</span>
          <span className="meta">{ago(post.createdAt)}{mood ? ` · ${mood.label}` : ""}{post.chapter ? ` · Chapter ${post.chapter.chapterNo}` : ""}</span>
        </div>
        <button className="icon-btn" aria-label={post.isMine ? "More options" : "Report or more"} onClick={() => setMenu(true)}>
          <More />
        </button>
      </header>

      <div className={`card-body${revealed ? "" : " veiled-content"}`} onClick={revealed ? onCardClick : undefined}>
        {post.letterTo ? <p className="letter-to">To: {post.letterTo}</p> : null}
        {post.chapter?.title ? <p className="chapter-title">{post.chapter.title}</p> : null}
        <p className={`quote${detail ? " full" : ""}`}>{post.text}</p>
        {post.gif ? <GifView gif={post.gif} /> : null}
        {burst ? <span className="heart-burst" onAnimationEnd={() => setBurst(false)} aria-hidden>{emojiOf(mine)}</span> : null}
      </div>
      {!revealed ? (
        <button className="reveal" onClick={() => setRevealed(true)}>
          <span className="tag">Sensitive</span>
          <span>Tap to read</span>
        </button>
      ) : null}

      {revealed && post.poll ? <PollView post={post} onChange={actions.onChange} /> : null}

      <footer className="card-foot">
        <button className={`react-btn${mine ? " on" : ""}`} {...press.handlers}
                onClick={() => { if (!press.fired.current) void setReaction(mine ? null : "felt"); }}
                aria-label={`${mine ? `You reacted: ${labelOf(mine)}` : "Felt"}${post.likeCount ? `, ${post.likeCount}` : ""}. Long-press for more reactions`}>
          {mine && mine !== "felt" ? <span className="emoji">{emojiOf(mine)}</span> : <Heart filled={mine === "felt"} />}
          {others.length ? <span className="emoji small">{others.map(emojiOf).join("")}</span> : null}
          {post.likeCount ? <span>{count(post.likeCount)}</span> : null}
        </button>
        <button className="react-btn" onClick={() => actions.onOpen?.(post)} disabled={detail}
                aria-label={post.commentsEnabled ? `Responses, ${post.commentCount}` : "Responses closed"}>
          {post.commentsEnabled ? <Message /> : <MessageOff />}
          {post.commentCount ? <span>{count(post.commentCount)}</span> : null}
        </button>
        <span className="meta stamp">Anonymous</span>
      </footer>

      {picker ? <ReactionPicker mine={mine} onClose={() => setPicker(false)}
                                onPick={(r) => { setPicker(false); void setReaction(r === mine ? null : r); }} /> : null}
      {menu ? <PostMenu post={post} actions={actions} onClose={() => setMenu(false)} /> : null}
    </article>
  );
}

/* ── Menus, report, block, delete ───────────────────────────────────────── */

export function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
  }, [onClose]);
  return (
    <Portal>
      <div className="overlay" onClick={onClose}>
        <section className="sheet" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
          <div className="grabber" />
          {children}
        </section>
      </div>
    </Portal>
  );
}

export function Confirm({ title, body, confirm, danger, onConfirm, onClose }:
  { title: string; body: string; confirm: string; danger?: boolean; onConfirm: () => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  return (
    <Portal>
    <div className="overlay center" onClick={onClose}>
      <section className="dialog stack" role="alertdialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2 className="title">{title}</h2>
        <p className="small">{body}</p>
        <Notice>{error}</Notice>
        <Button block variant={danger ? "danger" : "primary"} busy={busy}
                onClick={async () => { setBusy(true); try { await onConfirm(); } catch (e) { setError(errorMessage(e)); setBusy(false); } }}>
          {confirm}
        </Button>
        <Button variant="ghost" block onClick={onClose}>Cancel</Button>
      </section>
    </div>
    </Portal>
  );
}

export type ReportKind = { kind: "post"; id: string } | { kind: "comment"; id: string } | { kind: "user"; alias: string };

export function ReportSheet({ target, onDone, onClose }: { target: ReportKind; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();
  const copy = target.kind === "post"
    ? { title: "Report this confession", sub: "It will be hidden for you right away. Our moderators review reports.", send: "Report and hide", done: "Reported. You won't see it again." }
    : target.kind === "comment"
      ? { title: "Report this response", sub: "It will be hidden for you right away. Our moderators review reports.", send: "Report and hide", done: "Reported. You won't see that response again." }
      : { title: "Report this person", sub: "Their account goes to our moderators. Use this when it's about them, not just one confession.", send: "Report person", done: "Reported. Our moderators will take a look." };

  async function send() {
    if (!reason) return;
    setBusy(true);
    setError(undefined);
    try {
      if (target.kind === "post") await P.reportPost(target.id, reason);
      else if (target.kind === "comment") await P.reportComment(target.id, reason);
      else await P.reportUser(target.alias, reason);
      toast(copy.done);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Sheet title={copy.title} onClose={onClose}>
      <div className="stack">
        <div className="stack-sm"><h2 className="title">{copy.title}</h2><p className="small">{copy.sub}</p></div>
        <div className="choice-list" role="radiogroup">
          {REPORT_REASONS.map((r) => (
            <label key={r.key} className={`choice${reason === r.key ? " on" : ""}`}>
              <input type="radio" name="reason" value={r.key} checked={reason === r.key} onChange={() => setReason(r.key)} />
              <span>{r.label}</span>
            </label>
          ))}
        </div>
        <Notice>{error}</Notice>
        <Button block variant="danger" busy={busy} disabled={!reason} onClick={send}>{copy.send}</Button>
      </div>
    </Sheet>
  );
}

function PostMenu({ post, actions, onClose }: { post: Post; actions: CardActions; onClose: () => void }) {
  const [step, setStep] = useState<"menu" | "report" | "reportUser" | "block" | "delete">("menu");
  const toast = useToast();
  const alias = post.author.alias;

  if (step === "report") return <ReportSheet target={{ kind: "post", id: post.postId }} onClose={onClose} onDone={() => { onClose(); actions.onRemove(post.postId); }} />;
  if (step === "reportUser") return <ReportSheet target={{ kind: "user", alias }} onClose={onClose} onDone={onClose} />;
  if (step === "block") {
    return (
      <Confirm title={`Block @${alias}?`} danger confirm="Block" onClose={onClose}
               body="You won't see each other's confessions, responses or stories, and any follow between you is removed. They won't be told."
               onConfirm={async () => { await P.blockUser(alias); toast(`Blocked @${alias}`); onClose(); actions.onBlocked(alias); }} />
    );
  }
  if (step === "delete") {
    return (
      <Confirm title="Delete this confession?" danger confirm="Delete"
               body="It disappears for everyone, along with its responses. This can't be undone." onClose={onClose}
               onConfirm={async () => { await P.deletePost(post.postId); toast("Confession deleted."); onClose(); actions.onRemove(post.postId); }} />
    );
  }
  return (
    <Sheet title="Options" onClose={onClose}>
      <div className="menu-list">
        {post.isMine ? (
          <button className="menu-item danger" onClick={() => setStep("delete")}><Trash /> Delete confession</button>
        ) : (
          <>
            <button className="menu-item" onClick={() => setStep("report")}><Flag /> Report confession</button>
            <button className="menu-item" onClick={() => setStep("reportUser")}><UserX /> Report @{alias}</button>
            <button className="menu-item danger" onClick={() => setStep("block")}><Ban /> Block @{alias}</button>
          </>
        )}
        <button className="menu-item" onClick={onClose}><Close /> Cancel</button>
      </div>
    </Sheet>
  );
}
