/**
 * The confession card (item_confession_card + PostCardBinder), its reaction bar (ReactionPicker,
 * popup_reactions), the flag / bin menu (PostMenu, popup_post_menu), the report sheet
 * (ReportSheet, sheet_report) and the confirms (ConfessDialog), as the Android app draws them.
 */
import {
  forwardRef, useCallback, useEffect, useImperativeHandle, useLayoutEffect, useRef, useState,
  type ReactNode, type RefObject,
} from "react";
import { createPortal } from "react-dom";
import { ApiError } from "../lib/api.ts";
import { count, relative } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { errorMessage } from "../lib/session.tsx";
import { REACTIONS, type Gif, type Mood, type Post, type Reaction } from "../lib/types.ts";
import { navigate } from "../lib/router.ts";
import { P as plural, S } from "../lib/strings.ts";
import { hasStory, RingAvatar } from "./stories.tsx";
import { useToast } from "./toast.tsx";
import { Portal } from "./ui.tsx";
import { overshoot } from "../ui/Art.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, ConfessDialog, haptic, PopMenu, Sheet as KSheet, type MenuItem } from "../ui/kit.tsx";
import { accelerate, decelerate, tween } from "../ui/motion.ts";

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");

/* ── Reactions & moods (Reactions.java, Moods.java) ─────────────────────── */

export const emojiOf = (k: Reaction | null) => REACTIONS.find((r) => r.key === k)?.emoji ?? "❤️";
export const reactionLabel = (k: Reaction) => S(`reaction_${k}`);
export const MOOD_EMOJI: Record<Mood, string> = { vent: "🌧️", advice: "💬", listen: "🫂", happy: "☀️", heartbreak: "💔" };
export const moodLabel = (m: Mood) => S(`mood_${m}`);

/** My reaction (older payloads only have likedByMe = a heart). */
export const mineOf = (p: Post): Reaction | null => p.myReaction ?? (p.likedByMe ? "felt" : null);

/** Others' reactions, most used first. */
function top(p: Post): Reaction[] {
  return REACTIONS.map((r) => r.key)
    .filter((k) => (p.reactions[k] ?? 0) > 0)
    .sort((a, b) => (p.reactions[b] ?? 0) - (p.reactions[a] ?? 0));
}

/** Reactions.summary: my non-heart reaction first, then the most used others, no hearts, at most `max`. */
export function reactionSummary(p: Post, max = 3): string {
  const mine = mineOf(p);
  let out = "", shown = 0;
  if (mine && mine !== "felt") { out += emojiOf(mine); shown++; }
  for (const k of top(p)) {
    if (shown >= max) break;
    if (k === "felt" || k === mine) continue;
    out += emojiOf(k);
    shown++;
  }
  return out;
}

/** Reactions.apply: the post as it is once I feel `want` (or nothing). */
export function withReaction(p: Post, want: Reaction | null): Post {
  const had = mineOf(p);
  const r = { ...p.reactions };
  if (had) r[had] = Math.max(0, (r[had] ?? 1) - 1);
  if (want) r[want] = (r[want] ?? 0) + 1;
  return { ...p, myReaction: want, likedByMe: !!want, reactions: r, likeCount: Math.max(0, p.likeCount + (want && !had ? 1 : !want && had ? -1 : 0)) };
}

/* ── Sensitive (Sensitive.java): revealed ones stay revealed for this run ── */

const revealed = new Set<string>();
const sensitiveHidden = (p: Post) => p.sensitive && !p.isMine && !revealed.has(p.postId);

/* ── GIF (AspectImageView + GifViews) ───────────────────────────────────── */

/** A GIPHY GIF in its own shape; very tall ones are cropped (maxRatio). `thumb` uses the small rendition. */
export function GifView({ gif, thumb, maxRatio = 1.25, className, onClick }:
  { gif: Gif; thumb?: boolean; maxRatio?: number; className?: string; onClick?: () => void }) {
  const box = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    const r = gif.width && gif.height ? gif.height / gif.width : 0.75;
    box.current?.style.setProperty("--ratio", String(Math.min(maxRatio, Math.max(0.3, r))));
  }, [gif, maxRatio]);
  const img = <img src={thumb ? gif.previewUrl : gif.url} alt={gif.title || S("cd_gif")} loading="lazy" decoding="async"
                   referrerPolicy="no-referrer" />;
  return onClick
    ? <button type="button" ref={box as RefObject<HTMLButtonElement>} className={cx("gifv", className)} onClick={onClick}>{img}</button>
    : <span ref={box as RefObject<HTMLSpanElement>} className={cx("gifv", className)}>{img}</span>;
}

