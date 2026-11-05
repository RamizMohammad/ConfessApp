/**
 * ComposeActivity (activity_compose): Discard · UNSENT NOTE · help; the prompt; the paper (aura,
 * alias, Today/Tonight clock, "Draft kept" stamp, To: line, ruled editor, the GIF, ONLY YOUR ALIAS
 * IS SHOWN); Letter · Poll · GIF; the poll editor; HOW SHOULD PEOPLE RESPOND?; the counter;
 * the moderation plate; Instant publish / Publish → "How should it land?" → Edit / Release.
 * With a GIF only 100 characters go: the rest is struck through in brick.
 * On iPad it opens as a centred sheet (Home decides; the CSS draws it).
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api.ts";
import { deleteDraft, getDraft, newDraftId, saveDraft, takeResumable, type Draft } from "../lib/drafts.ts";
import { length, moderationReason } from "../lib/format.ts";
import * as P from "../lib/posts.ts";
import { emit } from "../lib/postStore.ts";
import { back, resetTo } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { S } from "../lib/strings.ts";
import { LIMITS, MOODS, type Gif, type Mood } from "../lib/types.ts";
import { Aura } from "../components/Aura.tsx";
import { GifPicker } from "../components/GifPicker.tsx";
import { Confirm, GifView, MOOD_EMOJI, moodLabel } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Icon } from "../ui/Icon.tsx";
import { Btn, Chip, GlideToggle, haptic, IconBtn, Switch } from "../ui/kit.tsx";
import { tween } from "../ui/motion.ts";
import { Guidelines } from "./Onboarding.tsx";

type Duration = 24 | 72 | 168;
type Extra = "letter" | "poll" | "gif";
const MAX_CHARS = LIMITS.post, GIF_MAX = LIMITS.gifPost, WARN_AT = 1800;
const POLL_MIN = 2, POLL_MAX = 4;

function timeLabel(): string {
  const now = new Date();
  const clock = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(now);
  const night = now.getHours() >= 20 || now.getHours() < 5;
  return S(night ? "compose_tonight" : "compose_today", clock);
}

/** The words up to the GIF limit, then the struck-out rest (markOverflow). */
function splitOverflow(text: string): [string, string, string] {
  const chars = [...text];
  let start = 0;
  while (start < chars.length && /\s/.test(chars[start]!)) start++;
  const cut = start + GIF_MAX;
  let end = chars.length;
  while (end > cut && /\s/.test(chars[end - 1]!)) end--;
  if (cut >= end) return [text, "", ""];
  return [chars.slice(0, cut).join(""), chars.slice(cut, end).join(""), chars.slice(end).join("")];
}

