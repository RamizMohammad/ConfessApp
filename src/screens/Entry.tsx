/**
 * Web-only entry pieces, drawn with the Android components so they sit in the same set:
 * the boot curtain and S0 · "Add to Home Screen" (iPhone / iPad in Safari).
 * Welcome and the sign-in sheet are in Welcome.tsx.
 */
import { useEffect, useRef } from "react";
import { biometricName, isIOSSafari } from "../lib/device.ts";
import { S } from "../lib/strings.ts";
import { Art } from "../ui/Art.tsx";
import { Icon } from "../ui/Icon.tsx";
import { Btn, Tag } from "../ui/kit.tsx";
import { tween } from "../ui/motion.ts";

/** Plain ink while the session is checked (the splash covers the first one). */
export function Boot() {
  return <div className="boot" aria-busy="true" aria-label="Opening Confess" />;
}

/* ── S0 · Add to Home Screen (iPhone in Safari) ─────────────────────────── */

/** Safari's own Share and Add glyphs (so the steps match what's on screen). */
function ShareGlyph() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-label="Share">
      <path d="M12 3v12M8 7l4-4 4 4M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
    </svg>
  );
}

function AddGlyph() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-label="Add">
      <rect x="4" y="4" width="16" height="16" rx="4" /><path d="M12 8v8M8 12h8" />
    </svg>
  );
}

export function InstallGuide({ onSkip }: { onSkip: () => void }) {
  const root = useRef<HTMLElement>(null);
  useEffect(() => {
    root.current?.querySelectorAll<HTMLElement>("[data-in]").forEach((v) => {
      void tween(v, { y: [14, 0], alpha: [0, 1] }, 520, { delay: 80 * Number(v.dataset.in) });
    });
  }, []);
  return (
    <main className="a-screen install" ref={root}>
      <div className="welcome-top"><Art name="logo_lockup" height={26} label={S("app_name")} /></div>
      <div className="install-body">
        <span data-in="0"><Tag>Before you begin</Tag></span>
        <h1 className="t-display install-h" data-in="1">Keep Confess on your <em>Home Screen.</em></h1>
        <p className="t-body install-sub" data-in="2">
          It opens like an app, unlocks with {biometricName}, and can tell you when someone responds.
          Nothing to download from the App Store.
        </p>
        <ol className="install-plate" data-in="3">
          <li>
            <span className="install-tile"><ShareGlyph /></span>
            <span className="t-label">Tap <b>Share</b> in Safari's toolbar</span>
          </li>
          <li>
            <span className="install-tile"><AddGlyph /></span>
            <span className="t-label">Scroll and tap <b>Add to Home Screen</b></span>
          </li>
          <li>
            <span className="install-tile"><Art name="logo_letty" size={22} /></span>
            <span className="t-label">Open Confess from your Home Screen</span>
          </li>
        </ol>
        <p className="t-meta install-note" data-in="4">On iPhone, Safari puts Share at the bottom of the screen; on iPad it's at the top right.</p>
      </div>
      <span data-in="5" className="install-skip">
        <Btn kind="ghost" block onClick={onSkip}>Continue in Safari for now</Btn>
      </span>
      {isIOSSafari ? <span className="install-arrow" aria-hidden><Icon name="arrow_up" size={28} /></span> : null}
    </main>
  );
}
