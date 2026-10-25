import { useState } from "react";
import { config } from "../config.ts";
import { googleConfigured } from "../lib/google.ts";
import { isAndroid, isIOSSafari, biometricName } from "../lib/device.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import { Button, FaceIdGlyph, GoogleButton, Letty, Lockup, Notice, Portal } from "../components/ui.tsx";
import { GuidelinesList } from "./Onboarding.tsx";

export function Boot() {
  return (
    <div className="boot" aria-busy="true" aria-label="Opening Confess">
      <Letty size={88} />
    </div>
  );
}

/* ── S0 · Add to Home Screen (iPhone in Safari) ─────────────────────────── */

function ShareIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-label="Share">
      <path d="M12 3v12M8 7l4-4 4 4M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
    </svg>
  );
}

function AddIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-label="Add">
      <rect x="4" y="4" width="16" height="16" rx="4" /><path d="M12 8v8M8 12h8" />
    </svg>
  );
}

export function InstallGuide({ onSkip }: { onSkip: () => void }) {
  return (
    <main className="screen">
      <div className="topbar"><span className="spacer" /><Lockup /><span className="spacer" /></div>
      <div className="grow stack">
        <div className="stack-sm">
          <span className="tag">Before you begin</span>
          <h1 className="display">Keep Confess on your Home Screen.</h1>
          <p className="body">It opens like an app, unlocks with {biometricName}, and can tell you when someone responds.
            Nothing to download from the App Store.</p>
        </div>
        <div className="plate">
          <ol className="install-steps">
            <li><span>Tap <ShareIcon /> <b>Share</b> in Safari's toolbar</span></li>
            <li><span>Scroll and tap <AddIcon /> <b>Add to Home Screen</b></span></li>
            <li><span>Open Confess from your Home Screen</span></li>
          </ol>
        </div>
        <p className="small muted">On iPhone, Safari puts Share at the bottom of the screen; on iPad it's at the top right.</p>
      </div>
      <div className="stack-sm">
        <Button variant="ghost" block onClick={onSkip}>Continue in Safari for now</Button>
      </div>
      {isIOSSafari ? (
        <svg className="install-arrow" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
          <path d="M12 4v15M6 13l6 6 6-6" />
        </svg>
      ) : null}
    </main>
  );
}

/* ── S1 · Welcome ───────────────────────────────────────────────────────── */

const NOTES = [
  { text: "“Sometimes I pretend I didn't see the message just to feel in control.”", alias: "silent_frequency" },
  { text: "“I told everyone I was proud of them, but I cried on the way home.”", alias: "folded_paper" },
  { text: "“I still look for your car at every red light.”", alias: "quiet_wanderer" },
];

export function Welcome() {
  const { phase, signInWithFaceId, canUseFaceId } = useSession();
  const [sheet, setSheet] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>(phase.name === "signedOut" ? phase.notice : undefined);
  const faceId = prefs.faceId && canUseFaceId;

  async function faceIdIn() {
    setError(undefined);
    setBusy(true);
    try {
      await signInWithFaceId();
    } catch (e) {
      if (!(e instanceof FaceIdError && e.kind === "cancelled")) setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="screen">
      <div className="topbar"><span className="spacer" /><Lockup /><span className="spacer" /></div>
      <div className="grow">
        <div className="welcome-notes" aria-hidden>
          {NOTES.map((n) => (
            <div className="paper" key={n.alias}>
              <p className="quote">{n.text}</p>
              <span className="meta">@{n.alias} · Anonymous</span>
            </div>
          ))}
        </div>
      </div>
      <div className="stack">
        <h1 className="headline">Say what you can't say anywhere else.</h1>
        <Notice>{error}</Notice>
        {isAndroid ? (
          <a className="btn btn-outline block" href={config.playStoreUrl} rel="noopener noreferrer">Get the Android app</a>
        ) : null}
        {faceId ? (
          <>
            <Button block busy={busy} onClick={faceIdIn}><FaceIdGlyph className="" /> Continue with {biometricName}</Button>
            <Button variant="ghost" block onClick={() => setSheet(true)}>Use Google instead</Button>
          </>
        ) : (
          <>
            <Button block onClick={() => setSheet(true)}>Join the world</Button>
            <Button variant="ghost" block onClick={() => setSheet(true)}>Already have an account? Sign in</Button>
          </>
        )}
        <p className="meta">No names · No photos · Just you</p>
      </div>
      {sheet ? <SignInSheet onClose={() => setSheet(false)} /> : null}
    </main>
  );
}

/* ── S2 · Sign-in sheet ─────────────────────────────────────────────────── */

const PROMISES = [
  { title: "Your email is never shown", body: "Google only proves it's you. Others see an alias and an aura, nothing else." },
  { title: "You'll pick an alias next", body: "Known only by a name you choose and an abstract avatar." },
  { title: "You can delete everything anytime", body: "Your account, confessions and responses — gone in one step." },
];

function SignInSheet({ onClose }: { onClose: () => void }) {
  const { signInWithGoogle } = useSession();
  const [leaving, setLeaving] = useState(false);
  const [guidelines, setGuidelines] = useState(false);
  const configured = googleConfigured();

  if (guidelines) {
    return (
      <Portal><div className="overlay" onClick={onClose}>
        <section className="sheet stack" role="dialog" aria-modal="true" aria-label="Community guidelines" onClick={(e) => e.stopPropagation()}>
          <div className="grabber" />
          <Button variant="ghost" onClick={() => setGuidelines(false)}>← Back</Button>
          <h2 className="title">Community guidelines</h2>
          <GuidelinesList />
        </section>
      </div></Portal>
    );
  }

  return (
    <Portal><div className="overlay" onClick={onClose}>
      <section className="sheet" role="dialog" aria-modal="true" aria-labelledby="signin-title" onClick={(e) => e.stopPropagation()}>
        <div className="grabber" />
        <div className="stack">
          <div className="stack-sm">
            <span className="tag">Anonymous doorway</span>
            <h2 id="signin-title" className="headline">Enter the room</h2>
            <p className="body">A safe haven for your untold stories.</p>
          </div>
          {configured ? (
            <GoogleButton busy={leaving} onClick={() => { setLeaving(true); signInWithGoogle(); }} />
          ) : (
            <Notice>Google sign-in isn't set up for this build (VITE_GOOGLE_CLIENT_ID).</Notice>
          )}
          <hr className="divider" />
          <span className="tag">Our promise</span>
          {PROMISES.map((p, i) => (
            <div className="promise" key={p.title}>
              <span className="dot">{i + 1}</span>
              <div><h3>{p.title}</h3><p className="small">{p.body}</p></div>
            </div>
          ))}
          <p className="small muted">
            By continuing, you agree to our{" "}
            <button className="link" onClick={() => setGuidelines(true)}>Community guidelines</button>.
          </p>
        </div>
      </section>
    </div></Portal>
  );
}
