/**
 * S1 · Welcome (WelcomeActivity + activity_welcome.xml), its "A glimpse inside" sheet
 * (sheet_welcome_peek.xml) and S2 · the sign-in sheet (SignInSheet + sheet_sign_in.xml).
 *
 * The paper notes fill whatever height the screen has (3 on a short phone, up to 7 on a tall
 * one), fanned like letters on a table. Tap one: a sheet opens on it in a sample feed of ten
 * made-up confessions drawn with the real feed cards. Both buttons open the sign-in sheet.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { config } from "../config.ts";
import { googleConfigured } from "../lib/google.ts";
import { biometricName, isAndroid } from "../lib/device.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { A, S } from "../lib/strings.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import type { Post } from "../lib/types.ts";
import { PostCard } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Art } from "../ui/Art.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, haptic, IconBtn, LinearProgress, SectionLabel, Sheet, Snackbar, Tag } from "../ui/kit.tsx";
import { decelerate, tween } from "../ui/motion.ts";
import { Guidelines } from "./Onboarding.tsx";

/** Rotation and sideways nudge for each note, back to front. */
const ROT = [-7, 5, -3, 6, -5, 3, -2];
const DX = [-18, 22, -10, 16, -20, 12, 0];
const ICONS: IconName[] = ["eye_off", "heart", "lock", "mail_open"];
const MAX_NOTES = 7;

/** TextClock: hh:mm a or HH:mm, as the phone is set. */
function Clock() {
  const fmt = () => new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(new Date()).toUpperCase();
  const [now, setNow] = useState(fmt);
  useEffect(() => {
    const t = window.setInterval(() => setNow(fmt()), 10_000);
    return () => window.clearInterval(t);
  }, []);
  return <span className="k-tag t-meta-small welcome-clock">{now}</span>;
}

/** WelcomeActivity.buildNotes: as many notes as the space holds; the front one is sample 0. */
function NoteStack({ onOpen }: { onOpen: (sample: number) => void }) {
  const box = useRef<HTMLDivElement>(null);
  const [n, setN] = useState(0);
  const texts = A("demo_texts"), aliases = A("demo_aliases");
  const layout = useCallback(() => {
    const el = box.current;
    if (!el) return;
    const h = el.clientHeight;
    const card = 150, minStep = 42;
    let step = 66;
    const count = Math.max(3, Math.min(MAX_NOTES, 1 + Math.floor(Math.max(0, h - card) / step)));
    if (card + (count - 1) * step > h) step = Math.max(minStep, Math.floor((h - card) / Math.max(1, count - 1)));
    const used = card + (count - 1) * step;
    const top = Math.max(0, Math.floor((h - used) / 2));
    el.style.setProperty("--top", `${top}px`);
    el.style.setProperty("--step", `${step}px`);
    setN(count);
  }, []);
  // Built on the first layout, and again when the screen changes shape (rotation, Split View) —
  // on Android the activity is rebuilt then.
  useLayoutEffect(() => {
    layout();
    const el = box.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(() => layout());
    ro.observe(el);
    return () => ro.disconnect();
  }, [layout]);
  const dealt = useRef(false);
  useEffect(() => {
    // Dealt onto the table one after another (the first time only).
    box.current?.querySelectorAll<HTMLElement>(".welcome-note").forEach((card, i) => {
      card.style.setProperty("--i", String(i));
      card.style.setProperty("--dx", `${DX[i % DX.length]}px`);
      card.style.setProperty("--rot", `${ROT[i % ROT.length]}deg`);
      if (!dealt.current) void tween(card, { y: [28, 0], alpha: [0, 1] }, 420, { delay: 260 + 70 * i, ease: decelerate(1.6) });
    });
    if (n) dealt.current = true;
  }, [n]);
  const tap = (el: HTMLElement, i: number, sample: number) => {
    haptic();
    const r = ROT[i % ROT.length]!;
    el.animate([{ scale: "1", rotate: `${r}deg` }, { scale: "1.05", rotate: "0deg" }], { duration: 140, fill: "forwards" })
      .finished.then(() => {
        el.animate([{ scale: "1.05", rotate: "0deg" }, { scale: "1", rotate: `${r}deg` }], { duration: 220, fill: "forwards" });
        onOpen(sample);
      }, () => onOpen(sample));
  };
  return (
    <div className="welcome-notes" ref={box}>
      {Array.from({ length: n }, (_, i) => {
        const sample = n - 1 - i;
        return (
          <button key={i} type="button" className="welcome-note" aria-label={S("welcome_peek_hint")}
                  onClick={(e) => tap(e.currentTarget, i, sample)}>
            <span className="t-quote welcome-note-text">“{texts[sample % texts.length]}”</span>
            <span className="welcome-note-rule" />
            <span className="welcome-note-meta">
              <span className="t-meta">· {aliases[sample % aliases.length]}</span>
              <Icon name={ICONS[sample % ICONS.length]!} size={18} />
            </span>
          </button>
        );
      })}
    </div>
  );
}