/* ── The heart burst (PostCardBinder.burst) ─────────────────────────────── */

type BurstHandle = { fire: (r: Reaction) => void };
type Shot = { id: number; reaction: Reaction; sparks: { a: number; d: number; rot: number }[] };

function BurstItem({ shot, onDone }: { shot: Shot; onDone: () => void }) {
  const centre = useRef<HTMLSpanElement>(null);
  const sparks = useRef<(HTMLSpanElement | null)[]>([]);
  useLayoutEffect(() => {
    const c = centre.current;
    void tween(c, { alpha: [0, 1], scale: [0.2, 1.15] }, 240, { ease: overshoot(3) })
      .then(() => tween(c, { alpha: [1, 0], scale: [1.15, 1.5], y: [0, -36] }, 320, { delay: 220, ease: accelerate() }))
      .then(onDone);
    shot.sparks.forEach((s, i) => {
      const el = sparks.current[i];
      void tween(el, { alpha: [0, 1] }, 80, { delay: 60 }).then(() =>
        tween(el, { x: [0, Math.cos(s.a) * s.d], y: [0, Math.sin(s.a) * s.d - 10], rotate: [0, s.rot], scale: [1, 0.4], alpha: [1, 0] },
              520, { ease: decelerate() }));
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <>
      <span ref={centre} className={cx("pc-burst", shot.reaction !== "felt" && "emoji")}>
        {shot.reaction === "felt" ? <Icon name="heart_filled" size={88} /> : emojiOf(shot.reaction)}
      </span>
      {shot.sparks.map((_, i) => (
        <span key={i} ref={(el) => { sparks.current[i] = el; }} className={cx("pc-spark", i % 2 ? "gold" : "amber")}>
          <Icon name="heart_filled" size={14} />
        </span>
      ))}
    </>
  );
}

const BurstLayer = forwardRef<BurstHandle>(function BurstLayer(_, ref) {
  const [shots, setShots] = useState<Shot[]>([]);
  const seq = useRef(0);
  useImperativeHandle(ref, () => ({
    fire: (reaction) => {
      const sparks = Array.from({ length: 8 }, (_, i) => ({
        a: (Math.PI * 2 * i) / 8 + Math.random() * 0.4, d: 70 * (0.75 + Math.random() * 0.5), rot: Math.floor(Math.random() * 90) - 45,
      }));
      setShots((s) => [...s, { id: ++seq.current, reaction, sparks }]);
    },
  }), []);
  return (
    <span className="pc-burst-layer" aria-hidden>
      {shots.map((s) => <BurstItem key={s.id} shot={s} onDone={() => setShots((all) => all.filter((x) => x.id !== s.id))} />)}
    </span>
  );
});

/* ── Reaction bar (ReactionPicker): hold the heart, slide, let go ───────── */

type PickerHandle = { hover: (x: number, y: number) => void; release: (x: number, y: number) => void };

const ReactionBar = forwardRef<PickerHandle, { anchor: DOMRect; mine: Reaction | null; onPick: (r: Reaction) => void; onClose: () => void }>(
  function ReactionBar({ anchor, mine, onPick, onClose }, ref) {
    const wrap = useRef<HTMLDivElement>(null);
    const row = useRef<HTMLDivElement>(null);
    const items = useRef<(HTMLButtonElement | null)[]>([]);
    const [hovered, setHovered] = useState(-1);
    const hov = useRef(-1);

    useLayoutEffect(() => {
      const w = wrap.current;
      if (!w) return;
      const vw = document.documentElement.clientWidth;
      const h = w.offsetHeight, width = w.offsetWidth, gap = 4;
      const above = anchor.top - h - gap >= 8;
      const x = Math.max(8, Math.min(vw - width - 8, anchor.left - 4));
      const y = above ? anchor.top - h - gap : anchor.bottom + gap;
      w.style.setProperty("--x", `${x}px`);
      w.style.setProperty("--y", `${y}px`);
      items.current.forEach((el, i) => void tween(el, { scale: [0.3, 1], alpha: [0, 1] }, 220, { delay: 30 * i, ease: overshoot(2.2) }));
    }, [anchor]);

    const setHover = useCallback((i: number) => {
      if (i === hov.current) return;
      const old = items.current[hov.current];
      if (old) void tween(old.firstElementChild, { scale: [1.35, 1], y: [-8, 0] }, 120);
      hov.current = i;
      setHovered(i);
      const el = items.current[i];
      if (el) { void tween(el.firstElementChild, { scale: [1, 1.35], y: [0, -8] }, 120); haptic("tick"); }
    }, []);

    /** Uses each emoji's layout slot (not its moving, enlarged box), with a little grace for the current one. */
    const indexAt = useCallback((x: number, y: number) => {
      const r = row.current?.getBoundingClientRect();
      if (!r || y < r.top - 72 || y > r.bottom + 28) return -1;
      const rx = x - r.left;
      const cur = items.current[hov.current];
      if (cur && rx >= cur.offsetLeft - 6 && rx < cur.offsetLeft + cur.offsetWidth + 6) return hov.current;
      return items.current.findIndex((el) => !!el && rx >= el.offsetLeft && rx < el.offsetLeft + el.offsetWidth);
    }, []);

    const pick = useCallback((i: number) => { haptic(); onPick(REACTIONS[i]!.key); }, [onPick]);

    useImperativeHandle(ref, () => ({
      hover: (x, y) => setHover(indexAt(x, y)),
      release: (x, y) => { const i = indexAt(x, y); if (i >= 0) pick(i); else setHover(-1); },
    }), [indexAt, pick, setHover]);

    useEffect(() => {
      const esc = (e: KeyboardEvent) => e.key === "Escape" && onClose();
      window.addEventListener("keydown", esc);
      return () => window.removeEventListener("keydown", esc);
    }, [onClose]);

    return createPortal(
      <>
        <div className="rx-scrim" onPointerDown={onClose} />
        <div ref={wrap} className="rx m-popup-in" role="menu" aria-label={S("cd_react_hint")}>
          <span className={cx("rx-label t-meta-small", hovered < 0 && "off")} aria-hidden>
            {hovered >= 0 ? reactionLabel(REACTIONS[hovered]!.key) : " "}
          </span>
          <div ref={row} className="rx-row">
            {REACTIONS.map((r, i) => (
              <button key={r.key} ref={(el) => { items.current[i] = el; }} type="button" role="menuitemradio" aria-checked={mine === r.key}
                      aria-label={reactionLabel(r.key)} className={cx("rx-item", mine === r.key && "on")}
                      onPointerDown={() => setHover(i)} onClick={() => pick(i)}>
                <span>{r.emoji}</span>
              </button>
            ))}
          </div>
        </div>
      </>,
      document.body,
    );
  });

/* ── Poll (item_poll_option) ────────────────────────────────────────────── */

function timeLeft(iso: string): string {
  const mins = Math.floor((new Date(iso).getTime() - Date.now()) / 60000);
  if (!(mins > 0)) return S("poll_left_soon");
  const h = Math.floor(mins / 60);
  if (h >= 48) return S("poll_left_days", Math.floor(h / 24));
  if (h >= 24) return S("poll_left_day");
  if (h >= 1) return S("poll_left_hours", h);
  return S("poll_left_soon");
}

const pollsSeen = new Set<string>();

function PollView({ post, disabled, onVote }: { post: Post; disabled: boolean; onVote: (i: number) => void }) {
  const poll = post.poll!;
  const results = poll.closed || poll.myVote !== null || post.isMine;
  const total = Math.max(0, poll.totalVotes);
  const sig = `${post.postId}|${poll.options.map((o) => o.votes ?? "-").join(",")}|${poll.myVote}`;
  const animate = results && !pollsSeen.has(sig);
  let best = -1, bestVotes = -1;
  poll.options.forEach((o, i) => { if (o.votes != null && o.votes > bestVotes) { bestVotes = o.votes; best = i; } });
  const fills = useRef<(HTMLSpanElement | null)[]>([]);
  useLayoutEffect(() => {
    if (!results) return;
    poll.options.forEach((o, i) => {
      const share = total > 0 ? (o.votes ?? 0) / total : 0;
      const el = fills.current[i];
      el?.style.setProperty("--share", String(share));
      if (animate) void tween(el, { scaleX: [0, share] }, 420, { delay: 60 * i, ease: decelerate() });
      else el?.getAnimations().forEach((a) => a.cancel());
    });
    pollsSeen.add(sig);
  }, [sig, results]); // eslint-disable-line react-hooks/exhaustive-deps
  const votes = plural("poll_votes", total, count(total));
  const left = timeLeft(poll.closesAt);
  const meta = poll.closed ? `${S("poll_final")} · ${votes}` : `${results ? votes : S("poll_vote_to_see")}${left ? ` · ${left}` : ""}`;
  return (
    <div className="pc-poll">
      {poll.options.map((o, i) => {
        const share = total > 0 ? (o.votes ?? 0) / total : 0;
        const pct = Math.round(share * 100);
        const mine = results && poll.myVote === i;
        const inner = (
          <>
            {results ? <span ref={(el) => { fills.current[i] = el; }} className={cx("pc-poll-fill", !(i === best && (o.votes ?? 0) > 0) && "dim")} /> : null}
            <span className="pc-poll-row">
              <span className={cx("pc-poll-label t-body", mine && "mine")}>{mine ? <Icon name="check" size={16} /> : null}{o.text}</span>
              {results ? <span className="t-meta">{pct}%</span> : null}
            </span>
          </>
        );
        return results ? (
          <div key={i} className="pc-poll-opt" aria-label={S("cd_poll_result", o.text, pct)}>{inner}</div>
        ) : (
          <button key={i} type="button" className="pc-poll-opt" disabled={disabled} aria-label={S("cd_poll_option", o.text, i + 1, poll.options.length)}
                  onClick={(e) => {
                    haptic("tick");
                    void tween(e.currentTarget, { scale: [1, 0.97] }, 80).then(() => tween(e.currentTarget, { scale: [0.97, 1] }, 120));
                    onVote(i);
                  }}>{inner}</button>
        );
      })}
      <span className="pc-poll-meta t-meta-small">{meta}</span>
    </div>
  );
}

/* ── Card ───────────────────────────────────────────────────────────────── */

export function openProfile(alias: string, mine?: boolean): void {
  navigate(mine ? { name: "me" } : { name: "profile", alias });
}

export interface CardActions {
  onChange: (p: Post) => void;
  /** Gone for me: reported, deleted. */
  onRemove: (postId: string) => void;
  /** I blocked this person: drop all their cards. */
  onBlocked: (alias: string) => void;
  /** Open the confession (toResponses: jump to the responses, keyboard up). */
  onOpen?: (p: Post, toResponses?: boolean) => void;
}

/** Long-press helper: fires after `ms` unless the finger moved or lifted. */
function useHold(ms: number, onHold: (e: PointerEvent | React.PointerEvent) => void) {
  const t = useRef<number | undefined>(undefined);
  const start = useRef<{ x: number; y: number } | null>(null);
  const fired = useRef(false);
  const cancel = () => { window.clearTimeout(t.current); start.current = null; };
  return {
    fired,
    cancel,
    down: (e: React.PointerEvent) => {
      if (e.pointerType === "mouse" && e.button !== 0) return;
      fired.current = false;
      start.current = { x: e.clientX, y: e.clientY };
      const ev = e;
      t.current = window.setTimeout(() => { fired.current = true; start.current = null; onHold(ev); }, ms);
    },
    move: (e: React.PointerEvent) => {
      const s = start.current;
      if (s && Math.hypot(e.clientX - s.x, e.clientY - s.y) > 10) cancel();
    },
    up: cancel,
  };
}

/**
 * One confession card. `detail`: the big version on the confession screen. `sample`: a made-up
 * card (Welcome → "A glimpse inside"): every tap only calls it, nothing reaches the server.
 */
export function PostCard({ post, detail, actions, sample, menuAnchor }:
  { post: Post; detail?: boolean; actions: CardActions; sample?: () => void;
    /** Detail: the top bar's flag / bin opens this card's menu. */
    menuAnchor?: { ref: RefObject<HTMLElement | null>; open: boolean; onClose: () => void } }) {
  const toast = useToast();
  const [hidden, setHidden] = useState(() => sensitiveHidden(post));
  useEffect(() => setHidden(sensitiveHidden(post)), [post.postId, post.sensitive]); // eslint-disable-line react-hooks/exhaustive-deps
  const [picker, setPicker] = useState<DOMRect | null>(null);
  const [menu, setMenu] = useState(false);
  const [voting, setVoting] = useState(false);
  const mine = mineOf(post);
  const alias = post.author.alias;
  const when = relative(post.createdAt);
  const card = useRef<HTMLElement>(null);
  const felt = useRef<HTMLButtonElement>(null);
  const more = useRef<HTMLButtonElement>(null);
  const burst = useRef<BurstHandle>(null);
  const bar = useRef<PickerHandle>(null);
  const single = useRef<number | undefined>(undefined);
  const holding = useRef(false);
  const feltPop = useRef(false);

  useEffect(() => () => window.clearTimeout(single.current), []);

  // Felt button bounce when my reaction lands (bindFelt animate).
  useLayoutEffect(() => {
    if (feltPop.current && mine) void tween(felt.current, { scale: [0.8, 1] }, 260, { ease: overshoot(3) });
    feltPop.current = false;
  }, [mine]);

  async function setReaction(next: Reaction | null) {
    if (sample) { sample(); return; }
    const before = post;
    feltPop.current = true;
    actions.onChange(withReaction(post, next));
    try {
      const r = next ? await P.react(post.postId, next) : await P.unreact(post.postId);
      actions.onChange({ ...withReaction(before, next), myReaction: r.myReaction, likedByMe: r.liked, likeCount: r.likeCount, reactions: r.reactions });
    } catch (e) {
      actions.onChange(before);
      if (e instanceof ApiError && e.status === 404) actions.onRemove(post.postId);
      else toast(errorMessage(e));
    }
  }

  async function vote(i: number) {
    if (sample) { sample(); return; }
    setVoting(true);
    try {
      actions.onChange(await P.vote(post.postId, i));
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setVoting(false);
    }
  }

  const reveal = () => {
    revealed.add(post.postId);
    haptic("tick");
    setHidden(false);
  };

  /** Double tap = ❤️ (never takes a reaction back). */
  const doubleTap = () => {
    if (sample) { sample(); return; }
    if (!mine) void setReaction("felt");
    else haptic("tick");
    burst.current?.fire(mine ?? "felt");
  };

  // Card taps: one → open (after the double-tap window) or reveal; two → felt; hold → copy.
  const copyHold = useHold(500, () => {
    window.clearTimeout(single.current);
    single.current = undefined;
    const text = post.text?.trim();
    if (!text) return;
    void navigator.clipboard?.writeText(text).then(() => { haptic("tick"); toast(S("copied")); }, () => {});
  });

  const onCardClick = (e: React.MouseEvent) => {
    if ((e.target as Element).closest("button, a, input, .pc-poll")) return;
    if (copyHold.fired.current) { copyHold.fired.current = false; return; }
    if (single.current !== undefined) {
      window.clearTimeout(single.current);
      single.current = undefined;
      doubleTap();
      return;
    }
    single.current = window.setTimeout(() => {
      single.current = undefined;
      if (sample) { sample(); return; }
      if (hidden) { reveal(); return; }
      if (!detail) actions.onOpen?.(post, false);
    }, 300);
  };

  // The heart: tap = felt on/off; hold = the reaction bar, slide onto one and let go.
  const feltHold = useHold(400, () => {
    if (sample) { sample(); return; }
    const r = felt.current?.getBoundingClientRect();
    if (!r) return;
    haptic();
    holding.current = true;
    setPicker(r);
  });
  useEffect(() => {
    if (!picker) return;
    // While the finger is still down after the bar opened: no page scroll, and track it.
    const stop = (e: TouchEvent) => { if (holding.current) e.preventDefault(); };
    const move = (e: PointerEvent) => { if (holding.current) bar.current?.hover(e.clientX, e.clientY); };
    const up = (e: PointerEvent) => {
      if (!holding.current) return;
      holding.current = false;
      bar.current?.release(e.clientX, e.clientY);
    };
    const cancel = () => { holding.current = false; bar.current?.hover(-1, -1); };
    window.addEventListener("touchmove", stop, { passive: false });
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", cancel);
    return () => {
      window.removeEventListener("touchmove", stop);
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", cancel);
    };
  }, [picker]);

  const onFeltClick = () => {
    if (feltHold.fired.current) { feltHold.fired.current = false; return; }
    if (sample) { sample(); return; }
    haptic("tick");
    if (!mine) burst.current?.fire("felt");
    void setReaction(mine ? null : "felt");
  };

  const onPicked = (r: Reaction) => {
    setPicker(null);
    holding.current = false;
    if (r === mine) void setReaction(null);
    else { burst.current?.fire(r); void setReaction(r); }
  };

  const openAuthor = () => {
    if (sample) { sample(); return; }
    if (!detail && hasStory(alias) && !post.isMine) navigate({ name: "stories", alias });
    else openProfile(alias, post.isMine);
  };

  const letter = !!post.letterTo?.trim();
  const summary = reactionSummary(post, 3);
  const likeText = post.likeCount > 0 ? count(post.likeCount) : "";
  const feltText = detail
    ? (summary ? `${summary}  ` : "") + (post.likeCount > 0 ? S("detail_felt_count", count(post.likeCount)) : S("cd_felt"))
    : summary ? (likeText ? `${summary} ${likeText}` : summary) : likeText;
  const responsesText = detail ? plural("detail_responses", post.commentCount, count(post.commentCount))
    : post.commentCount > 0 ? count(post.commentCount) : "";
  const score = post.riskScore;
  const scoreTone = score == null ? "" : score >= 85 ? "brick" : score >= 60 ? "amber" : "sage";

  const menuOpen = menuAnchor ? menuAnchor.open : menu;
  const closeMenu = menuAnchor ? menuAnchor.onClose : () => setMenu(false);

  return (
    <article ref={card} className={cx("pc", detail && "pc-detail", letter && "pc-letter", sample && "pc-sample")}
             aria-label={S("cd_card", alias, when)} onClick={onCardClick}
             onPointerDown={(e) => { if (!(e.target as Element).closest("button, a, input")) copyHold.down(e); }}
             onPointerMove={copyHold.move} onPointerUp={copyHold.up} onPointerCancel={copyHold.up}
             onContextMenu={(e) => e.preventDefault()}>
      {letter ? <span className="pc-airmail" aria-hidden /> : null}
      <div className="pc-body">
        <div className="pc-head">
          <button type="button" className="pc-avatar" onClick={openAuthor} aria-label={S("cd_author", `@${alias}`)}>
            <RingAvatar alias={alias} avatar={post.author.avatar} size={36} />
          </button>
          <div className="pc-who">
            <button type="button" className="pc-alias t-alias" onClick={() => (sample ? sample() : openProfile(alias, post.isMine))}>@{alias}</button>
            <span className="pc-time t-meta">{post.isMine ? `${when} · ${S("feed_you")}` : when}</span>
          </div>
          {post.mood && MOOD_EMOJI[post.mood] ? (
            <span className="pc-mood t-meta-small">{`${MOOD_EMOJI[post.mood]}  ${moodLabel(post.mood)}`}</span>
          ) : null}
        </div>

        {post.pendingReview ? (
          <p className="pc-pending t-meta" role="status">
            <span className="pc-pending-tag t-meta-small">Waiting for a safety check</span>
            Only you can see this for now. It goes out to everyone once it's checked — usually within minutes.
          </p>
        ) : null}

        {letter ? (
          <p className={cx("pc-letter-to", hidden && "blurred")}><Icon name="mail_open" size={16} />{S("letter_to", post.letterTo!.trim())}</p>
        ) : null}
        {post.chapter?.title ? <p className="pc-chapter t-meta">{post.chapter.title}</p> : null}

        <div className="pc-text-frame">
          <p className={cx("pc-text", post.gif && !detail && "two", hidden && "blurred")}
             aria-label={hidden ? S("cd_sensitive") : undefined}>{post.text?.trim()}</p>
          {hidden ? (
            <button type="button" className="pc-sensitive t-meta-small" onClick={() => (sample ? sample() : reveal())}>
              <Icon name="eye_off" size={16} />{S("sensitive_cover")}
            </button>
          ) : null}
        </div>

        {post.gif ? <GifView gif={post.gif} className={cx("pc-gif", hidden && "blurred")} /> : null}
        {post.sensitive && post.isMine ? <p className="pc-sens-note t-meta-small"><Icon name="eye_off" size={14} />{S("sensitive_mine")}</p> : null}
        {post.poll ? (
          <div className={cx(hidden && "blurred")}>
            <PollView post={post} disabled={hidden || voting} onVote={(i) => void vote(i)} />
          </div>
        ) : null}

        <div className="pc-foot">
          <button ref={felt} type="button" className={cx("pc-ghost pc-felt t-meta", mine && "on")}
                  onPointerDown={feltHold.down} onPointerMove={(e) => { if (!picker) feltHold.move(e); }}
                  onPointerUp={feltHold.up} onPointerCancel={feltHold.up} onClick={onFeltClick}
                  onContextMenu={(e) => e.preventDefault()}
                  aria-label={`${mine ? S("cd_reacted", reactionLabel(mine)) : S("cd_felt")}${post.likeCount > 0 ? `, ${post.likeCount}` : ""}. ${S("cd_react_hint")}`}>
            {!mine || mine === "felt" ? <Icon name={mine ? "heart_filled" : "heart"} size={18} /> : null}
            {feltText ? <span>{feltText}</span> : null}
          </button>
          <button type="button" className="pc-ghost pc-responses t-meta"
                  onClick={() => (sample ? sample() : actions.onOpen?.(post, true))}
                  aria-label={post.commentsEnabled ? S("cd_responses") : S("cd_responses_closed")}>
            <Icon name={post.commentsEnabled ? "message" : "message_off"} size={18} />
            {responsesText ? <span>{responsesText}</span> : null}
          </button>
          <span className="grow" />
          {score != null ? (
            <button type="button" className={cx("pc-score t-meta-small", scoreTone)} aria-label={S("cd_score", score)}
                    onClick={() => toast(S(score >= 85 ? "score_explain_blurred" : "score_explain", score, 85))}>
              <Icon name="shield" size={12} />{score}
            </button>
          ) : null}
          {!detail ? (
            <button ref={more} type="button" className="pc-more" onClick={() => (sample ? sample() : setMenu(true))}
                    aria-label={S(post.isMine ? "cd_delete_menu" : "cd_report_menu")}>
              <Icon name={post.isMine ? "trash" : "flag"} size={18} />
            </button>
          ) : null}
        </div>
      </div>
      <BurstLayer ref={burst} />

      {picker ? <ReactionBar ref={bar} anchor={picker} mine={mine} onPick={onPicked}
                             onClose={() => { setPicker(null); holding.current = false; }} /> : null}
      {!sample ? <PostMenu post={post} actions={actions} anchor={menuAnchor?.ref ?? more} open={menuOpen} onClose={closeMenu} /> : null}
    </article>
  );
}

/* ── Confirms → ConfessDialog ───────────────────────────────────────────── */

/** A confirm (Android MaterialAlertDialog / ConfessDialog): the primary button runs `onConfirm`. */
export function Confirm({ title, body, confirm, danger, icon, onConfirm, onClose, open = true }:
  { title: string; body: string; confirm: string; danger?: boolean; icon?: IconName; open?: boolean;
    onConfirm: () => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (open) { setBusy(false); setError(null); } }, [open]);
  return (
    <ConfessDialog open={open} icon={icon ?? (danger ? "trash" : undefined)} title={title} body={body} primary={confirm} danger={danger}
                   busy={busy} error={error} onClose={onClose}
                   onPrimary={async () => {
                     setBusy(true);
                     setError(null);
                     try { await onConfirm(); } catch (e) { setError(errorMessage(e)); setBusy(false); }
                   }} />
  );
}

/** BlockFlow.confirm: "Block @x?" → block → toast → onBlocked. */
export function BlockConfirm({ alias, open, onClose, onBlocked }: { alias: string; open: boolean; onClose: () => void; onBlocked?: () => void }) {
  const toast = useToast();
  return (
    <Confirm open={open} icon="ban" danger title={S("block_title", alias)} body={S("block_body")} confirm={S("block_confirm")} onClose={onClose}
             onConfirm={async () => {
               haptic();
               await P.blockUser(alias);
               toast(S("blocked_toast", alias));
               onClose();
               onBlocked?.();
             }} />
  );
}

/* ── Report sheet (sheet_report.xml) ────────────────────────────────────── */

export type ReportKind = { kind: "post"; id: string } | { kind: "comment"; id: string } | { kind: "user"; alias: string };

const REASONS: { code: string; label: string }[] = [
  { code: "hate_or_harassment", label: "report_hate" },
  { code: "reveals_identity", label: "report_personal" },
  { code: "threat_or_self_harm", label: "report_threat" },
  { code: "sexual_or_minor", label: "report_sexual_minor" },
  { code: "spam", label: "report_spam" },
  { code: "other", label: "report_other" },
];

export function ReportSheet({ target, onDone, onClose, open = true }:
  { target: ReportKind; onDone: () => void; onClose: () => void; open?: boolean }) {
  const [reason, setReason] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  useEffect(() => { if (open) { setReason(null); setBusy(false); } }, [open]);
  const copy = target.kind === "post"
    ? { title: S("report_title"), sub: S("report_subtitle"), send: S("report_send"), done: S("reported") }
    : target.kind === "comment"
      ? { title: S("report_response_title"), sub: S("report_response_subtitle"), send: S("report_send"), done: S("reported_response") }
      : { title: S("report_user_title"), sub: S("report_user_subtitle"), send: S("report_user_send"), done: S("reported_user") };

  async function send() {
    if (!reason || busy) return;
    setBusy(true);
    try {
      if (target.kind === "post") await P.reportPost(target.id, reason);
      else if (target.kind === "comment") await P.reportComment(target.id, reason);
      else await P.reportUser(target.alias, reason);
      haptic();
      toast(copy.done);
      onDone();
    } catch (e) {
      toast(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <KSheet open={open} onClose={onClose} label={copy.title} cancelable={!busy}>
      <div className="k-sheet-scroll report">
        <span className="report-tile"><Icon name="flag" size={22} /></span>
        <h2 className="t-headline-small report-title">{copy.title}</h2>
        <p className="t-body-medium report-sub">{copy.sub}</p>
        <div className="report-reasons" role="radiogroup" aria-label={copy.title}>
          {REASONS.map((r) => (
            <button key={r.code} type="button" role="radio" aria-checked={reason === r.code}
                    className={cx("report-reason t-body", reason === r.code && "on")}
                    onClick={() => { haptic("tick"); setReason(r.code); }}>
              <span className="report-radio" aria-hidden />{S(r.label)}
            </button>
          ))}
        </div>
        <Btn kind="danger" block icon="flag" busy={busy} disabled={!reason} className="report-send" onClick={() => void send()}>{copy.send}</Btn>
      </div>
    </KSheet>
  );
}

/* ── The flag / bin menu (PostMenu.show) ────────────────────────────────── */

function PostMenu({ post, actions, anchor, open, onClose }:
  { post: Post; actions: CardActions; anchor: RefObject<HTMLElement | null>; open: boolean; onClose: () => void }) {
  const [step, setStep] = useState<null | "report" | "reportUser" | "block" | "delete">(null);
  const toast = useToast();
  const alias = post.author.alias;
  const items: MenuItem[] = post.isMine
    ? [{ icon: "trash", label: S("menu_delete"), danger: true, onClick: () => setStep("delete") }]
    : [
        { icon: "flag", label: S("menu_report_post"), onClick: () => setStep("report") },
        { icon: "user", label: S("menu_report_user", alias), onClick: () => setStep("reportUser") },
        { icon: "ban", label: S("menu_block_user", alias), danger: true, onClick: () => setStep("block") },
      ];
  const done = () => setStep(null);
  return (
    <>
      <PopMenu anchor={anchor} open={open} onClose={onClose} items={items} />
      <ReportSheet open={step === "report"} target={{ kind: "post", id: post.postId }} onClose={done}
                   onDone={() => { done(); actions.onRemove(post.postId); }} />
      <ReportSheet open={step === "reportUser"} target={{ kind: "user", alias }} onClose={done} onDone={done} />
      <BlockConfirm open={step === "block"} alias={alias} onClose={done} onBlocked={() => actions.onBlocked(alias)} />
      <Confirm open={step === "delete"} danger title={S("delete_title")} body={S("delete_body")} confirm={S("action_delete")} onClose={done}
               onConfirm={async () => { await P.deletePost(post.postId); toast(S("deleted")); done(); actions.onRemove(post.postId); }} />
    </>
  );
}

/* ── The old bottom sheet (screens not yet redrawn use it) ──────────────── */

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

