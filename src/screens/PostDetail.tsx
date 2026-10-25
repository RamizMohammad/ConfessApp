import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import { ago, length, moderationReason } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { cached, cardActions, emit, subscribe } from "../lib/postStore.ts";
import { back } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import { LIMITS, type Comment, type Gif, type Post } from "../lib/types.ts";
import { Aura } from "../components/Aura.tsx";
import { GifPicker } from "../components/GifPicker.tsx";
import { Close, Flag, GifIcon, More, Send, Trash } from "../components/icons.tsx";
import { Confirm, GifView, PostCard, ReportSheet, Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, Notice, TopBar } from "../components/ui.tsx";

export function PostDetail({ id }: { id: string }) {
  const [post, setPost] = useState<Post | null>(cached(id) ?? null);
  const [gone, setGone] = useState(false);
  const [error, setError] = useState<string>();
  const [list, setList] = useState<Comment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    window.scrollTo(0, 0);
    let stop = false;
    P.getPost(id).then((p) => { if (!stop) { setPost(p); emit({ type: "update", post: p }); } })
      .catch((e) => { if (!stop) (e instanceof ApiError && e.status === 404 ? setGone(true) : setError(errorMessage(e))); });
    return () => { stop = true; };
  }, [id]);

  const loadComments = useCallback(async (c: string | null) => {
    setLoading(true);
    try {
      const r = await P.comments(id, c);
      setList((cur) => (c ? [...cur, ...r.comments] : r.comments));
      setCursor(r.nextCursor);
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void loadComments(null); }, [loadComments]);

  useEffect(() => subscribe((e) => {
    if (e.type === "update" && e.post.postId === id) setPost(e.post);
    if (e.type === "remove" && e.postId === id) back();
    if (e.type === "blocked") {
      if (post && e.alias.toLowerCase() === post.author.alias.toLowerCase()) back();
      setList((cur) => cur.filter((c) => c.author.alias.toLowerCase() !== e.alias.toLowerCase()));
    }
  }), [id, post]);

  function added(c: Comment) {
    setList((cur) => [...cur, c]);
    if (post) emit({ type: "update", post: { ...post, commentCount: post.commentCount + 1 } });
    window.setTimeout(() => window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" }), 50);
  }

  function removed(c: Comment) {
    setList((cur) => cur.filter((x) => x.commentId !== c.commentId));
    if (post) emit({ type: "update", post: { ...post, commentCount: Math.max(0, post.commentCount - 1) } });
  }

  if (gone) {
    return (
      <main className="screen">
        <TopBar onBack={back} />
        <div className="empty grow">
          <h2 className="title">This confession is gone.</h2>
          <p className="small">Its author deleted it, or it was hidden after reports.</p>
          <Button variant="outline" onClick={back}>Back</Button>
        </div>
      </main>
    );
  }

  return (
    <main className="screen detail-screen">
      <TopBar onBack={back} />
      {!post ? <div className="feed-skeleton" aria-busy="true"><div /></div> : (
        <div className="stack">
          <PostCard post={post} detail actions={cardActions()} />
          <div className="row spread responses-head">
            <span className="tag">Responses ({post.commentCount})</span>
            <span className="meta">Oldest first</span>
          </div>
          <Notice>{error}</Notice>
          {!loading && !list.length && post.commentsEnabled ? (
            <div className="empty small-empty">
              <p className="title">No responses yet.</p>
              <p className="small">Be the first gentle voice.</p>
            </div>
          ) : null}
          <div className="responses">
            {list.map((c) => <Response key={c.commentId} c={c} onPost={post} onRemoved={removed} />)}
          </div>
          {loading ? <span className="spinner center-self" aria-label="Loading responses" /> : null}
          {cursor && !loading ? <Button variant="ghost" onClick={() => void loadComments(cursor)}>More responses</Button> : null}
          {!post.commentsEnabled ? <p className="meta center-text">The author closed responses on this one.</p> : null}
        </div>
      )}
      {post?.commentsEnabled ? <ResponseComposer post={post} onAdded={added} onClosed={() => emit({ type: "update", post: { ...post, commentsEnabled: false } })} /> : null}
    </main>
  );
}

function Response({ c, onPost, onRemoved }: { c: Comment; onPost: Post; onRemoved: (c: Comment) => void }) {
  const [menu, setMenu] = useState<"menu" | "report" | "delete" | null>(null);
  const toast = useToast();
  const canDelete = c.isMine || onPost.isMine;
  return (
    <div className="response">
      <Aura value={c.author.avatar} size={30} />
      <div className="grow stack-sm">
        <div className="row resp-head">
          <span className="alias">@{c.author.alias}</span>
          {c.isPostAuthor ? <span className="author-tag">Author</span> : null}
          <span className="meta">{ago(c.createdAt)}</span>
        </div>
        {c.text ? <p className="resp-text">{c.text}</p> : null}
        {c.gif ? <GifView gif={c.gif} compact /> : null}
      </div>
      <button className="icon-btn small" aria-label={canDelete ? "Response options" : "Report response"} onClick={() => setMenu("menu")}><More /></button>
      {menu === "menu" ? (
        <Sheet title="Response options" onClose={() => setMenu(null)}>
          <div className="menu-list">
            {canDelete ? <button className="menu-item danger" onClick={() => setMenu("delete")}><Trash /> Delete response</button> : null}
            {!c.isMine ? <button className="menu-item" onClick={() => setMenu("report")}><Flag /> Report response</button> : null}
            <button className="menu-item" onClick={() => setMenu(null)}><Close /> Cancel</button>
          </div>
        </Sheet>
      ) : null}
      {menu === "report" ? <ReportSheet target={{ kind: "comment", id: c.commentId }} onClose={() => setMenu(null)} onDone={() => { setMenu(null); onRemoved(c); }} /> : null}
      {menu === "delete" ? (
        <Confirm title="Delete this response?" body="It disappears for everyone. This can't be undone." confirm="Delete" danger
                 onClose={() => setMenu(null)}
                 onConfirm={async () => { await P.deleteComment(c.commentId); toast("Response deleted."); setMenu(null); onRemoved(c); }} />
      ) : null}
    </div>
  );
}

function ResponseComposer({ post, onAdded, onClosed }: { post: Post; onAdded: (c: Comment) => void; onClosed: () => void }) {
  const [text, setText] = useState("");
  const [gif, setGif] = useState<Gif | null>(null);
  const [picker, setPicker] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const ref = useRef<HTMLTextAreaElement>(null);
  const n = length(text.trim());
  const over = n > LIMITS.comment;
  const canSend = (n > 0 || !!gif) && !over && !busy;

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [text]);

  async function send() {
    if (!canSend) return;
    setBusy(true);
    setError(undefined);
    try {
      const c = await P.addComment(post.postId, text.trim(), gif);
      setText("");
      setGif(null);
      onAdded(c);
    } catch (e) {
      if (e instanceof ApiError && e.code === "COMMENTS_DISABLED") onClosed();
      else if (e instanceof ApiError && e.status === 451) setError(`This response can't be posted. ${moderationReason(e.message)}`);
      else if (e instanceof ApiError && e.code === "MODERATION_UNAVAILABLE") setError("We couldn't check your words just now. Nothing was sent — try again.");
      else setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="composer">
      <Notice>{error}</Notice>
      {gif ? (
        <div className="attached">
          <GifView gif={gif} compact />
          <button className="icon-btn small" aria-label="Remove the GIF" onClick={() => setGif(null)}><Close /></button>
        </div>
      ) : null}
      <div className="composer-row">
        <button className="icon-btn" aria-label="Add a GIF" onClick={() => setPicker(true)}><GifIcon /></button>
        <textarea ref={ref} className="input composer-input" rows={1} placeholder="Respond with kindness…" value={text}
                  aria-label="Your response" onChange={(e) => setText(e.target.value)} />
        <button className="send-btn" aria-label="Send response" disabled={!canSend} onClick={() => void send()}>
          {busy ? <span className="spinner" /> : <Send />}
        </button>
      </div>
      {n > LIMITS.comment - 80 ? <span className={`meta counter${over ? " error-text" : ""}`}>{n} / {LIMITS.comment}</span> : null}
      {picker ? <GifPicker onClose={() => setPicker(false)} onPick={(g) => { setGif(g); setPicker(false); }} /> : null}
    </div>
  );
}
