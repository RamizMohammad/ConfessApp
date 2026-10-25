import { useEffect, useState } from "react";
import { isIOS, isStandalone } from "./lib/device.ts";
import { SessionProvider, useSession } from "./lib/session.tsx";
import { prefs } from "./lib/storage.ts";
import { Boot, InstallGuide, Welcome } from "./screens/Entry.tsx";
import { Home } from "./screens/Home.tsx";
import { AliasStep, AvatarStep, Guidelines } from "./screens/Onboarding.tsx";
import { FaceIdOffer, LockScreen, ReauthDialog } from "./screens/Secure.tsx";
import { Button } from "./components/ui.tsx";

function Screens() {
  const { phase } = useSession();
  const [installSkipped, setInstallSkipped] = useState(prefs.installDismissed);

  switch (phase.name) {
    case "booting":
      return <Boot />;
    case "signedOut":
      if (isIOS && !isStandalone() && !installSkipped) {
        return <InstallGuide onSkip={() => { prefs.installDismissed = true; setInstallSkipped(true); }} />;
      }
      return <Welcome />;
    case "onboarding":
      return phase.step === "guidelines" ? <Guidelines /> : phase.step === "alias" ? <AliasStep /> : <AvatarStep />;
    case "faceIdOffer":
      return <FaceIdOffer />;
    case "locked":
      return <LockScreen />;
    case "ready":
      return <Home />;
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
      <Screens />
      <ReauthDialog />
      <UpdateToast />
    </SessionProvider>
  );
}
