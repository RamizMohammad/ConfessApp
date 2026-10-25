import { useEffect, useState, type FormEvent } from "react";
import { ApiError, api } from "../lib/api.ts";
import { biometricName } from "../lib/device.ts";
import { errorMessage, reauthWithFaceId, reauthWithGoogle, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import { Aura } from "../components/Aura.tsx";
import { Button, FaceIdGlyph, Lockup, Notice } from "../components/ui.tsx";

const cancelled = (e: unknown) => e instanceof FaceIdError && e.kind === "cancelled";

/* ── "Unlock Confess with Face ID" (after sign-up / first sign-in) ──────── */

export function FaceIdOffer() {
  const { enableFaceId, skipFaceId } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  async function turnOn() {
    setError(undefined);
    setBusy(true);
    try {
      await enableFaceId();
    } catch (e) {
      if (!cancelled(e)) setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <main className="screen center">
      <div className="grow" />
      <div className="stack">
        <FaceIdGlyph />
        <h1 className="headline">Unlock Confess with {biometricName}</h1>
        <p className="body">Next time, just look at your phone. Your face never leaves it — Confess only ever gets a yes.</p>
        <Notice>{error}</Notice>
      </div>
      <div className="grow" />
      <div className="stack-sm">
        <Button block busy={busy} onClick={turnOn}>Turn on {biometricName}</Button>
        <Button variant="ghost" block onClick={skipFaceId}>Not now</Button>
      </div>
    </main>
  );
}

/* ── Lock screen ────────────────────────────────────────────────────────── */

export function LockScreen() {
  const { me, unlockWithFaceId, unlockWithPassword, signOut, canUseFaceId } = useSession();
  const faceId = prefs.faceId && canUseFaceId;
  const [busy, setBusy] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [info, setInfo] = useState<string>();

  async function face() {
    setError(undefined);
    setBusy(true);
    try {
      await unlockWithFaceId();
    } catch (e) {
      if (!cancelled(e)) setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    if (!password) return;
    setError(undefined);
    setBusy(true);
    try {
      if (!(await unlockWithPassword(password))) setError("That's not it. Try again.");
    } catch (e) {
      setError(e instanceof ApiError && e.code === "ACCOUNT_LOCKED" ? e.message : errorMessage(e));
    } finally {
      setBusy(false);
      setPassword("");
    }
  }

  async function forgot() {
    setError(undefined);
    try {
      await api("/auth/forgot-password", { method: "POST" });
      setInfo("We've emailed a reset link to the address you signed in with.");
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <main className="screen center">
      <div className="topbar"><span className="spacer" /><Lockup /><span className="spacer" /></div>
      <div className="grow" />
      <div className="stack">
        <div className="lock-avatar"><Aura value={me?.avatar} size={84} /></div>
        <span className="tag">Locked</span>
        <h1 className="headline">Welcome back,<br /><span className="amber">@{me?.alias}</span></h1>
        {faceId ? (
          <Button block busy={busy} onClick={face}><FaceIdGlyph className="" /> Unlock with {biometricName}</Button>
        ) : null}
        {me?.hasPassword ? (
          <form className="stack-sm" onSubmit={submit}>
            {faceId ? <span className="meta">or</span> : <p className="body">Enter your password to open your letters.</p>}
            <input className="input" type="password" autoComplete="current-password" placeholder="Your password"
                   aria-label="Password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <Button block variant={faceId ? "outline" : "primary"} busy={busy && !!password} type="submit" disabled={!password}>Unlock</Button>
            <Button variant="ghost" onClick={forgot}>Forgot password?</Button>
          </form>
        ) : null}
        <Notice>{error}</Notice>
        <Notice kind="ok">{info}</Notice>
      </div>
      <div className="grow" />
      <Button variant="ghost" onClick={() => void signOut()}>Not you? Sign out</Button>
    </main>
  );
}

/* ── "Confirm it's you" (server said REAUTH_REQUIRED) ───────────────────── */

export function ReauthDialog() {
  const { reauth, canUseFaceId } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const faceId = prefs.faceId && canUseFaceId;

  useEffect(() => setError(undefined), [reauth]);
  if (!reauth) return null;

  async function confirm() {
    setError(undefined);
    setBusy(true);
    try {
      await reauthWithFaceId();
      reauth?.resolve(true);
    } catch (e) {
      if (!cancelled(e)) setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="overlay center">
      <section className="dialog stack" role="alertdialog" aria-modal="true" aria-labelledby="reauth-title">
        <FaceIdGlyph />
        <h2 id="reauth-title" className="title">Confirm it's you</h2>
        <p className="small">This one needs a quick check{faceId ? ` with ${biometricName}` : ""} first.</p>
        <Notice>{error}</Notice>
        {faceId ? (
          <Button block busy={busy} onClick={confirm}>Use {biometricName}</Button>
        ) : (
          <Button block onClick={() => { reauth.resolve(false); reauthWithGoogle(); }}>Sign in with Google again</Button>
        )}
        <Button variant="ghost" block onClick={() => reauth.resolve(false)}>Cancel</Button>
      </section>
    </div>
  );
}
