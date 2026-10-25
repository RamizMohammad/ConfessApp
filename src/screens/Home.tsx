import { useCallback, useEffect, useState } from "react";
import { config } from "../config.ts";
import { api } from "../lib/api.ts";
import { biometricName } from "../lib/device.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import { Aura } from "../components/Aura.tsx";
import { Button, Lockup, Notice } from "../components/ui.tsx";
import { GuidelinesList } from "./Onboarding.tsx";

/** Inside Confess. The feed, compose, profile… arrive in phase 4; this is the shell. */
export function Home() {
  const { me } = useSession();
  const [settings, setSettings] = useState(false);

  return (
    <main className="screen">
      <div className="topbar">
        <span className="spacer" />
        <Lockup />
        <button className="icon-btn" aria-label="Settings" onClick={() => setSettings(true)}>
          <Aura value={me?.avatar} size={32} />
        </button>
      </div>
      <div className="stack">
        <div className="paper stack-sm">
          <span className="meta">@{me?.alias} · just now</span>
          <p className="quote">“You made it in. The room is quiet for now — your letters arrive with the next update.”</p>
        </div>
        <p className="small muted">You're on the Confess web app. Confessions, responses, stories and books are on their way here.</p>
      </div>
      {settings ? <Settings onClose={() => setSettings(false)} /> : null}
    </main>
  );
}

interface Passkey {
  id: string;
  device: string;
  syncs: boolean;
  createdAt: string | null;
  lastUsedAt: string | null;
}

function when(iso: string | null): string {
  if (!iso) return "never";
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

function Settings({ onClose }: { onClose: () => void }) {
  const { me, enableFaceId, signOut, canUseFaceId } = useSession();
  const [keys, setKeys] = useState<Passkey[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [guidelines, setGuidelines] = useState(false);

  const load = useCallback(async () => {
    try {
      const { passkeys } = await api<{ passkeys: Passkey[] }>("/auth/passkey");
      setKeys(passkeys);
      if (!passkeys.length) prefs.faceId = false;
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function turnOn() {
    setError(undefined);
    setBusy("add");
    try {
      await enableFaceId();
      await load();
    } catch (e) {
      if (!(e instanceof FaceIdError && e.kind === "cancelled")) setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function remove(id: string) {
    setError(undefined);
    setBusy(id);
    try {
      await api(`/auth/passkey/${encodeURIComponent(id)}`, { method: "DELETE" });
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="overlay" onClick={onClose}>
      <section className="sheet" role="dialog" aria-modal="true" aria-label="Settings" onClick={(e) => e.stopPropagation()}>
        <div className="grabber" />
        {guidelines ? (
          <div className="stack">
            <Button variant="ghost" onClick={() => setGuidelines(false)}>← Back</Button>
            <h2 className="title">Community guidelines</h2>
            <GuidelinesList />
          </div>
        ) : (
          <div className="stack">
            <div className="row">
              <Aura value={me?.avatar} size={56} />
              <div className="stack-sm">
                <span className="title">@{me?.alias}</span>
                {me?.about ? <span className="small">{me.about}</span> : null}
              </div>
            </div>

            <hr className="divider" />
            <span className="tag">{biometricName}</span>
            <Notice>{error}</Notice>
            {keys === null ? <span className="meta">Loading…</span> : null}
            {keys?.length ? (
              <div className="list">
                {keys.map((k) => (
                  <div className="list-row" key={k.id}>
                    <div className="grow stack-sm">
                      <span>{k.device || "This device"}</span>
                      <span className="meta">Added {when(k.createdAt)} · last used {when(k.lastUsedAt)}{k.syncs ? " · syncs with iCloud" : ""}</span>
                    </div>
                    <Button variant="ghost" busy={busy === k.id} onClick={() => void remove(k.id)}>Remove</Button>
                  </div>
                ))}
              </div>
            ) : keys ? <p className="small">Off. Turn it on to unlock Confess with a glance.</p> : null}
            {canUseFaceId && keys && !prefs.faceId ? (
              <Button variant="outline" block busy={busy === "add"} onClick={turnOn}>Turn on {biometricName} on this device</Button>
            ) : null}

            <hr className="divider" />
            <Button variant="ghost" block onClick={() => setGuidelines(true)}>Community guidelines</Button>
            <a className="btn btn-ghost block" href={`${config.websiteUrl}/privacy`} target="_blank" rel="noopener noreferrer">Privacy policy</a>
            <Button variant="danger" block onClick={() => void signOut()}>Sign out</Button>
          </div>
        )}
      </section>
    </div>
  );
}
