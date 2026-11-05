/**
 * PostDetailActivity (activity_post_detail + DetailAdapter): top bar (back · ✉ LETTER · flag / bin),
 * the big card, "• RESPONSES (n) … OLDEST FIRST", the responses (item_response), the footer
 * (loading · retry · kindness line · closed), and the response bar: my aura, the pill with its
 * GIF button, the amber send circle; the moderation notice above it; the closed bar instead.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import { resolveAura } from "../lib/auras.ts";
import { length, moderationReason, relative } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { cached, cardActions, emit, subscribe } from "../lib/postStore.ts";
import { back, currentRoute, takeJump } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { S } from "../lib/strings.ts";
import { LIMITS, type Comment, type Gif, type Post } from "../lib/types.ts";
import { Aura } from "../components/Aura.tsx";
import { GifPicker } from "../components/GifPicker.tsx";
import { BlockConfirm, Confirm, GifView, PostCard, ReportSheet, openProfile } from "../components/post.tsx";
import { RingAvatar } from "../components/stories.tsx";
import { useToast } from "../components/toast.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, haptic, IconBtn, PopMenu, Spinner, type MenuItem } from "../ui/kit.tsx";

const COUNTER_FROM = 400;
type Footer = "loading" | "error" | "end";

function DetailSkeleton() {
  return (
    <div className="fsk" aria-hidden>
      <div className="fsk-head"><i className="fsk-circle" /><span><i className="fsk-b w110 h10" /><i className="fsk-b w56 h8 mt8" /></span></div>
      <i className="fsk-b h14 mt16" /><i className="fsk-b h14 mt10" /><i className="fsk-b w180 h14 mt10" />
      <span className="fsk-foot"><i className="fsk-b w44 h10" /><i className="fsk-b w44 h10" /></span>
    </div>
  );
}

export function PostDetail({ id }: { id: string }) {
  const { me } = useSession();
  const toast = useToast();
  const [post, setPost] = useState<Post | null>(cached(id) ?? null);
  const [screen, setScreen] = useState<"loading" | "content" | "gone" | "error">(cached(id) ? "content" : "loading");
  const [error, setError] = useState<{ text: string; offline: boolean }>();
  const [list, setList] = useState<Comment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [footer, setFooter] = useState<Footer>("loading");
  const [menu, setMenu] = useState(false);
  const more = useRef<HTMLButtonElement>(null);
  const header = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  const loadingMore = useRef(false);
  const jump = useRef(takeJump(id));
  const isTop = () => { const r = currentRoute(); return r.name === "post" && r.id === id; };

  const loadPost = useCallback(() => {
    let stop = false;
    if (!cached(id)) setScreen("loading");
    P.getPost(id).then((p) => { if (!stop) { setPost(p); setScreen("content"); emit({ type: "update", post: p }); } })
      .catch((e) => {
        if (stop) return;
        if (e instanceof ApiError && e.status === 404) { setScreen("gone"); return; }
        if (!cached(id)) {
          setError({ text: errorMessage(e), offline: navigator.onLine === false });
          setScreen("error");
        }
      });
    return () => { stop = true; };
  }, [id]);
  useEffect(loadPost, [loadPost]);

  const loadComments = useCallback(async (c: string | null) => {
    if (loadingMore.current) return;
    loadingMore.current = true;
    setFooter("loading");
    try {
      const r = await P.comments(id, c);
      setList((cur) => (c ? [...cur, ...r.comments.filter((x) => !cur.some((y) => y.commentId === x.commentId))] : r.comments));
      setCursor(r.nextCursor);
      setFooter(r.nextCursor ? "loading" : "end");
    } catch (e) {
      setFooter(e instanceof ApiError && e.status === 404 ? "end" : "error");
    } finally {
      loadingMore.current = false;
    }
  }, [id]);
  useEffect(() => { void loadComments(null); }, [loadComments]);

  // PREFETCH: more responses as the end comes into view.
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !cursor || footer !== "loading") return;
    const io = new IntersectionObserver((e) => { if (e.some((x) => x.isIntersecting)) void loadComments(cursor); }, { rootMargin: "600px" });
    io.observe(el);
    return () => io.disconnect();
  }, [cursor, footer, loadComments]);

  useEffect(() => subscribe((e) => {
    if (e.type === "update" && e.post.postId === id) setPost(e.post);
    if (e.type === "remove" && e.postId === id) { if (isTop()) back(); else setScreen("gone"); }
    if (e.type === "blocked") {
      setPost((p) => {
        if (p && e.alias.toLowerCase() === p.author.alias.toLowerCase()) { if (isTop()) back(); }
        return p;
      });
      setList((cur) => cur.filter((c) => c.author.alias.toLowerCase() !== e.alias.toLowerCase()));
    }
  }), [id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Opened from the responses button: the header at the top, keyboard up.
  const jumpToResponses = useCallback((focus: boolean) => {
    const h = header.current;
    if (h) window.scrollTo({ top: Math.max(0, h.getBoundingClientRect().top + window.scrollY - 64), behavior: "smooth" });
    if (focus) input.current?.focus({ preventScroll: true });
  }, []);
  useLayoutEffect(() => {
    if (screen === "content" && post && jump.current) {
      jump.current = false;
      window.setTimeout(() => jumpToResponses(post.commentsEnabled), 60);
    }
  }, [screen, post, jumpToResponses]);
  useEffect(() => {
    const on = (e: Event) => { if ((e as CustomEvent).detail === id) jumpToResponses(!!post?.commentsEnabled); };
    window.addEventListener("confess:jump-responses", on);
    return () => window.removeEventListener("confess:jump-responses", on);
  }, [id, post, jumpToResponses]);

  function added(c: Comment) {
    setList((cur) => [...cur, c]);
    if (post) emit({ type: "update", post: { ...post, commentCount: post.commentCount + 1 } });
    window.setTimeout(() => window.scrollTo({ top: document.documentElement.scrollHeight, behavior: "smooth" }), 250);
  }
  function removed(c: Comment) {
    setList((cur) => cur.filter((x) => x.commentId !== c.commentId));
    if (post) emit({ type: "update", post: { ...post, commentCount: Math.max(0, post.commentCount - 1) } });
  }

  const content = screen === "content" && !!post;
  const actions = { ...cardActions((_, r) => { if (r) jumpToResponses(!!post?.commentsEnabled); }) };

  const state = (icon: IconName, title: string, body: string, action: string, onAction: () => void) => (
    <div className="feed-state detail-state">
      <span className="icon-tile-56"><Icon name={icon} size={24} /></span>
      <h2 className="t-headline-small feed-state-title">{title}</h2>
      <p className="t-body-medium feed-state-body">{body}</p>
      <Btn kind="outlined" className="feed-state-action" onClick={onAction}>{action}</Btn>
    </div>
  );

  return (
    <main className="detail-a" aria-label={S("detail_title")}>
      <header className="detail-top">
        <IconBtn icon="arrow_left" label={S("action_back")} onClick={back} />
        <span className="detail-title t-meta"><Icon name="mail_open" size={24} />{S("detail_title")}</span>
        <IconBtn ref={more} icon={post?.isMine ? "trash" : "flag"} label={S(post?.isMine ? "cd_delete_menu" : "cd_report_menu")}
                 className="detail-more" disabled={!content} onClick={() => setMenu(true)} />
      </header>

      <div className="detail-list">
        {screen === "loading" ? <div className="detail-skeletons"><DetailSkeleton /><DetailSkeleton /></div> : null}
        {screen === "gone" ? state("mail_open", S("detail_gone_title"), S("detail_gone_body"), S("detail_back_to_feed"), back) : null}
        {screen === "error" ? state(error?.offline ? "wifi_off" : "alert", S("feed_error_title"), error?.text ?? "", S("action_retry"), loadPost) : null}
        {content ? (
          <>
            <PostCard post={post} detail actions={actions}
                      menuAnchor={{ ref: more, open: menu, onClose: () => setMenu(false) }} />
            <div className="resp-header" ref={header}>
              <span className="resp-header-title t-meta-small"><i className="dot-amber" />{S("detail_responses_header", String(post.commentCount))}</span>
              <span className="t-meta-small resp-header-order">{S("detail_responses_order")}</span>
            </div>
            {list.map((c) => <Response key={c.commentId} c={c} post={post} onRemoved={removed} />)}
            <div ref={sentinel} className="resp-footer">
              {footer === "loading" ? <Spinner size={24} /> : null}
              {footer === "error" ? (
                <div className="resp-footer-error">
                  <span className="t-body-medium">{S("responses_more_failed")}</span>
                  <Btn kind="link" icon="refresh" onClick={() => void loadComments(cursor)}>{S("action_retry")}</Btn>
                </div>
              ) : null}
              {footer === "end" ? (
                <div className="resp-footer-end">
                  <span className="resp-end-title">
                    {!post.commentsEnabled ? S(list.length === 0 ? "responses_empty_title" : "responses_kindness")
                      : list.length === 0 ? S("responses_empty_title") : S("responses_kindness")}
                  </span>
                  {!post.commentsEnabled ? <span className="t-meta resp-end-body">{S("responses_closed_end")}</span>
                    : list.length === 0 ? <span className="t-meta resp-end-body">{S("responses_empty_body")}</span> : null}
                </div>
              ) : null}
            </div>
          </>
        ) : null}
      </div>

      {content ? (
        post.commentsEnabled
          ? <ResponseBar post={post} myAvatar={me?.avatar} inputRef={input} onAdded={added}
                         onClosed={() => emit({ type: "update", post: { ...post, commentsEnabled: false } })}
                         say={(t) => toast(t)} />
          : <div className="detail-bottom"><p className="closed-bar t-meta"><Icon name="lock_small" size={16} />{S("responses_closed_bar")}</p></div>
      ) : null}
    </main>
  );
}

/** item_response: dark card with a stripe in the writer's aura colour. */
function Response({ c, post, onRemoved }: { c: Comment; post: Post; onRemoved: (c: Comment) => void }) {
  const [menu, setMenu] = useState(false);
  const [step, setStep] = useState<null | "report" | "delete" | "block">(null);
  const toast = useToast();
  const more = useRef<HTMLButtonElement>(null);
  const stripe = useRef<HTMLSpanElement>(null);
  const canDelete = c.isMine || post.isMine;
  const alias = c.author.alias;
  useLayoutEffect(() => { stripe.current?.style.setProperty("--stripe", `${resolveAura(c.author.avatar).glow}CC`); }, [c.author.avatar]);
  const hold = useRef<number | undefined>(undefined);
  const items: MenuItem[] = [
    ...(canDelete ? [{ icon: "trash" as IconName, label: S("response_delete"), danger: true, onClick: () => setStep("delete") }] : []),
    ...(!c.isMine ? [{ icon: "flag" as IconName, label: S("menu_report_response"), onClick: () => setStep("report") }] : []),
    ...(!c.isMine && alias ? [{ icon: "ban" as IconName, label: S("menu_block_user", alias), danger: true, onClick: () => setStep("block") }] : []),
  ];
  const when = relative(c.createdAt);
  return (
    <div className="resp" onPointerDown={(e) => {
      if ((e.target as Element).closest("button")) return;
      hold.current = window.setTimeout(() => { haptic(); setMenu(true); }, 500);
    }} onPointerUp={() => window.clearTimeout(hold.current)} onPointerCancel={() => window.clearTimeout(hold.current)}
       onPointerMove={() => window.clearTimeout(hold.current)} onContextMenu={(e) => e.preventDefault()}>
      <span className="resp-stripe" ref={stripe} />
      <div className="resp-body">
        <div className="resp-head">
          <button type="button" className="resp-avatar" onClick={() => openProfile(alias, c.isMine)} aria-label={S("cd_author", `@${alias}`)}>
            <RingAvatar alias={alias} avatar={c.author.avatar} size={28} />
          </button>
          <button type="button" className="resp-alias t-alias" onClick={() => openProfile(alias, c.isMine)}>@{alias}</button>
          {c.isPostAuthor ? <span className="resp-author t-meta-small">{S("response_author")}</span> : null}
          <span className="resp-time t-meta">{c.isMine ? `${when} · ${S("feed_you")}` : when}</span>
          <button ref={more} type="button" className="resp-more" aria-label={S("action_more")} onClick={() => setMenu(true)}>
            <Icon name="more" size={18} />
          </button>
        </div>
        {c.text?.trim() ? <p className="resp-text t-body">{c.text}</p> : null}
        {c.gif ? <GifView gif={c.gif} thumb maxRatio={1.4} className="resp-gif" /> : null}
      </div>
      <PopMenu anchor={more} open={menu} onClose={() => setMenu(false)} items={items} />
      <ReportSheet open={step === "report"} target={{ kind: "comment", id: c.commentId }} onClose={() => setStep(null)}
                   onDone={() => { setStep(null); onRemoved(c); }} />
      <BlockConfirm open={step === "block"} alias={alias} onClose={() => setStep(null)}
                    onBlocked={() => emit({ type: "blocked", alias })} />
      <Confirm open={step === "delete"} danger title={S("response_delete_title")} body={S("response_delete_body")} confirm={S("action_delete")}
               onClose={() => setStep(null)}
               onConfirm={async () => { await P.deleteComment(c.commentId); toast(S("response_deleted")); setStep(null); onRemoved(c); }} />
    </div>
  );
}