export function Welcome() {
  const { phase } = useSession();
  const toast = useToast();
  const [signIn, setSignIn] = useState(false);
  const [peek, setPeek] = useState<number | null>(null);
  const [terms, setTerms] = useState(false);
  const root = useRef<HTMLElement>(null);

  // A notice from the last session (signed out, expired…) shows once, like a toast.
  const notice = phase.name === "signedOut" ? phase.notice : undefined;
  useEffect(() => { if (notice) toast(notice); }, [notice, toast]);

  useEffect(() => {
    root.current?.querySelectorAll<HTMLElement>("[data-in]").forEach((v) => {
      void tween(v, { y: [14, 0], alpha: [0, 1] }, 520, { delay: 80 * Number(v.dataset.in) });
    });
  }, []);

  if (terms) return <Guidelines readOnly onClose={() => setTerms(false)} />;

  return (
    <main className="a-screen welcome" ref={root}>
      <div className="welcome-top">
        <Art name="logo_lockup" height={26} label={S("app_name")} />
        <Clock />
      </div>
      <h1 className="t-display welcome-h1" data-in="0">{S("tagline_line1")}</h1>
      <p className="t-display welcome-h2" data-in="1">{S("tagline_line2")}</p>
      <p className="t-body welcome-sub" data-in="2">{S("tagline_sub")}</p>
      <div className="welcome-stack" data-in="3"><NoteStack onOpen={setPeek} /></div>
      <p className="t-meta welcome-hint" data-in="4"><Icon name="eye" size={24} />{S("welcome_peek_hint")}</p>
      <span data-in="5" className="welcome-join">
        <Btn block icon="arrow_right" onClick={() => setSignIn(true)}>{S("welcome_join")}</Btn>
      </span>
      <p className="t-meta welcome-footer" data-in="6">{S("welcome_footer")}</p>
      <span data-in="7" className="welcome-have">
        <Btn kind="ghost" className="welcome-have-btn" onClick={() => setSignIn(true)}>{S("welcome_have_account")}</Btn>
      </span>

      <PeekSheet sample={peek} onClose={() => setPeek(null)} onJoin={() => { setPeek(null); setSignIn(true); }} />
      <SignInSheet open={signIn} onClose={() => setSignIn(false)} onTerms={() => { setSignIn(false); setTerms(true); }} />
    </main>
  );
}

// ── A glimpse inside ─────────────────────────────────────────────────────────

/** WelcomeActivity.samples(): ten made-up confessions with believable reactions, newest first. */
function samples(): Post[] {
  const texts = A("demo_texts"), aliases = A("demo_aliases"), moods = A("demo_moods");
  const minutesAgo = [4, 18, 42, 75, 130, 190, 260, 400, 610, 900];
  const felt = [38, 112, 241, 57, 304, 23, 86, 167, 92, 211];
  const hugs = [6, 31, 64, 19, 12, 4, 27, 58, 9, 40];
  const responses = [7, 0, 19, 12, 25, 9, 14, 0, 6, 17];
  const now = Date.now();
  return texts.map((text, i) => {
    const mood = (moods[i] || null) as Post["mood"];
    const commentsEnabled = mood !== "listen";
    return {
      postId: `sample-${i}`,
      author: { alias: aliases[i % aliases.length]!, avatar: `aura_${String(((i * 5) % 12) + 1).padStart(2, "0")}` },
      text, mood, commentsEnabled,
      commentCount: commentsEnabled ? responses[i % responses.length]! : 0,
      reactions: { felt: felt[i % felt.length]!, hug: hugs[i % hugs.length]! },
      likeCount: felt[i % felt.length]! + hugs[i % hugs.length]!,
      likedByMe: false, myReaction: null,
      createdAt: new Date(now - minutesAgo[i % minutesAgo.length]! * 60_000).toISOString(),
      letterTo: i === 7 ? S("welcome_sample_letter_to") : null,
      gif: null, poll: null, isMine: false, chapter: null, riskScore: null, sensitive: false, burst: null,
    } as Post;
  });
}

const NOOP = { onChange: () => {}, onRemove: () => {}, onBlocked: () => {} };

