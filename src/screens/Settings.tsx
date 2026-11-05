import { useCallback, useEffect, useMemo, useState } from "react";
import { config } from "../config.ts";
import { api } from "../lib/api.ts";
import { AURAS } from "../lib/auras.ts";
import { type Banner, bannerSpec, PATTERNS, resolveBanner } from "../lib/banner.ts";
import { biometricName } from "../lib/device.ts";
import { length } from "../lib/format.ts";
import { back, navigate, type SettingsPage } from "../lib/router.ts";
import { errorMessage, useSession, type Me } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { usePaged } from "../lib/usePaged.ts";
import * as U from "../lib/users.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import * as WP from "../lib/webpush.ts";
import { Aura } from "../components/Aura.tsx";
import { BannerArt } from "../components/art.tsx";
import { Bell, Lightbulb, Lock, Pencil, Phone, User, Users, UserX } from "../components/icons.tsx";
import { burstMode, setBurstMode, type BurstMode } from "../lib/bursts.ts";
import { Empty, ListStatus } from "../components/list.tsx";
import { PersonRow } from "../components/people.tsx";
import { Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, FaceIdGlyph, Header, NavRow, Notice, Portal, Toggle } from "../components/ui.tsx";
import { S } from "../lib/strings.ts";
import { GuidelinesList } from "./Onboarding.tsx";

export function SettingsScreen({ page }: { page?: SettingsPage }) {
  switch (page) {
    case "security": return <Security />;
    case "notifications": return <Notifications />;
    case "devices": return <Devices />;
    case "blocked": return <BlockedPeople />;
    case "profile": return <EditProfile />;
    case "feature": return <Feature />;
    case "appearance": return <Appearance />;
    case "guidelines": return (
      <main className="screen"><Header title="Community guidelines" onBack={back} /><GuidelinesList /></main>
    );
    default: return <SettingsHome />;
  }
}

const go = (page: SettingsPage) => navigate({ name: "settings", page });

function SettingsHome() {
  const { me, signOut } = useSession();
  const [deleting, setDeleting] = useState(false);
  const lockValue = me?.hasPassword ? "Password" : prefs.faceId ? biometricName : "Off";
  return (
    <main className="screen">
      <Header title="Settings" onBack={back} />
      <div className="stack-sm settings-intro">
        <span className="tag">Your account</span>
        <h2 className="headline">Lock, notices &amp; account</h2>
        <p className="small">Control who can open your letters and what reaches you.</p>
      </div>

      <section className="settings-group" aria-label="Profile">
        <span className="tag group-tag">Profile</span>
        <NavRow icon={<Aura value={me?.avatar} size={28} />} label="Avatar, banner & about" sub="Change how you appear" onClick={() => go("profile")} />
      </section>
      <section className="settings-group" aria-label="Lock and privacy">
        <span className="tag group-tag">Lock &amp; privacy</span>
        <NavRow icon={<Lock />} label="Lock & privacy" sub={`${biometricName}, password lock`} value={lockValue} onClick={() => go("security")} />
        <NavRow icon={<Phone />} label="Signed-in devices" sub="See where you're signed in, sign out the rest" onClick={() => go("devices")} />
        <NavRow icon={<UserX />} label="Blocked people" onClick={() => go("blocked")} />
      </section>
      <section className="settings-group" aria-label="Appearance">
        <span className="tag group-tag">Appearance</span>
        <NavRow icon={<Users />} label="Bursts" sub="When someone posts several confessions close together" value={BURST_LABEL[burstMode()]}
                onClick={() => go("appearance")} />
      </section>
      <section className="settings-group" aria-label="Notifications">
        <span className="tag group-tag">Notifications</span>
        <NavRow icon={<Bell />} label="Choose notifications" sub="Felt, responses, followers, new letters, nudges" onClick={() => go("notifications")} />
      </section>
      <section className="settings-group" aria-label="Support">
        <span className="tag group-tag">Support</span>
        <NavRow icon={<Lightbulb />} label={S("settings_tour")} sub={S("settings_tour_sub")}
                onClick={() => window.dispatchEvent(new CustomEvent("confess:tour"))} />
        <NavRow icon={<Lightbulb />} label="Request a feature" sub="Tell us what would make Confess better" onClick={() => go("feature")} />
        <NavRow icon={<Pencil />} label="Community guidelines" onClick={() => go("guidelines")} />
        <a className="setting-row nav-row" href={`${config.websiteUrl}/privacy`} target="_blank" rel="noopener noreferrer">
          <span className="row-icon"><User /></span><span className="grow setting-label">Privacy policy</span><span className="chev" aria-hidden>↗</span>
        </a>
      </section>
      <section className="settings-group" aria-label="Account">
        <span className="tag group-tag">Account</span>
        <Button variant="outline" block onClick={() => void signOut()}>Sign out</Button>
      </section>
      <section className="settings-group danger-zone" aria-label="Danger zone">
        <span className="tag group-tag danger-tag">Danger zone</span>
        <p className="small">Permanently removes your profile, confessions, responses and reactions. As Indian law requires, a restricted copy of your sign-up details is kept for 180 days, then deleted. This can't be undone.</p>
        <Button variant="danger" block onClick={() => setDeleting(true)}>Delete my account</Button>
      </section>
      <p className="meta center-text">Confess for iPhone · web</p>
      {deleting && me ? <DeleteAccount me={me} onClose={() => setDeleting(false)} /> : null}
    </main>
  );
}

