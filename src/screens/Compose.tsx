import { useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import { deleteDraft, getDraft, newDraftId, saveDraft, type Draft } from "../lib/drafts.ts";
import { length, moderationReason } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { emit } from "../lib/postStore.ts";
import { back, navigate } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import { LIMITS, MOODS, type Gif, type Mood } from "../lib/types.ts";
import { GifPicker } from "../components/GifPicker.tsx";
import { Close, GifIcon, Inbox } from "../components/icons.tsx";
import { GifView } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, Notice } from "../components/ui.tsx";
import { GuidelinesList } from "./Onboarding.tsx";
import { Sheet } from "../components/post.tsx";

type Duration = 24 | 72 | 168;

export function Compose({ draftId }: { draftId?: string }) {
  const toast = useToast();
  const id = useRef(draftId ?? newDraftId());
  const [text, setText] = useState("");
  const [commentsEnabled, setCommentsEnabled] = useState(true);
  const [mood, setMood] = useState<Mood | null>(null);
  const [letter, setLetter] = useState<string | null>(null);
  const [poll, setPoll] = useState<{ options: string[]; durationHours: Duration } | null>(null);
  const [gif, setGif] = useState<Gif | null>(null);
  const [picker, setPicker] = useState(false);
  const [busy, setBusy] = useState(false);
  const [flag, setFlag] = useState<{ title: string; body: string; guidelines?: boolean } | null>(null);
  const [guidelines, setGuidelines] = useState(false);
  const [loaded, setLoaded] = useState(!draftId);
  const area = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!draftId) {
      area.current?.focus();
      return;
    }
    void getDraft(draftId).then((d) => {
      if (d) {
        setText(d.text);
        setCommentsEnabled(d.commentsEnabled);
        setMood(d.mood);
        setLetter(d.letterTo);
        setPoll(d.poll);
        setGif(d.gif);
        toast("Draft restored");
      }
      setLoaded(true);
    });
  }, [draftId, toast]);

  // Auto-save (encrypted, on this device only): after a pause, when leaving, and when the app goes to the background.
  const hasContent = text.trim().length > 0 || !!gif || !!poll?.options.some((o) => o.trim());
  const latest = useRef<Draft | null>(null);
  const released = useRef(false);
  latest.current = loaded && hasContent
    ? { id: id.current, text, commentsEnabled, mood, letterTo: letter, poll, gif, updatedAt: Date.now() }
    : null;
  const flush = () => (latest.current && !released.current ? saveDraft({ ...latest.current, updatedAt: Date.now() }) : Promise.resolve());
  useEffect(() => {
    if (!loaded || !hasContent) return;
    const t = window.setTimeout(() => void flush(), 600);
    return () => window.clearTimeout(t);
  }, [loaded, hasContent, text, commentsEnabled, mood, letter, poll, gif]);
  useEffect(() => {
    const hide = () => { if (document.visibilityState === "hidden") void flush(); };
    document.addEventListener("visibilitychange", hide);
    return () => { document.removeEventListener("visibilitychange", hide); void flush(); };   // leaving by back-swipe too
  }, []);

  const limit = gif ? LIMITS.gifPost : LIMITS.post;
  const n = length(text.trim());
  const over = n - limit;
  const listen = mood === "listen";
  const pollOk = !poll || new Set(poll.options.map((o) => o.trim().toLowerCase()).filter(Boolean)).size >= 2;
  const letterOk = letter === null || letter.trim().length > 0;
  const ready = n > 0 && over <= 0 && pollOk && letterOk && !busy;

  async function close() {
    if (hasContent) {
      await flush();
      toast("Saved to drafts");
    }
    back();
  }

  async function discard() {
    released.current = true;                  // nothing gets saved on the way out
    await deleteDraft(id.current);
    back();
  }

  async function release() {
    if (!ready) return;
    setFlag(null);
    setBusy(true);
    try {
      const post = await P.createPost({
        text: text.trim(),
        commentsEnabled: listen ? false : commentsEnabled,
        mood,
        letterTo: letter?.trim() || null,
        poll: poll ? { options: poll.options.map((o) => o.trim()).filter(Boolean), durationHours: poll.durationHours } : null,
        gif: gif ? { id: gif.id, width: gif.width, height: gif.height } : null,
      });
      released.current = true;
      await deleteDraft(id.current);
      emit({ type: "created", post });
      toast("Released. Your words are out there now.");
      navigate({ name: "feed" }, true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 451) {
        setFlag({ title: "This can't be posted.", body: moderationReason(e.message), guidelines: true });
      } else if (e instanceof ApiError && e.code === "MODERATION_UNAVAILABLE") {
        setFlag({ title: "We couldn't read it just now.", body: "Every confession is checked before it goes out. Nothing was posted — try again in a moment." });
      } else {
        setFlag({ title: "It didn't go out.", body: errorMessage(e) });
      }
      setBusy(false);
    }
  }

  const today = new Date();
  const label = `${today.getHours() >= 18 || today.getHours() < 5 ? "Tonight" : "Today"} · ${today.toLocaleDateString(undefined, { day: "numeric", month: "short" })}`;

  return (
    <main className="screen compose">
      <div className="topbar">
        <button className="icon-btn" aria-label="Close — keeps a draft" onClick={() => void close()}><Close /></button>
        <span className="tag">Unsent note</span>
        <button className="icon-btn" aria-label="Drafts" onClick={() => navigate({ name: "drafts" })}><Inbox /></button>
      </div>

      <div className="stack grow">
        {!text && !letter && !poll ? (
          <div className="stack-sm">
            <h1 className="headline">Oh so you have something melting you down?</h1>
            <p className="body">Tell us and make your heart lighter.</p>
          </div>
        ) : null}

        <div className="paper compose-paper">
          <span className="meta">{label}</span>
          {letter !== null ? (
            <div className="letter-row">
              <span>To:</span>
              <input className="letter-input" value={letter} maxLength={LIMITS.letterTo} autoFocus
                     placeholder="someone who'll never read this" aria-label="Who is the letter to?"
                     onChange={(e) => setLetter(e.target.value)} />
            </div>
          ) : null}
          <textarea ref={area} className="compose-text" value={text} onChange={(e) => setText(e.target.value)}
                    placeholder="Something melting you, tell us…" aria-label="Your confession" rows={7} />
          {gif ? (
            <div className="attached">
              <GifView gif={gif} compact />
              <button className="icon-btn small" aria-label="Remove the GIF" onClick={() => setGif(null)}><Close /></button>
            </div>
          ) : null}
          <div className="row spread">
            <span className="meta">Only your alias is shown</span>
            <span className={`meta${over > 0 ? " error-text" : ""}`}>{n} / {limit}</span>
          </div>
        </div>
        {over > 0 ? (
          <Notice>{gif ? `With a GIF, a confession can say ${LIMITS.gifPost} characters.` : `${over} characters over the limit.`}</Notice>
        ) : null}

        {poll ? (
          <div className="plate stack-sm">
            <div className="row spread"><span className="tag">Ask them</span>
              <button className="link" onClick={() => setPoll(null)}>Remove poll</button></div>
            <p className="small">2–4 choices · votes are anonymous · results show after voting</p>
            {poll.options.map((o, i) => (
              <div className="row" key={i}>
                <input className="input" value={o} maxLength={LIMITS.pollOption} placeholder={`Choice ${i + 1}`} aria-label={`Choice ${i + 1}`}
                       onChange={(e) => setPoll({ ...poll, options: poll.options.map((x, j) => (j === i ? e.target.value : x)) })} />
                {poll.options.length > 2 ? (
                  <button className="icon-btn" aria-label="Remove choice"
                          onClick={() => setPoll({ ...poll, options: poll.options.filter((_, j) => j !== i) })}><Close /></button>
                ) : null}
              </div>
            ))}
            {poll.options.length < 4 ? <Button variant="ghost" onClick={() => setPoll({ ...poll, options: [...poll.options, ""] })}>Add a choice</Button> : null}
            <div className="chips">
              {([24, 72, 168] as Duration[]).map((h) => (
                <button key={h} className={`chip${poll.durationHours === h ? " on" : ""}`} onClick={() => setPoll({ ...poll, durationHours: h })}>
                  {h === 24 ? "1 day" : h === 72 ? "3 days" : "1 week"}
                </button>
              ))}
            </div>
            {!pollOk ? <span className="meta error-text">Give your poll at least two different choices.</span> : null}
          </div>
        ) : null}

        <div className="chips">
          <button className={`chip${gif ? " on" : ""}`} disabled={!!poll || letter !== null} onClick={() => setPicker(true)}><GifIcon width={16} height={16} /> GIF</button>
          <button className={`chip${letter !== null ? " on" : ""}`} disabled={!!gif} onClick={() => setLetter(letter === null ? "" : null)}>Unsent letter</button>
          <button className={`chip${poll ? " on" : ""}`} disabled={!!gif}
                  onClick={() => setPoll(poll ? null : { options: ["", ""], durationHours: 24 })}>Poll</button>
        </div>

        <span className="tag">How should people respond?</span>
        <div className="chips">
          {MOODS.map((m) => (
            <button key={m.key} className={`chip${mood === m.key ? " on" : ""}`} onClick={() => setMood(mood === m.key ? null : m.key)}>{m.label}</button>
          ))}
        </div>
        <label className="check">
          <input type="checkbox" checked={!listen && commentsEnabled} disabled={listen} onChange={(e) => setCommentsEnabled(e.target.checked)} />
          <span className="box" aria-hidden />
          <span className="stack-sm">
            <span>Allow responses</span>
            <span className="small">{listen ? "Just listen — people can react, not reply" : commentsEnabled ? "Others can respond with gentle words" : "No one can respond to this one"}</span>
          </span>
        </label>

        {flag ? (
          <div className="notice error stack-sm" role="alert">
            <b>{flag.title}</b><span>{flag.body}</span>
            {flag.guidelines ? <button className="link" onClick={() => setGuidelines(true)}>Review guidelines →</button> : null}
          </div>
        ) : null}
      </div>

      <div className="compose-actions stack-sm">
        <Button block busy={busy} disabled={!ready} onClick={() => void release()}>{busy ? "Reading your words…" : "Release into the dark"}</Button>
        {hasContent ? <Button variant="ghost" block onClick={() => void discard()}>Discard</Button> : null}
        <p className="meta center-text">Only your alias and aura are shown. You can delete it anytime.</p>
      </div>

      {picker ? <GifPicker onClose={() => setPicker(false)} onPick={(g) => { setGif(g); setPicker(false); }} /> : null}
      {guidelines ? (
        <Sheet title="Community guidelines" onClose={() => setGuidelines(false)}>
          <div className="stack"><h2 className="title">Community guidelines</h2><GuidelinesList /></div>
        </Sheet>
      ) : null}
    </main>
  );
}