/** The response bar (inputBar + notice + counter + gifAttach). */
function ResponseBar({ post, myAvatar, inputRef, onAdded, onClosed, say }:
  { post: Post; myAvatar?: string; inputRef: React.RefObject<HTMLTextAreaElement | null>; onAdded: (c: Comment) => void;
    onClosed: () => void; say: (t: string) => void }) {
  const [text, setText] = useState("");
  const [gif, setGif] = useState<Gif | null>(null);
  const [picker, setPicker] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; soft: boolean } | null>(null);
  const n = length(text.trim());
  const canSend = !busy && (n > 0 || !!gif) && n <= LIMITS.comment;

  // Grows with the words, up to five lines.
  useLayoutEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.setProperty("height", "auto");
    el.style.setProperty("height", `${Math.min(el.scrollHeight, 10 + 22 * 5 + 10)}px`);
  }, [text, inputRef]);

  async function send() {
    if (!canSend) return;
    setNotice(null);
    setBusy(true);
    try {
      const c = await P.addComment(post.postId, text.trim(), gif);
      haptic();
      setText("");
      setGif(null);
      onAdded(c);
    } catch (e) {
      haptic();
      if (e instanceof ApiError && e.code === "COMMENTS_DISABLED") { say(errorMessage(e)); onClosed(); }
      else if (e instanceof ApiError && e.status === 451) {
        setNotice({ soft: false, text: /empty/i.test(e.message) ? S("compose_empty_after_clean") : S("response_flagged", moderationReason(e.message)) });
      } else if (e instanceof ApiError && e.code === "MODERATION_UNAVAILABLE") setNotice({ soft: true, text: S("response_unavailable") });
      else say(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="detail-bottom">
      {notice ? (
        <div className={`detail-notice${notice.soft ? " soft" : ""}`} role="alert">
          <Icon name={notice.soft ? "shield" : "alert"} size={18} />
          <span className="t-body-medium">{notice.text}</span>
          <button type="button" className="detail-notice-close" aria-label={S("cd_dismiss")} onClick={() => setNotice(null)}><Icon name="close" size={18} /></button>
        </div>
      ) : null}
      {n >= COUNTER_FROM ? <span className={`detail-counter t-meta-small${n > LIMITS.comment ? " over" : ""}`}>{S("response_counter", n, LIMITS.comment)}</span> : null}
      {gif ? (
        <div className="detail-gif">
          <GifView gif={gif} thumb maxRatio={1.4} className="detail-gif-thumb" onClick={() => setPicker(true)} />
          <span className="t-meta">{S("response_gif_attached")}</span>
          <button type="button" className="detail-gif-remove" aria-label={S("cd_remove_gif")} onClick={() => { haptic("tick"); setGif(null); }}>
            <Icon name="close" size={24} />
          </button>
        </div>
      ) : null}
      <div className="input-bar">
        <span className="input-bar-avatar"><Aura value={myAvatar} size={36} /></span>
        <span className="input-pill-wrap">
          <textarea ref={inputRef} className="input-pill t-body" rows={1} value={text} placeholder={S("response_hint")}
                    aria-label={S("response_hint")} disabled={busy} onChange={(e) => setText(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) { e.preventDefault(); void send(); } }} />
          <button type="button" className={`input-gif${gif ? " on" : ""}`} aria-label={S("cd_pick_gif")}
                  onClick={() => { haptic("tick"); setPicker(true); }}><Icon name="gif" size={24} /></button>
        </span>
        <button type="button" className={`send-circle${busy ? " busy" : ""}`} aria-label={S("cd_send_response")} disabled={!canSend}
                onClick={() => void send()}>
          {busy ? <Spinner size={20} /> : <Icon name="arrow_up" size={24} />}
        </button>
      </div>
      <GifPicker open={picker} onClose={() => setPicker(false)} onPick={setGif} />
    </div>
  );
}