function PeekSheet({ sample, onClose, onJoin }: { sample: number | null; onClose: () => void; onJoin: () => void }) {
  const list = useRef<HTMLDivElement>(null);
  const [posts] = useState(samples);
  const [nudge, setNudge] = useState(0);
  // Opened at the note that was tapped.
  useLayoutEffect(() => {
    if (sample == null) return;
    const el = list.current?.children[sample] as HTMLElement | undefined;
    if (list.current && el) list.current.scrollTop = el.offsetTop - list.current.offsetTop;
  }, [sample]);
  useEffect(() => {
    if (!nudge) return;
    const t = window.setTimeout(() => setNudge(0), 2750);
    return () => window.clearTimeout(t);
  }, [nudge]);
  const say = () => setNudge((x) => x + 1);
  return (
    <Sheet open={sample != null} onClose={onClose} label={S("welcome_peek_title")} handle={false} className="peek">
      <div className="peek-head">
        <span className="t-meta-small peek-label"><i className="k-dot" aria-hidden />{S("welcome_peek_sample")}</span>
        <h2 className="t-headline-small peek-title">{S("welcome_peek_title")}</h2>
        <p className="t-body-medium peek-sub">{S("welcome_peek_sub")}</p>
      </div>
      <div className="k-sheet-scroll peek-list feed" ref={list}>
        {posts.map((p) => <PostCard key={p.postId} post={p} actions={NOOP} sample={say} />)}
      </div>
      <div className="peek-join">
        <Btn block onClick={onJoin} className="k-icon-end" icon="arrow_right">{S("welcome_join")}</Btn>
      </div>
      {nudge ? <Snackbar key={nudge} text={S("welcome_peek_nudge")} action={S("welcome_join")} onAction={onJoin} /> : null}
    </Sheet>
  );
}

// ── S2 · Sign-in sheet ("Enter the room") ────────────────────────────────────

const PROMISES: [IconName, string, string][] = [
  ["eye_off", "signin_p1_title", "signin_p1_body"],
  ["user", "signin_p2_title", "signin_p2_body"],
  ["trash", "signin_p3_title", "signin_p3_body"],
];

/**
 * Google → server login: a returning account goes in, a new one starts onboarding (S3). The web
 * adds what an iPhone has instead of the Android fingerprint: Face ID, once it's been turned on.
 */
export function SignInSheet({ open, onClose, onTerms }: { open: boolean; onClose: () => void; onTerms: () => void }) {
  const { signInWithGoogle, signInWithFaceId, canUseFaceId } = useSession();
  const [working, setWorking] = useState<"google" | "face" | null>(null);
  const [error, setError] = useState<string>();
  const configured = googleConfigured();
  const faceId = prefs.faceId && canUseFaceId;

  useEffect(() => { if (open) { setError(undefined); setWorking(null); } }, [open]);

  const google = () => {
    if (working) return;
    if (!configured) { setError(S("signin_failed")); return; }
    setWorking("google");
    setError(undefined);
    signInWithGoogle();                 // leaves for Google; comes back signed in or onboarding
  };
  const face = async () => {
    if (working) return;
    setWorking("face");
    setError(undefined);
    try {
      await signInWithFaceId();
      haptic();
    } catch (e) {
      if (!(e instanceof FaceIdError && e.kind === "cancelled")) setError(errorMessage(e));
      setWorking(null);
    }
  };

  return (
    <Sheet open={open} onClose={onClose} label={S("signin_title")} handle={false} cancelable={!working} className="signin">
      <span className={working ? "signin-progress on" : "signin-progress"}><LinearProgress /></span>
      <div className="k-sheet-scroll signin-body">
        <div className="signin-handle-row">
          <span className="m3-handle" aria-hidden />
          <IconBtn icon="close" label={S("action_close")} onClick={onClose} disabled={!!working} className="signin-close" />
        </div>
        <Tag>{S("signin_tag")}</Tag>
        <h2 className="t-headline signin-title">{S("signin_title")}</h2>
        <p className="t-body signin-sub">{S("signin_subtitle")}</p>
        <Btn kind="light" block icon="google" disabled={!!working} onClick={google} className="signin-google">
          {working === "google" ? S("signin_working") : S("signin_google")}
        </Btn>
        {faceId ? (
          <Btn kind="outlined" block icon="fingerprint" busy={working === "face"} disabled={!!working} onClick={() => void face()}
               className="signin-face">{`Continue with ${biometricName}`}</Btn>
        ) : null}
        {error ? <p className="t-meta signin-error" role="alert">{error}</p> : null}
        <SectionLabel>{S("signin_promise")}</SectionLabel>
        <div className="signin-plate">
          {PROMISES.map(([icon, title, body]) => (
            <div className="signin-promise" key={title}>
              <span className="signin-tile"><Icon name={icon} size={20} /></span>
              <span className="signin-promise-text">
                <span className="t-label">{S(title)}</span>
                <span className="t-body-medium signin-promise-body">{S(body)}</span>
              </span>
            </div>
          ))}
        </div>
        <p className="t-meta signin-terms">{S("signin_terms_prefix")}</p>
        <Btn kind="link" icon="arrow_right" onClick={onTerms} className="signin-terms-link">{S("signin_terms_link")}</Btn>
        {isAndroid ? (
          <a className="k-btn k-btn-outlined k-block signin-android" href={config.playStoreUrl} rel="noopener noreferrer">Get the Android app</a>
        ) : null}
      </div>
    </Sheet>
  );
}