function DeleteAccount({ me, onClose }: { me: Me; onClose: () => void }) {
  const { accountDeleted } = useSession();
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const ok = typed.trim().toLowerCase() === me.alias.toLowerCase();
  return (
    <Portal>
      <div className="overlay center" onClick={onClose}>
        <section className="dialog stack" role="alertdialog" aria-modal="true" aria-labelledby="del-title" onClick={(e) => e.stopPropagation()}>
          <h2 id="del-title" className="title">Delete your account?</h2>
          <p className="small">Everything you've written, felt and responded with disappears for everyone. To confirm, type your alias: <b>{me.alias}</b></p>
          <input className="input" value={typed} onChange={(e) => setTyped(e.target.value)} placeholder="Your alias"
                 autoCapitalize="none" autoCorrect="off" spellCheck={false} aria-label="Your alias" />
          <Notice>{error}</Notice>
          <Button variant="danger" block disabled={!ok} busy={busy} onClick={async () => {
            setBusy(true);
            setError(undefined);
            try {
              await U.deleteAccount();
              accountDeleted();
            } catch (e) {
              setError(errorMessage(e));
              setBusy(false);
            }
          }}>{busy ? "Deleting…" : "Delete forever"}</Button>
          <Button variant="ghost" block onClick={onClose}>Cancel</Button>
        </section>
      </div>
    </Portal>
  );
}

/* ── Lock & privacy ─────────────────────────────────────────────────────── */

interface Passkey {
  id: string;
  device: string;
  syncs: boolean;
  createdAt: string | null;
  lastUsedAt: string | null;
}

const when = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "never";