export function Compose({ draftId }: { draftId?: string }) {
  const { me } = useSession();
  const toast = useToast();
  const id = useRef(draftId ?? newDraftId());
  const [text, setText] = useState("");
  const [allow, setAllow] = useState(true);
  const allowBeforeListen = useRef(true);
  const [mood, setMood] = useState<Mood | null>(null);
  const [extra, setExtra] = useState<Extra | null>(null);
  const [letter, setLetter] = useState("");
  const [letterError, setLetterError] = useState(false);
  const [choices, setChoices] = useState<string[]>([]);
  const [duration, setDuration] = useState<Duration>(24);
  const [gif, setGif] = useState<Gif | null>(null);
  const [picker, setPicker] = useState(false);
  const [working, setWorking] = useState(false);
  const [onOptions, setOnOptions] = useState(false);
  const [notice, setNotice] = useState<{ soft: boolean; title: string; body: string; guidelines: boolean } | null>(null);
  const [stamp, setStamp] = useState<string | null>(null);
  const [guidelines, setGuidelines] = useState(false);
  const [discardAsk, setDiscardAsk] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [time] = useState(timeLabel);
  const area = useRef<HTMLTextAreaElement>(null);
  const letterInput = useRef<HTMLInputElement>(null);
  const paper = useRef<HTMLDivElement>(null);
  const gifBox = useRef<HTMLDivElement>(null);
  const noticeEl = useRef<HTMLDivElement>(null);
  const pollEl = useRef<HTMLDivElement>(null);
  const choiceRefs = useRef<(HTMLInputElement | null)[]>([]);
  const finished = useRef(false);

  const fromDraft = (d: Draft) => {
    id.current = d.id;
    setText(d.text);
    setAllow(d.commentsEnabled);
    allowBeforeListen.current = d.commentsEnabled;
    setMood(d.mood);
    if (d.letterTo !== null) { setExtra("letter"); setLetter(d.letterTo); }
    else if (d.poll) { setExtra("poll"); setChoices(d.poll.options); setDuration(d.poll.durationHours); }
    else if (d.gif) { setExtra("gif"); setGif(d.gif); }
  };

  // restoreDraft: the one asked for, or the last one left by going back.
  useEffect(() => {
    let stop = false;
    void (draftId ? getDraft(draftId) : takeResumable()).then((d) => {
      if (stop) return;
      if (d) { fromDraft(d); setStamp(S("compose_draft_restored")); }
      setLoaded(true);
    });
    const t = window.setTimeout(() => {
      const el = area.current;
      if (el && !stop) { el.focus({ preventScroll: true }); el.setSelectionRange(el.value.length, el.value.length); }
    }, 350);
    return () => { stop = true; window.clearTimeout(t); };
  }, [draftId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Drafts (encrypted, on this device only): 700 ms after a change, when hidden, and when leaving.
  const draft = (): Draft => ({
    id: id.current, text, commentsEnabled: allow, mood,
    letterTo: extra === "letter" ? letter : null,
    poll: extra === "poll" ? { options: choices, durationHours: duration } : null,
    gif: extra === "gif" ? gif : null, updatedAt: Date.now(),
  });
  const latest = useRef<Draft>(draft());
  latest.current = draft();
  const saveNow = useCallback(async (left = false) => {
    if (finished.current || !loaded) return false;
    const d = latest.current;
    if (!d.text.trim()) { await deleteDraft(d.id); return false; }
    await saveDraft({ ...d, left, updatedAt: Date.now() });
    return true;
  }, [loaded]);
  useEffect(() => {
    if (!loaded) return;
    const t = window.setTimeout(() => { void saveNow().then((kept) => { if (kept) setStamp(S("compose_draft_kept")); }); }, 700);
    return () => window.clearTimeout(t);
  }, [loaded, text, allow, mood, extra, letter, choices, duration, gif, saveNow]);
  useEffect(() => {
    const hide = () => { if (document.visibilityState === "hidden") void saveNow(); };
    document.addEventListener("visibilitychange", hide);
    return () => {
      document.removeEventListener("visibilitychange", hide);
      // Leaving by back / swipe: kept for next time (leave()).
      if (!finished.current && latest.current.text.trim()) {
        void saveNow(true);
        toast(S("compose_saved_to_drafts"));
      }
    };
  }, [saveNow]); // eslint-disable-line react-hooks/exhaustive-deps

  const limit = gif ? GIF_MAX : MAX_CHARS;
  const n = length(text.trim());
  const over = n - limit;
  const warn = gif ? limit - 20 : WARN_AT;
  const ok = !working && n > 0 && n <= limit;
  const listen = mood === "listen";

  const hideNotice = () => { if (notice && !working) setNotice(null); };

  function pickExtra(next: Extra | null) {
    if (working) return;
    if (next === "gif") { openPicker(); return; }
    if (gif) setGif(null);
    setExtra(next);
    if (next === "letter") window.setTimeout(() => letterInput.current?.focus(), 0);
    if (next === "poll") {
      const list = choices.length >= POLL_MIN ? choices : ["", ""];
      setChoices(list);
      window.setTimeout(() => {
        pollEl.current?.scrollIntoView({ block: "start", behavior: "smooth" });
        choiceRefs.current[0]?.focus({ preventScroll: true });
      }, 0);
    }
  }

  function openPicker() {
    if (working) return;
    haptic("tick");
    area.current?.blur();
    setPicker(true);
  }

  function attachGif(g: Gif | null) {
    setGif(g);
    if (g) {
      setExtra("gif");
      window.setTimeout(() => void tween(gifBox.current, { alpha: [0, 1], scale: [0.96, 1] }, 220), 0);
    } else if (extra === "gif") setExtra(null);
  }

  function applyMood(key: Mood | null) {
    const wasListen = mood === "listen";
    setMood(key);
    if (key === "listen" && !wasListen) { allowBeforeListen.current = allow; setAllow(false); }
    else if (key !== "listen" && wasListen) setAllow(allowBeforeListen.current);
  }

  function pollOrNull(): { options: string[]; durationHours: Duration } | null {
    const seen = new Set<string>(), out: string[] = [];
    for (const raw of choices) {
      const c = raw.trim().replace(/\s+/g, " ");
      if (!c) continue;
      if (seen.has(c.toLowerCase())) return null;
      seen.add(c.toLowerCase());
      out.push(c);
    }
    return out.length >= POLL_MIN ? { options: out, durationHours: duration } : null;
  }

  function showOptions(show: boolean) {
    if (show === onOptions || (show && working)) return;
    if (show) {
      const body = text.trim();
      if (!body || length(body) > limit) return;
      haptic();
      (document.activeElement as HTMLElement | null)?.blur();
    }
    setOnOptions(show);
  }

  async function release() {
    const body = text.trim();
    if (working || !body || length(body) > limit) return;
    const to = extra === "letter" ? letter.trim() : null;
    if (to !== null && !to) {
      haptic();
      setOnOptions(false);
      setLetterError(true);
      window.setTimeout(() => letterInput.current?.focus(), 0);
      return;
    }
    let poll = null;
    if (extra === "poll") {
      poll = pollOrNull();
      if (!poll) { haptic(); toast(S("compose_poll_invalid")); return; }
    }
    await saveNow();                    // safe even if the app dies mid-request
    setNotice(null);
    setWorking(true);
    haptic();
    try {
      const post = await P.createPost({
        text: body, commentsEnabled: allow && !listen, mood, letterTo: to, poll,
        gif: extra === "gif" && gif ? { id: gif.id, width: gif.width, height: gif.height } : null,
      });
      finished.current = true;
      await deleteDraft(id.current);
      emit({ type: "created", post });
      resetTo({ name: "feed" });
      window.setTimeout(() => window.dispatchEvent(new Event("confess:released")), 0);
      toast(post.pendingReview
        ? "Posted. Others will see it once our safety check is back — usually within minutes."
        : S("compose_released"));
    } catch (e) {
      setWorking(false);
      const flagged = e instanceof ApiError && (e.status === 451 || e.code === "CONTENT_FLAGGED");
      const unavailable = e instanceof ApiError && e.code === "MODERATION_UNAVAILABLE";
      if (onOptions && (flagged || unavailable)) setOnOptions(false);
      if (flagged) {
        haptic();
        const empty = /empty|formatting/i.test((e as ApiError).message);
        setNotice({ soft: false, title: S("mod_flagged_title"), body: empty ? S("compose_empty_after_clean") : moderationReason((e as ApiError).message), guidelines: !empty });
      } else if (unavailable) {
        setNotice({ soft: true, title: S("mod_unavailable_title"), body: S("mod_unavailable_body"), guidelines: false });
      } else toast(errorMessage(e));
    }
  }
  useLayoutEffect(() => {
    if (notice) {
      void tween(noticeEl.current, { alpha: [0, 1] }, 200);
      noticeEl.current?.scrollIntoView({ block: "end", behavior: "smooth" });
    }
  }, [notice]);
  useEffect(() => { void tween(paper.current, { alpha: working ? [1, 0.7] : [0.7, 1] }, 200); }, [working]);

  function discard() {
    if (working) return;
    if (!text.trim()) { finished.current = true; void deleteDraft(id.current); back(); return; }
    setDiscardAsk(true);
  }
  function leave() {
    if (working) return;
    if (onOptions) { showOptions(false); return; }
    back();
  }
  // Esc (iPad keyboard) = Android back: options → write → leave; ⌘↩ publishes.
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      if (document.querySelector("[aria-modal='true']") || guidelines) return;
      if (e.key === "Escape") { e.preventDefault(); leave(); }
      if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) { e.preventDefault(); if (onOptions) void release(); else showOptions(true); }
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  });

  const [head, struck, tail] = gif ? splitOverflow(text) : [text, "", ""];
  const preview = extra === "letter" && letter.trim() ? `${S("compose_to")} ${letter.trim()}\n${text.trim()}` : text.trim();
  const counterTone = n === 0 ? "muted" : over > 0 ? "brick" : n >= warn ? "amber" : "secondary";

  const moods = (
    <div className="scroll-x compose-moods" role="group" aria-label={S("compose_mood_label")}>
      {MOODS.map((m) => (
        <Chip key={m.key} on={mood === m.key} disabled={working}
              onClick={() => { haptic("tick"); applyMood(mood === m.key ? null : m.key); }}>
          {`${MOOD_EMOJI[m.key]}  ${moodLabel(m.key)}`}
        </Chip>
      ))}
    </div>
  );

  return (
    <main className="compose-a">
      <header className="compose-top">
        <Btn kind="ghost" icon="close" className="compose-discard" disabled={working} onClick={discard}>{S("compose_discard")}</Btn>
        <span className="compose-status t-meta-small"><i className="dot-amber" />{S("compose_status")}</span>
        <IconBtn icon="help" label={S("cd_compose_help")} className="compose-help" onClick={() => setGuidelines(true)} />
      </header>
      {!onOptions ? <div className="compose-mood-top">{moods}</div> : null}

      <div className="compose-pages">
        <div className="compose-scroll" hidden={onOptions}>
          <h1 className="t-headline-small compose-prompt">{S("compose_prompt")}</h1>
          <p className="compose-prompt-sub">{S("compose_prompt_sub")}</p>

          <div ref={paper} className="compose-paper">
            <div className="compose-author">
              <Aura value={me?.avatar} size={48} />
              <span className="compose-who">
                <span className="t-alias compose-alias">@{me?.alias}</span>
                <span className="t-meta compose-time">{time}</span>
              </span>
              {stamp && !working ? <span className="compose-stamp t-meta-small m-item-enter" key={stamp}><Icon name="save" size={14} />{stamp}</span> : null}
            </div>
            <i className="compose-rule" />
            {extra === "letter" ? (
              <label className="compose-letter">
                <span className="compose-to">{S("compose_to")}</span>
                <input ref={letterInput} value={letter} maxLength={LIMITS.letterTo} placeholder={S("compose_to_hint")}
                       aria-label={S("compose_letter_invalid")} disabled={working} aria-invalid={letterError || undefined}
                       onChange={(e) => { setLetter(e.target.value); setLetterError(false); hideNotice(); }} />
              </label>
            ) : null}
            {letterError ? <span className="compose-letter-error t-meta" role="alert">{S("compose_letter_invalid")}</span> : null}
            <div className={`compose-editor${gif ? " one" : ""}`}>
              <div className="compose-mirror" aria-hidden>{head}{struck ? <s>{struck}</s> : null}{tail}{"​"}</div>
              <textarea ref={area} value={text} placeholder={S("compose_hint")} aria-label={S("compose_cd_editor")} disabled={working}
                        onChange={(e) => { setText(e.target.value); setStamp(null); hideNotice(); }} />
            </div>
            {gif ? (
              <div ref={gifBox} className="compose-gif">
                <GifView gif={gif} maxRatio={1.25} onClick={openPicker} />
                <button type="button" className="compose-gif-remove" aria-label={S("cd_remove_gif")} disabled={working}
                        onClick={() => { haptic("tick"); attachGif(null); }}><Icon name="close" size={24} /></button>
                <span className="compose-gif-mark t-meta-small">{S("giphy")}</span>
              </div>
            ) : null}
            <i className="compose-rule" />
            <span className="compose-only t-meta-small"><Icon name="lock_small" size={16} />{S("compose_stamp")}</span>
          </div>

          <div className="compose-extras">
            <GlideToggle<Extra> value={extra} disabled={working} label="Extras"
                                options={[{ key: "letter", label: S("compose_letter"), wide: true }, { key: "poll", label: S("compose_poll") },
                                          { key: "gif", label: S("compose_gif") }]}
                                onChange={(k) => pickExtra(k)} onClear={() => pickExtra(null)} />
          </div>

          {extra === "poll" ? (
            <div ref={pollEl} className="compose-poll">
              <span className="t-body">{S("compose_poll_title")}</span>
              <span className="t-meta compose-poll-sub">{S("compose_poll_sub")}</span>
              <div className="compose-poll-options">
                {choices.map((c, i) => (
                  <div key={i} className="compose-choice">
                    <input ref={(el) => { choiceRefs.current[i] = el; }} className="input-pill-plain" value={c} maxLength={LIMITS.pollOption}
                           placeholder={S("compose_poll_hint", i + 1)} aria-label={S("compose_poll_hint", i + 1)} disabled={working}
                           enterKeyHint="next" autoComplete="off"
                           onChange={(e) => { const v = e.target.value; setChoices((cur) => cur.map((x, j) => (j === i ? v : x))); hideNotice(); }} />
                    {choices.length > POLL_MIN ? (
                      <button type="button" className="compose-choice-remove" aria-label={S("compose_poll_remove")} disabled={working}
                              onClick={() => { haptic("tick"); setChoices((cur) => cur.filter((_, j) => j !== i)); }}>
                        <Icon name="close" size={24} />
                      </button>
                    ) : null}
                  </div>
                ))}
              </div>
              {choices.length < POLL_MAX ? (
                <Btn kind="link" icon="plus" className="compose-poll-add t-meta" disabled={working}
                     onClick={() => { haptic("tick"); setChoices((cur) => [...cur, ""]); window.setTimeout(() => choiceRefs.current[choices.length]?.focus(), 0); }}>
                  {S("compose_poll_add")}
                </Btn>
              ) : null}
              <div className="compose-durations" role="radiogroup">
                {([24, 72, 168] as Duration[]).map((h) => (
                  <Chip key={h} on={duration === h} disabled={working} onClick={() => setDuration(h)}>
                    {S(h === 24 ? "compose_poll_1d" : h === 72 ? "compose_poll_3d" : "compose_poll_7d")}
                  </Chip>
                ))}
              </div>
            </div>
          ) : null}

          <div className="compose-mood-home">
            <span className="k-section-label t-meta-small compose-mood-label">{S("compose_mood_label")}</span>
            {moods}
          </div>

          <div className="compose-count">
            <span className={`compose-count-n ${counterTone}`}>{S("compose_counter", n)}</span>
            <span className="t-meta-small compose-count-max">{S("compose_counter_max", limit)}</span>
          </div>
          {over > 0 ? <p className="compose-too-long t-meta">{gif ? S("compose_gif_limit", GIF_MAX) : S("compose_too_long", over)}</p> : null}

          {notice ? (
            <div ref={noticeEl} className={`compose-notice${notice.soft ? " soft" : ""}`} role="alert">
              <Icon name={notice.soft ? "shield" : "alert"} size={24} />
              <div className="compose-notice-text">
                <span className="t-body">{notice.title}</span>
                <span className="t-body-medium compose-notice-body">{notice.body}</span>
                {notice.soft ? <Btn kind="link" icon="refresh" className="compose-notice-action" onClick={() => void release()}>{S("action_retry")}</Btn>
                  : notice.guidelines ? <Btn kind="link" className="compose-notice-action" onClick={() => setGuidelines(true)}>{S("mod_review")}<Icon name="arrow_right" size={18} /></Btn>
                    : null}
              </div>
            </div>
          ) : null}
        </div>

        {onOptions ? (
          <div className="compose-options">
            <span className="t-meta-small compose-options-label">{S("compose_options_label")}</span>
            <h2 className="t-headline compose-options-title">{S("compose_options_title")}</h2>
            <p className="compose-options-preview">{preview}</p>
            <div className="compose-allow">
              <Switch checked={allow && !listen} disabled={working || listen} label={S("compose_allow")} onChange={setAllow} />
              <button type="button" className="compose-allow-text" disabled={listen} onClick={() => { if (!working && !listen) { haptic("tick"); setAllow(!allow); } }}>
                <span className="t-body">{S("compose_allow")}</span>
                <span className="t-meta compose-allow-sub">{S(listen ? "compose_allow_listen" : allow ? "compose_allow_on" : "compose_allow_off")}</span>
              </button>
            </div>
            <p className="t-meta compose-options-note"><Icon name="lock_small" size={16} />{S("compose_options_note")}</p>
          </div>
        ) : null}
      </div>

      <div className="compose-bottom">
        {!onOptions ? (
          <div className="compose-actions">
            <Btn kind="outlined" disabled={!ok} onClick={() => void release()}>{working ? S("compose_checking") : S("compose_instant")}</Btn>
            <Btn disabled={!ok} icon="arrow_right" className="k-icon-end" onClick={() => showOptions(true)}>{S("compose_publish_next")}</Btn>
          </div>
        ) : (
          <div className="compose-actions">
            <Btn kind="outlined" icon="arrow_left" disabled={working} onClick={() => showOptions(false)}>{S("compose_options_back")}</Btn>
            <Btn disabled={!ok} icon={working ? undefined : "send"} onClick={() => void release()}>
              {working ? S("compose_checking") : S("compose_release_short")}
            </Btn>
          </div>
        )}
        <p className="t-meta-small compose-footnote">{S("compose_footnote")}</p>
      </div>

      <GifPicker open={picker} onClose={() => setPicker(false)} onPick={(g) => attachGif(g)} />
      <Confirm open={discardAsk} danger icon="trash" title={S("compose_discard_title")} body={S("compose_discard_body")}
               confirm={S("compose_leave_discard")} onClose={() => setDiscardAsk(false)}
               onConfirm={async () => { finished.current = true; await deleteDraft(id.current); setDiscardAsk(false); back(); }} />
      {guidelines ? <div className="compose-guidelines"><Guidelines readOnly onClose={() => setGuidelines(false)} /></div> : null}
    </main>
  );
}
