import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { useRoute } from "./lib/router.ts";
import { isIOS, isStandalone } from "./lib/device.ts";
import { SessionProvider, useSession } from "./lib/session.tsx";
import { prefs } from "./lib/storage.ts";
import { Boot, InstallGuide } from "./screens/Entry.tsx";
import { Welcome } from "./screens/Welcome.tsx";
import { Maintenance, Splash, useServerCheck } from "./screens/Launch.tsx";
import { Main } from "./screens/Home.tsx";
import { ToastProvider } from "./components/toast.tsx";
import { AliasStep, AvatarStep, Guidelines } from "./screens/Onboarding.tsx";
import { FaceIdOffer, LockScreen, ReauthDialog } from "./screens/Secure.tsx";
import { Button } from "./components/ui.tsx";

/** /dev/design: every token and component on one page (no account needed). Loaded only when opened. */
const DesignCheck = lazy(() => import("./screens/DesignCheck.tsx"));
/** /dev/screen: what the screen map measured on this device (no account needed). */
const ScreenCheck = lazy(() => import("./screens/ScreenCheck.tsx"));

function Screens() {
  const { phase } = useSession();
  const route = useRoute();
  const [installSkipped, setInstallSkipped] = useState(prefs.installDismissed);
  const [server] = useServerCheck();
  const [splash, setSplash] = useState(true);
  const splashGone = useCallback(() => setSplash(false), []);

  if (route.name === "design") return <Suspense fallback={null}><DesignCheck /></Suspense>;
  if (route.name === "screen") return <Suspense fallback={null}><ScreenCheck /></Suspense>;

  // The splash stays up until we know where to go (session checked, server asked).
  const ready = phase.name !== "booting" && server !== "checking";
  let screen = null;
  if (!splash) {
    // Back from maintenance: start again from the top, as the Android launcher does.
    if (server === "down") screen = <Maintenance onBack={() => window.location.reload()} />;
    else screen = <Routed phase={phase} installSkipped={installSkipped}
                          skipInstall={() => { prefs.installDismissed = true; setInstallSkipped(true); }} />;
  }
  return (
    <>
      <div className="statusbar-shim" aria-hidden />
      {screen ? <div className="routed">{screen}</div> : null}
      {splash ? <Splash ready={ready} onGone={splashGone} /> : null}
    </>
  );
}

function Routed({ phase, installSkipped, skipInstall }:
  { phase: ReturnType<typeof useSession>["phase"]; installSkipped: boolean; skipInstall: () => void }) {
  switch (phase.name) {
    case "booting":
      return <Boot />;
    case "signedOut":
      if (isIOS && !isStandalone() && !installSkipped) return <InstallGuide onSkip={skipInstall} />;
      return <Welcome />;
    case "onboarding":
      return phase.step === "guidelines" ? <Guidelines /> : phase.step === "alias" ? <AliasStep /> : <AvatarStep />;
    case "faceIdOffer":
      return <FaceIdOffer />;
    case "locked":
      return <LockScreen />;
    case "ready":
      return <Main />;
  }
}

/** "A new version is ready" — shown when an updated service worker is waiting. */
function UpdateToast() {
  const [waiting, setWaiting] = useState<ServiceWorker | null>(null);
  useEffect(() => {
    const on = (e: Event) => setWaiting((e as CustomEvent<ServiceWorker>).detail);
    window.addEventListener("confess:update", on);
    return () => window.removeEventListener("confess:update", on);
  }, []);
  if (!waiting) return null;
  return (
    <div className="toast" role="status">
      <span>A new version of Confess is ready</span>
      <Button onClick={() => waiting.postMessage({ type: "SKIP_WAITING" })}>Refresh</Button>
    </div>
  );
}

export default function App() {
  return (
    <SessionProvider>
      <ToastProvider>
        <Screens />
        <ReauthDialog />
        <UpdateToast />
      </ToastProvider>
    </SessionProvider>
  );
}