function Security() {
  const { me, enableFaceId, canUseFaceId, patchMe } = useSession();
  const [keys, setKeys] = useState<Passkey[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [form, setForm] = useState<"set" | "change" | "remove" | null>(null);
  const toast = useToast();

  const load = useCallback(async () => {
    try {
      const { passkeys } = await api<{ passkeys: Passkey[] }>("/auth/passkey");
      setKeys(passkeys);
      if (!passkeys.length) prefs.faceId = false;
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function turnOn() {
    setError(undefined);
    setBusy("add");
    try {
      await enableFaceId();
      await load();
      toast(`${biometricName} is on.`);
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
    <main className="screen">
      <Header title="Lock & privacy" onBack={back} />
      <p className="small settings-lead">Control who can open your letters on this phone. Confess locks again after 5 minutes away, and hides itself in the app switcher.</p>

      <section className="settings-group" aria-label={biometricName}>
        <span className="tag group-tag">{biometricName}</span>
        <Notice>{error}</Notice>
        {keys === null ? <span className="meta">Loading…</span> : null}
        {keys?.length ? (
          <div className="list">
            {keys.map((k) => (
              <div className="list-row" key={k.id}>
                <FaceIdGlyph className="row-glyph" />
                <div className="grow stack-xs">
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
      </section>

      <section className="settings-group" aria-label="Password lock">
        <span className="tag group-tag">Password lock</span>
        <p className="small">{me?.hasPassword ? "On — asks for your password when Confess opens." : "Off — anyone holding this phone can open Confess."}</p>
        {me?.hasPassword ? (
          <>
            <Button variant="outline" block onClick={() => setForm("change")}>Change password</Button>
            <Button variant="danger" block onClick={() => setForm("remove")}>Turn off password lock</Button>
          </>
        ) : <Button variant="outline" block onClick={() => setForm("set")}>Set a password</Button>}
      </section>

      {form ? (
        <PasswordSheet mode={form} onClose={() => setForm(null)}
                       onDone={(has) => { patchMe({ hasPassword: has }); setForm(null);
                                          toast(form === "change" ? "Password changed." : has ? "Password lock is on." : "Password lock is off."); }} />
      ) : null}
    </main>
  );
}

export const PASSWORD_MIN = 8, PASSWORD_MAX = 64;

function PasswordSheet({ mode, onClose, onDone }: { mode: "set" | "change" | "remove"; onClose: () => void; onDone: (hasPassword: boolean) => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const needNew = mode !== "remove";
  const needCurrent = mode !== "set";
  const lengthOk = next.length >= PASSWORD_MIN && next.length <= PASSWORD_MAX;
  const problem = !needNew ? null
    : next && !lengthOk ? `Use ${PASSWORD_MIN}–${PASSWORD_MAX} characters.`
      : again && again !== next ? "The passwords don't match."
        : mode === "change" && next && next === current ? "Pick a password different from the current one." : null;
  const ready = (!needCurrent || current.length > 0) && (!needNew || (lengthOk && again === next && !problem));

  async function save() {
    setBusy(true);
    setError(undefined);
    try {
      if (mode === "set") await U.setPassword(next);
      else if (mode === "change") await U.changePassword(current, next);
      else await U.removePassword(current);
      onDone(mode !== "remove");
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  const title = mode === "set" ? "Set a password" : mode === "change" ? "Change password" : "Turn off the password lock?";
  return (
    <Sheet title={title} onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); if (ready) void save(); }}>
        <div className="stack-sm">
          <h2 className="title">{title}</h2>
          <p className="small">
            {mode === "set" ? "Lock Confess so only you can open your letters on this phone."
              : mode === "change" ? "Pick a new password. You'll use it the next time Confess locks."
                : "Enter your current password."}
          </p>
        </div>
        {needCurrent ? (
          <label className="field"><span className="tag">Current password</span>
            <input className="input" type="password" autoComplete="current-password" value={current} maxLength={PASSWORD_MAX}
                   onChange={(e) => setCurrent(e.target.value)} placeholder="Your current password" />
          </label>
        ) : null}
        {needNew ? (
          <>
            <label className="field"><span className="tag">New password</span>
              <input className="input" type="password" autoComplete="new-password" value={next} maxLength={PASSWORD_MAX}
                     onChange={(e) => setNext(e.target.value)} placeholder={`At least ${PASSWORD_MIN} characters`} />
            </label>
            <label className="field"><span className="tag">Confirm new password</span>
              <input className="input" type="password" autoComplete="new-password" value={again} maxLength={PASSWORD_MAX}
                     onChange={(e) => setAgain(e.target.value)} placeholder="Type it again" />
            </label>
          </>
        ) : null}
        {problem ? <p className="small error-text" role="status">{problem}</p> : null}
        {needNew ? <p className="small">Your password only locks the app. If you forget it, you can reset it by email from the unlock screen.</p> : null}
        <Notice>{error}</Notice>
        <Button type="submit" block variant={mode === "remove" ? "danger" : "primary"} busy={busy} disabled={!ready}>
          {mode === "set" ? "Turn on lock" : mode === "change" ? "Change password" : "Turn off"}
        </Button>
      </form>
    </Sheet>
  );
}

/* ── Notifications ──────────────────────────────────────────────────────── */

const PREF_ROWS: { section: string; rows: { key: keyof U.NotificationPrefs; label: string; sub: string }[] }[] = [
  { section: "About you", rows: [
    { key: "likes", label: "Felt", sub: "Someone felt your confession" },
    { key: "comments", label: "Responses", sub: "Someone responded to your confession" },
    { key: "follows", label: "New followers", sub: "Someone started following you" },
  ] },
  { section: "New letters", rows: [
    { key: "followingPosts", label: "From people you follow", sub: "A writer you follow posted a new letter" },
    { key: "newPosts", label: "From everyone", sub: "Any new confession on Confess" },
    { key: "bookChapters", label: "New chapters", sub: "When a book in your library gets a chapter" },
  ] },
  { section: "Other", rows: [
    { key: "nudges", label: "Gentle nudges", sub: "A few quiet reminders a day to write" },
  ] },
];

function Notifications() {
  const { me, patchMe, refreshMe } = useSession();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  useEffect(() => { void refreshMe(); }, [refreshMe]);
  const prefsNow = me?.notifications;

  // Switches move at once; a failure puts them back.
  async function set(key: keyof U.NotificationPrefs, on: boolean) {
    if (!prefsNow) return;
    const before = prefsNow;
    patchMe({ notifications: { ...before, [key]: on } });
    setBusy(key);
    setError(undefined);
    try {
      patchMe({ notifications: await U.setNotificationPrefs({ [key]: on }) });
    } catch (e) {
      patchMe({ notifications: before });
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function setMaster(on: boolean) {
    const before = me?.pushEnabled ?? true;
    patchMe({ pushEnabled: on });
    setBusy("master");
    setError(undefined);
    try {
      await api("/push/enabled", { method: "POST", body: { enabled: on } });
    } catch (e) {
      patchMe({ pushEnabled: before });
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="screen">
      <Header title="Notifications" onBack={back} />
      <ThisIPhone />
      <Notice kind="info">The switches below choose which notifications your account gets — here and on any Android phone you use. Everything still shows in Activity.</Notice>
      <Notice>{error}</Notice>
      {!prefsNow ? <div className="list-loading"><span className="spinner" aria-label="Loading" /></div> : (
        <>
          <section className="settings-group">
            <Toggle label="Push notifications" sub="Turn every push on or off for this account" on={me?.pushEnabled ?? true}
                    busy={busy === "master"} onChange={(on) => void setMaster(on)} />
          </section>
          {PREF_ROWS.map((g) => (
            <section key={g.section} className="settings-group" aria-label={g.section}>
              <span className="tag group-tag">{g.section}</span>
              {g.rows.map((r) => (
                <Toggle key={r.key} label={r.label} sub={r.sub} on={!!prefsNow[r.key]} busy={busy === r.key}
                        disabled={me?.pushEnabled === false} onChange={(on) => void set(r.key, on)} />
              ))}
            </section>
          ))}
        </>
      )}
    </main>
  );
}

/* ── Notifications on this iPhone (web push) ────────────────────────────── */

function ThisIPhone() {
  const [state, setState] = useState<WP.PushState | null>(null);
  const [priv, setPriv] = useState(WP.isPrivate());
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const toast = useToast();
  useEffect(() => { void WP.pushState().then(setState); }, []);
  if (state === null || state === "unavailable" || state === "unsupported") return null;

  async function run(key: string, f: () => Promise<void>) {
    setBusy(key);
    setError(undefined);
    try {
      await f();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="settings-group" aria-label="On this iPhone">
      <span className="tag group-tag">On this iPhone</span>
      {state === "install" ? (
        <Notice kind="info">To get notifications, add Confess to your Home Screen (Share → Add to Home Screen), open it from there, then turn them on here.</Notice>
      ) : null}
      {state === "denied" ? (
        <Notice kind="info">Notifications are blocked for Confess. Turn them on in iPhone Settings → Notifications → Confess, then come back.</Notice>
      ) : null}
      {state === "off" ? (
        <div className="stack-sm">
          <p className="small">Know when your words land — someone feels your confession, responds, or follows you.</p>
          <Button block busy={busy === "on"} onClick={() => void run("on", async () => {
            const next = await WP.enablePush(priv);
            setState(next);
            if (next === "on") toast("Notifications are on.");
          })}>Turn on notifications</Button>
        </div>
      ) : null}
      {state === "on" ? (
        <>
          <Toggle label="Notifications on this iPhone" sub="Felt, responses, followers and the rest you choose below" on
                  busy={busy === "off"} onChange={() => void run("off", async () => { await WP.disablePush(); setState("off"); toast("Notifications are off on this iPhone."); })} />
          <Toggle label="Keep them private" sub={priv ? "The lock screen only says “Something new is waiting for you”" : "The lock screen shows who and what, like on Android"}
                  on={priv} busy={busy === "private"}
                  onChange={(on) => { setPriv(on); void run("private", () => WP.setPrivate(on)).catch(() => setPriv(!on)); }} />
          <Button variant="outline" block busy={busy === "test"} onClick={() => void run("test", async () => {
            toast((await WP.testPush()) ? "Sent — it should arrive in a moment." : "Couldn't send. Turn notifications off and on again.");
          })}>Send a test notification</Button>
        </>
      ) : null}
      <Notice>{error}</Notice>
    </section>
  );
}

/* ── Devices ────────────────────────────────────────────────────────────── */

function Devices() {
  const [list, setList] = useState<U.DeviceSession[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const toast = useToast();
  const load = useCallback(async () => {
    try {
      setList((await U.sessions()).sessions);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const others = list?.filter((s) => !s.current) ?? [];

  return (
    <main className="screen">
      <Header title="Signed-in devices" onBack={back} />
      <p className="small settings-lead">Wherever Confess is signed in to your account. Don't recognise one? Sign it out.</p>
      <Notice>{error}</Notice>
      {list === null && !error ? <div className="list-loading"><span className="spinner" aria-label="Loading" /></div> : null}
      <div className="list">
        {list?.map((s) => (
          <div className="list-row" key={s.id}>
            <Phone />
            <div className="grow stack-xs">
              <span>{U.deviceName(s)}{s.current ? <span className="tag current-tag"> This device</span> : null}</span>
              <span className="meta">Signed in {when(s.createdAt)} · last active {when(s.lastUsedAt)}</span>
            </div>
            {s.current ? null : (
              <Button variant="ghost" busy={busy === s.id} onClick={async () => {
                setBusy(s.id);
                try { await U.revokeSession(s.id); toast("Signed out that device."); await load(); }
                catch (e) { setError(errorMessage(e)); }
                finally { setBusy(null); }
              }}>Sign out</Button>
            )}
          </div>
        ))}
      </div>
      {others.length > 1 ? (
        <Button variant="danger" block busy={busy === "all"} onClick={async () => {
          setBusy("all");
          try { const r = await U.revokeOthers(); toast(`Signed out ${r.revoked} device${r.revoked === 1 ? "" : "s"}.`); await load(); }
          catch (e) { setError(errorMessage(e)); }
          finally { setBusy(null); }
        }}>Sign out every other device</Button>
      ) : null}
    </main>
  );
}

/* ── Blocked ────────────────────────────────────────────────────────────── */

function BlockedPeople() {
  const toast = useToast();
  const list = usePaged<U.Blocked>(async (c) => {
    const r = await U.blocked(c);
    return { items: r.users, next: r.nextCursor };
  }, (b) => b.alias);
  return (
    <main className="screen">
      <Header title="Blocked people" onBack={back} />
      <p className="small settings-lead">They can't see your confessions, responses or stories, and you can't see theirs. They're never told.</p>
      <div className="people-list">
        {list.items.map((b) => (
          <PersonRow key={b.alias} person={{ alias: b.alias, avatar: b.avatar, about: "" }}
                     right={<UnblockButton alias={b.alias} onDone={() => { list.setItems((cur) => cur.filter((x) => x.alias !== b.alias)); toast(`Unblocked @${b.alias}`); }} />} />
        ))}
      </div>
      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()}
                  empty={<Empty title="No one blocked" body="When you block someone, they show up here." />} />
    </main>
  );
}

function UnblockButton({ alias, onDone }: { alias: string; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  return (
    <Button variant="outline" className="btn-small" busy={busy} onClick={async () => {
      setBusy(true);
      try { await U.unblock(alias); onDone(); } catch (e) { toast(errorMessage(e)); setBusy(false); }
    }}>Unblock</Button>
  );
}

/* ── Profile: avatar, banner, about ─────────────────────────────────────── */

const ABOUT_MAX = 50;

function EditProfile() {
  const { me, patchMe } = useSession();
  const [about, setAbout] = useState(me?.about ?? "");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [bannerSheet, setBannerSheet] = useState(false);
  const toast = useToast();
  const banner = useMemo(() => resolveBanner(me?.banner, me?.avatar, me?.alias), [me?.banner, me?.avatar, me?.alias]);
  if (!me) return null;
  const aboutChanged = about.trim().replace(/\s+/g, " ") !== me.about;

  async function saveAbout() {
    setBusy("about");
    setError(undefined);
    try {
      const r = await U.setAbout(about.trim().replace(/\s+/g, " "));
      patchMe({ about: r.about });
      setAbout(r.about);
      toast("Saved.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function useAura(id: string) {
    setBusy(id);
    setError(undefined);
    try {
      patchMe({ avatar: (await U.setAvatar(id)).avatar });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="screen">
      <Header title="Avatar, banner & about" onBack={back} />
      <p className="quote center-text edit-lead">“Change how you appear in the dark. Your letters stay yours.”</p>

      <div className="edit-preview">
        <BannerArt banner={banner} fade={false} animated className="edit-banner" />
        <button className="edit-banner-btn" onClick={() => setBannerSheet(true)}>Change banner</button>
        <div className="edit-avatar"><Aura value={me.avatar} size={88} label="Your avatar" /></div>
      </div>

      <section className="settings-group">
        <span className="tag group-tag">Avatar</span>
        <Button variant="primary" block onClick={() => navigate({ name: "avatar" })}>
          {me.avatar.startsWith("av1:") ? "Edit your avatar" : "Design your avatar"}
        </Button>
        <span className="tag">Or choose an aura</span>
        <div className="aura-grid">
          {AURAS.map((a) => (
            <button key={a.id} className={`aura-pick${me.avatar === a.id ? " on" : ""}`} aria-pressed={me.avatar === a.id}
                    aria-label={a.name} disabled={busy !== null} onClick={() => void useAura(a.id)}>
              <Aura value={a.id} size={64} />
              {busy === a.id ? <span className="spinner pick-spinner" aria-hidden /> : null}
            </button>
          ))}
        </div>
      </section>

      <section className="settings-group">
        <label className="field">
          <span className="tag">About</span>
          <input className="input" value={about} maxLength={ABOUT_MAX + 10} placeholder="A line about you (optional)"
                 onChange={(e) => setAbout(e.target.value)} />
          <span className={`meta counter${length(about) > ABOUT_MAX ? " error-text" : ""}`}>{length(about)}/{ABOUT_MAX}</span>
        </label>
        <Notice>{error}</Notice>
        <Button block busy={busy === "about"} disabled={!aboutChanged || length(about) > ABOUT_MAX} onClick={saveAbout}>Save about</Button>
      </section>

      <section className="settings-group">
        <span className="tag group-tag">Alias</span>
        <p className="body">@{me.alias}</p>
        <p className="small">Aliases can't be changed — they keep your letters and responses linked.</p>
      </section>

      {bannerSheet ? <BannerSheet me={me} onClose={() => setBannerSheet(false)} onSaved={(b) => patchMe({ banner: b })} /> : null}
    </main>
  );
}

function BannerSheet({ me, onClose, onSaved }: { me: Me; onClose: () => void; onSaved: (spec: string) => void }) {
  const [b, setB] = useState<Banner>(() => resolveBanner(me.banner, me.avatar, me.alias));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();
  const auto = resolveBanner("", me.avatar, me.alias);

  async function save() {
    setBusy(true);
    setError(undefined);
    try {
      const r = await U.setBanner(bannerSpec(b));
      onSaved(r.banner);
      toast("Banner saved");
      onClose();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Sheet title="Your banner" onClose={onClose}>
      <div className="stack">
        <div className="stack-sm">
          <h2 className="title">Your banner</h2>
          <p className="small">Drawn just for your profile. Pick a pattern and a colour.</p>
        </div>
        <div className="banner-preview"><BannerArt banner={b} fade={false} animated /></div>
        <span className="meta">{PATTERNS[b.pattern]} · {AURAS[b.colour]?.name}{b.automatic ? " · matching your avatar" : ""}</span>
        <span className="tag">Pattern</span>
        <div className="banner-patterns" role="radiogroup" aria-label="Pattern">
          {PATTERNS.map((name, i) => (
            <button key={name} role="radio" aria-checked={b.pattern === i} aria-label={name}
                    className={`banner-thumb${b.pattern === i ? " on" : ""}`}
                    onClick={() => setB({ ...b, pattern: i, automatic: false })}>
              <BannerArt banner={{ pattern: i, colour: b.colour, automatic: false }} fade={false} />
              <span className="thumb-label">{name}</span>
            </button>
          ))}
        </div>
        <span className="tag">Colour</span>
        <div className="banner-colours" role="radiogroup" aria-label="Colour">
          {AURAS.map((a, i) => (
            <button key={a.id} role="radio" aria-checked={b.colour === i} aria-label={a.name}
                    className={`colour-dot${b.colour === i ? " on" : ""}`} onClick={() => setB({ ...b, colour: i, automatic: false })}>
              <Aura value={a.id} size={34} />
            </button>
          ))}
        </div>
        <Toggle label="Match my avatar" sub={b.automatic ? "Matching your avatar" : "Follows your avatar's colours"} on={b.automatic}
                onChange={(on) => setB(on ? auto : { ...b, automatic: false })} />
        <Notice>{error}</Notice>
        <Button block busy={busy} onClick={save}>Save banner</Button>
      </div>
    </Sheet>
  );
}

/* ── Request a feature ──────────────────────────────────────────────────── */

const CATEGORIES = [
  { key: "feed", label: "Feed" }, { key: "privacy", label: "Privacy" }, { key: "comments", label: "Responses" }, { key: "other", label: "Something else" },
] as const;

function Feature() {
  const [category, setCategory] = useState<string>("other");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();
  return (
    <main className="screen">
      <Header title="Request a feature" onBack={back} />
      <div className="stack">
        <h2 className="title">What would make Confess better for you?</h2>
        <span className="tag">About</span>
        <div className="chips">
          {CATEGORIES.map((c) => (
            <button key={c.key} className={`chip${category === c.key ? " on" : ""}`} aria-pressed={category === c.key}
                    onClick={() => setCategory(c.key)}>{c.label}</button>
          ))}
        </div>
        <textarea className="input feature-text" rows={6} maxLength={1000} value={text} placeholder="Describe the idea…"
                  onChange={(e) => setText(e.target.value)} aria-label="Your idea" />
        <span className="meta counter">{length(text)}/1000</span>
        <Notice>{error}</Notice>
        <Button block busy={busy} disabled={!text.trim()} onClick={async () => {
          setBusy(true);
          setError(undefined);
          try { await U.requestFeature(category, text.trim()); toast("Thank you — we read every one."); back(); }
          catch (e) { setError(errorMessage(e)); setBusy(false); }
        }}>{busy ? "Sending…" : "Send idea"}</Button>
      </div>
    </main>
  );
}

/* ── Appearance: bursts ─────────────────────────────────────────────────── */

const BURST_LABEL: Record<BurstMode, string> = { slider: "Slider", stack: "Stack", separate: "One by one" };

function Appearance() {
  const [mode, setMode] = useState<BurstMode>(burstMode);
  const options: { key: BurstMode; sub: string }[] = [
    { key: "slider", sub: "Swipe through them in one card, oldest first" },
    { key: "stack", sub: "Up to 3: the slider. More: one card, tap to read from the first" },
    { key: "separate", sub: "Every confession its own card" },
  ];
  return (
    <main className="screen">
      <Header title="Appearance" onBack={back} />
      <section className="settings-group" aria-label="Bursts">
        <span className="tag group-tag">Bursts</span>
        <p className="small">When someone posts several confessions close together.</p>
        <div className="choice-list" role="radiogroup">
          {options.map((o) => (
            <label key={o.key} className={`choice release-choice${mode === o.key ? " on" : ""}`}>
              <input type="radio" name="bursts" checked={mode === o.key} onChange={() => { setMode(o.key); setBurstMode(o.key); }} />
              <span className={`burst-demo demo-${o.key}`} aria-hidden><i /><i /><i /></span>
              <span className="stack-xs"><span>{BURST_LABEL[o.key]}</span><span className="small">{o.sub}</span></span>
            </label>
          ))}
        </div>
      </section>
    </main>
  );
}
