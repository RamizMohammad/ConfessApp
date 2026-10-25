import { useCallback, useEffect, useState } from "react";
import { config } from "../config.ts";
import { api } from "../lib/api.ts";
import { biometricName } from "../lib/device.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import { Aura } from "../components/Aura.tsx";
import { Button, Lockup, Notice, Portal } from "../components/ui.tsx";
import { GuidelinesList } from "./Onboarding.tsx";
import { Feather } from "../components/icons.tsx";
import { navigate, useRoute } from "../lib/router.ts";
import { Compose } from "./Compose.tsx";
import { Drafts } from "./Drafts.tsx";
import { Feed } from "./Feed.tsx";
import { PostDetail } from "./PostDetail.tsx";

/** Inside Confess: the feed stays mounted (keeps its place) while other screens open on top. */
export function Main() {
  const { me } = useSession();
  const route = useRoute();
  const [settings, setSettings] = useState(false);
  const onFeed = route.name === "feed";

  return (
    <>
      <main className="screen main" hidden={!onFeed}>
        <div className="topbar">
          <span className="spacer" />
          <Lockup />
          <button className="icon-btn" aria-label="Me and settings" onClick={() => setSettings(true)}>
            <Aura value={me?.avatar} size={32} />
          </button>
        </div>
        <Feed active={onFeed} />
      </main>
      {route.name === "post" ? <PostDetail key={route.id} id={route.id} /> : null}
      {route.name === "write" ? <Compose key={route.draftId ?? "new"} draftId={route.draftId} /> : null}
      {route.name === "drafts" ? <Drafts /> : null}
      {onFeed ? (
        <nav className="tabbar" aria-label="Main">
          <button className="tab on" aria-current="page" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>Feed</button>
          <button className="write-fab" aria-label="Write a confession" onClick={() => navigate({ name: "write" })}><Feather /></button>
          <button className="tab" onClick={() => setSettings(true)}>Me</button>
        </nav>
      ) : null}
      {settings ? <Settings onClose={() => setSettings(false)} /> : null}
    </>
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

export function Settings({ onClose }: { onClose: () => void }) {
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
    <Portal><div className="overlay" onClick={onClose}>
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
            <Button variant="ghost" block onClick={() => { onClose(); navigate({ name: "drafts" }); }}>Drafts</Button>
            <Button variant="ghost" block onClick={() => setGuidelines(true)}>Community guidelines</Button>
            <a className="btn btn-ghost block" href={`${config.websiteUrl}/privacy`} target="_blank" rel="noopener noreferrer">Privacy policy</a>
            <Button variant="danger" block onClick={() => void signOut()}>Sign out</Button>
          </div>
        )}
      </section>
    </div></Portal>
  );
}
