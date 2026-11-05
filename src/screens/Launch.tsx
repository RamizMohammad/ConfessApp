/**
 * Opening the app, as the Android launcher does it (LauncherActivity + MaintenanceActivity):
 *
 *  - Splash: ink, Letty 240 dp in the middle playing splash_letty_anim (1000 ms). It stays at
 *    least 1050 ms and until we know where to go; then Letty dips, zooms toward you and fades as
 *    the curtain lifts, and the next screen comes in.
 *  - The server is asked GET /ready first (skipped when the phone is offline). Anything but a 200
 *    → Maintenance, which re-checks on "Try again" and quietly every 30 s.
 */
import { useEffect, useRef, useState } from "react";
import { isDeviceOffline, serverUp } from "../lib/serverStatus.ts";
import { S } from "../lib/strings.ts";
import { AnimatedArt, Art } from "../ui/Art.tsx";
import { Btn, Tag } from "../ui/kit.tsx";
import { accelerate, decelerate, tween } from "../ui/motion.ts";

const MIN_SPLASH_MS = 1050;
const MAX_WAIT_MS = 8000;
const AUTO_RETRY_MS = 30_000;

export type ServerState = "checking" | "up" | "down";

/** Ask the server once at start-up (or say "up" straight away when offline). */
export function useServerCheck(): [ServerState, (s: ServerState) => void] {
  const [state, setState] = useState<ServerState>(() => (isDeviceOffline() ? "up" : "checking"));
  useEffect(() => {
    if (state !== "checking") return;
    const ctl = new AbortController();
    void serverUp(ctl.signal).then((up) => { if (!ctl.signal.aborted) setState(up ? "up" : "down"); });
    return () => ctl.abort();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return [state, setState];
}

/** The splash curtain. `ready` = we know where to go; `onGone` once it has played its way out. */
export function Splash({ ready, onGone }: { ready: boolean; onGone: () => void }) {
  const screen = useRef<HTMLDivElement>(null);
  const icon = useRef<HTMLDivElement>(null);
  const [held, setHeld] = useState(true);
  const leaving = useRef(false);

  useEffect(() => {
    const t1 = window.setTimeout(() => setHeld(false), MIN_SPLASH_MS);
    return () => window.clearTimeout(t1);
  }, []);
  // Never keep the splash longer than this, whatever the network does.
  const [timedOut, setTimedOut] = useState(false);
  useEffect(() => {
    const t = window.setTimeout(() => setTimedOut(true), MAX_WAIT_MS);
    return () => window.clearTimeout(t);
  }, []);

  useEffect(() => {
    if (held || leaving.current || !(ready || timedOut)) return;
    leaving.current = true;
    // LauncherActivity.playOut: dip to 0.9 (110 ms), then zoom to 2.6 and fade (340 ms);
    // the curtain fades from 220 ms for 260 ms.
    void tween(icon.current, { scale: [1, 0.9] }, 110, { ease: decelerate() })
      .then(() => tween(icon.current, { scale: [0.9, 2.6], alpha: [1, 0] }, 340, { ease: accelerate(1.4) }));
    void tween(screen.current, { alpha: [1, 0] }, 260, { delay: 220, ease: (t) => t }).then(onGone);
  }, [held, ready, timedOut, onGone]);

  return (
    <div ref={screen} className="splash" aria-busy="true" aria-label={S("app_name")}>
      <div ref={icon} className="splash-icon"><AnimatedArt avd="splash_letty_anim" size={240} /></div>
    </div>
  );
}

/** activity_maintenance.xml */
export function Maintenance({ onBack }: { onBack: () => void }) {
  const [checking, setChecking] = useState(false);
  const [status, setStatus] = useState("");
  const busy = useRef(false);

  const recheck = async (byUser: boolean) => {
    if (busy.current) return;
    busy.current = true;
    if (byUser) { setChecking(true); setStatus(""); }
    const up = await serverUp();
    busy.current = false;
    if (up) { onBack(); return; }
    setChecking(false);
    if (byUser) setStatus(S("maintenance_still_down"));
  };

  useEffect(() => {
    const t = window.setInterval(() => { if (document.visibilityState === "visible") void recheck(false); }, AUTO_RETRY_MS);
    return () => window.clearInterval(t);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <main className="a-screen maintenance">
      <span className="grow" />
      <Art name="logo_letty" size={96} />
      <span className="maintenance-tag"><Tag>{S("maintenance_tag")}</Tag></span>
      <h1 className="t-headline maintenance-title">{S("maintenance_title")}</h1>
      <p className="t-body-medium maintenance-body">{S("maintenance_body")}</p>
      <span className="grow" />
      <p className="t-meta-small maintenance-status" role="status">{status}</p>
      <Btn block disabled={checking} onClick={() => void recheck(true)} className="maintenance-retry">
        {checking ? S("maintenance_checking") : S("maintenance_retry")}
      </Btn>
    </main>
  );
}
