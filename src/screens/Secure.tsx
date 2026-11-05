/**
 * S6 · Unlock (UnlockActivity + activity_unlock.xml), with Face ID where the Android app has the
 * fingerprint; plus two web-only pieces drawn in the same set: the Face ID offer after sign-up
 * and "Confirm it's you" (the server asked for a fresh check).
 */
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError, api } from "../lib/api.ts";
import { biometricName } from "../lib/device.ts";
import { errorMessage, reauthWithFaceId, reauthWithGoogle, useSession } from "../lib/session.tsx";
import { prefs } from "../lib/storage.ts";
import { S } from "../lib/strings.ts";
import { FaceIdError } from "../lib/webauthn.ts";
import { Aura } from "../components/Aura.tsx";
import { useToast } from "../components/toast.tsx";
import { Art, overshoot } from "../ui/Art.tsx";
import { Icon } from "../ui/Icon.tsx";
import { Btn, ConfessDialog, haptic, Tag, TextField } from "../ui/kit.tsx";
import { tween } from "../ui/motion.ts";

const cancelled = (e: unknown) => e instanceof FaceIdError && e.kind === "cancelled";
const MAX_TRIES = 5;
const COOLDOWN_S = 30;

/** Face ID's own glyph (the app's fingerprint icon, on iPhone). */
export function FaceIdGlyph({ size = 24 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" aria-hidden>
      <path d="M3 7.5V6a3 3 0 0 1 3-3h1.5M16.5 3H18a3 3 0 0 1 3 3v1.5M21 16.5V18a3 3 0 0 1-3 3h-1.5M7.5 21H6a3 3 0 0 1-3-3v-1.5" />
      <path d="M8.5 9v1.5M15.5 9v1.5M12 9v4.5h-1M9 16c1.7 1.3 4.3 1.3 6 0" />
    </svg>
  );
}

/** TextClock in a Confess.Tag. */
function ClockTag() {
  const fmt = () => new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(new Date()).toUpperCase();
  const [now, setNow] = useState(fmt);
  useEffect(() => { const t = window.setInterval(() => setNow(fmt()), 10_000); return () => window.clearInterval(t); }, []);
  return <span className="k-tag t-meta-small unlock-clock">{now}</span>;
}

/* ── S6 · Unlock ────────────────────────────────────────────────────────── */

export function LockScreen() {
  const { me, unlockWithFaceId, unlockWithPassword, finishUnlock, signOut, canUseFaceId } = useSession();
  const toast = useToast();
  const bio = prefs.faceId && canUseFaceId;
  const hasPassword = !!me?.hasPassword;
  const [password, setPassword] = useState("");
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const [pulsing, setPulsing] = useState(false);
  const [forgot, setForgot] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const tries = useRef(0);
  const field = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const avatar = useRef<HTMLDivElement>(null);
  const badge = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!cooldown) return;
    const t = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    if (cooldown > 0) setError(S("unlock_cooldown", cooldown));
    return () => window.clearTimeout(t);
  }, [cooldown]);
  useEffect(() => { if (cooldown === 0) setError((e) => (e?.startsWith("Too many") ? null : e)); }, [cooldown]);

  /** Check pops onto the avatar, then the app opens. */
  const showUnlocked = async () => {
    setLeaving(true);
    setPulsing(false);
    if (badge.current) badge.current.hidden = false;
    void tween(badge.current, { scale: [0, 1] }, 260, { ease: overshoot(2.2) });
    void tween(avatar.current, { scale: [1, 1.06] }, 150).then(() => tween(avatar.current, { scale: [1.06, 1] }, 170));
    await new Promise((r) => window.setTimeout(r, 520));
    finishUnlock();
  };

  const shake = () => field.current?.animate(
    [{ translate: "0" }, { translate: "18px" }, { translate: "-14px" }, { translate: "8px" }, { translate: "0" }], { duration: 200 });

  async function verify(ev?: FormEvent) {
    ev?.preventDefault();
    if (working || cooldown || !password) return;
    setWorking(true);
    setError(null);
    try {
      const ok = await unlockWithPassword(password, false);
      if (ok) {
        haptic();
        await showUnlocked();
        return;
      }
      tries.current += 1;
      haptic();
      shake();
      setPassword("");
      const left = MAX_TRIES - tries.current;
      if (left <= 0) { tries.current = 0; setCooldown(COOLDOWN_S); }
      else setError(left <= 2 ? S("unlock_wrong_left", left) : S("unlock_wrong"));
    } catch (e) {
      if (e instanceof ApiError && (e.code === "RATE_LIMITED" || e.code === "ACCOUNT_LOCKED")) setCooldown(COOLDOWN_S);
      else { haptic(); setError(errorMessage(e)); }
    } finally {
      setWorking(false);
    }
  }

  async function face() {
    if (leaving) return;
    setError(null);
    setPulsing(true);
    try {
      await unlockWithFaceId(false);
      haptic();
      await showUnlocked();
    } catch (e) {
      setPulsing(false);
      if (!cancelled(e)) setError(errorMessage(e));
      input.current?.focus();
    }
  }

  /** "Forgot password?": with Face ID on, prove it's you first, then the reset popup. */
  async function confirmForgot() {
    if (!bio) { setForgot(true); return; }
    try {
      await reauthWithFaceId();
      setForgot(true);
    } catch (e) {
      if (!cancelled(e)) { haptic(); toast(S("forgot_verify_failed")); }
    }
  }

  async function sendReset() {
    setForgot(false);
    try {
      await api("/auth/forgot-password", { method: "POST" });
      toast(S("forgot_sent"));
    } catch (e) {
      toast(errorMessage(e));
    }
  }

  const [signOutAsk, setSignOutAsk] = useState(false);

  return (
    <main className="a-screen unlock">
      <div className="unlock-top">
        <span className="k-tag t-meta-small"><Icon name="lock_small" size={12} />{S("unlock_tag")}</span>
        <ClockTag />
      </div>
      <span className="unlock-space" />
      <div className="unlock-who">
        <span className={pulsing ? "unlock-pulse on" : "unlock-pulse"} aria-hidden />
        <div className="unlock-avatar" ref={avatar}><Aura value={me?.avatar} size={86} /></div>
        <span className="unlock-badge" ref={badge} hidden aria-label={S("unlock_cd_unlocked")}><Icon name="check" size={22} /></span>
      </div>
      <h1 className="t-headline unlock-welcome">{S("unlock_welcome")}</h1>
      <p className="t-headline unlock-alias">{me?.alias}</p>
      <p className="t-body unlock-sub">
        {hasPassword ? S("unlock_subtitle") : `Use ${biometricName} to open your letters.`}
      </p>

      <div className="unlock-plate">
        {hasPassword ? (
          <form onSubmit={(e) => void verify(e)}>
            <span className="t-meta-small unlock-label">{S("unlock_label")}</span>
            <div ref={field} className="unlock-field">
              <TextField password inputRef={input} value={password} placeholder={S("unlock_hint")} aria-label={S("unlock_label")}
                         maxLength={64} autoComplete="current-password" enterKeyHint="go" disabled={working || leaving}
                         error={error} onChange={(e) => { setPassword(e.target.value); if (!cooldown) setError(null); }} />
            </div>
            <Btn block type="submit" icon="arrow_right" disabled={!password || working || cooldown > 0 || leaving}>
              {working ? S("unlock_checking") : S("unlock_button")}
            </Btn>
          </form>
        ) : null}
        {bio ? (
          <>
            {hasPassword ? (
              <div className="unlock-or"><i /><span className="t-meta">{S("unlock_or")}</span><i /></div>
            ) : null}
            <Btn block kind={hasPassword ? "outlined" : "primary"} icon={<FaceIdGlyph />} disabled={leaving}
                 onClick={() => void face()}>{`Use ${biometricName}`}</Btn>
            {!hasPassword && error ? <p className="t-meta unlock-error" role="alert">{error}</p> : null}
          </>
        ) : null}
        <div className="unlock-links">
          {hasPassword ? <Btn kind="ghost" onClick={() => void confirmForgot()}>{S("unlock_forgot")}</Btn> : <span />}
          <Btn kind="ghost" className="unlock-signout" onClick={() => setSignOutAsk(true)}>{S("unlock_sign_out")}</Btn>
        </div>
      </div>
      <p className="t-meta unlock-note"><Icon name="lock_small" size={12} />{S("unlock_relock_note")}</p>
      <span className="unlock-space min" />

      <ConfessDialog open={forgot} icon="mail_open" title={S("forgot_title")} body={S("forgot_body")}
                     primary={S("forgot_send")} onPrimary={() => void sendReset()} onClose={() => setForgot(false)} />
      <ConfessDialog open={signOutAsk} icon="log_out" title={S("signout_title")} body={S("signout_body")}
                     primary={S("signout_confirm")} danger onPrimary={() => { setSignOutAsk(false); void signOut(); }}
                     onClose={() => setSignOutAsk(false)} />
    </main>
  );
}

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
      haptic();
    } catch (e) {
      if (!cancelled(e)) setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <main className="a-screen faceid">
      <span className="grow" />
      <div className="faceid-disc"><FaceIdGlyph size={40} /></div>
      <span className="faceid-tag"><Tag>One more thing</Tag></span>
      <h1 className="t-headline faceid-title">Unlock Confess with {biometricName}</h1>
      <p className="t-body-medium faceid-body">
        Next time, just look at your phone. Your face never leaves it — Confess only ever gets a yes.
      </p>
      {error ? <p className="t-meta faceid-error" role="alert">{error}</p> : null}
      <span className="grow" />
      <Btn block busy={busy} onClick={() => void turnOn()}>{`Turn on ${biometricName}`}</Btn>
      <Btn kind="ghost" block onClick={skipFaceId} className="faceid-skip">Not now</Btn>
      <span className="faceid-letty" aria-hidden><Art name="letty_pose_wink" size={64} /></span>
    </main>
  );
}

/* ── "Confirm it's you" (server said REAUTH_REQUIRED) ───────────────────── */

export function ReauthDialog() {
  const { reauth, canUseFaceId } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const faceId = prefs.faceId && canUseFaceId;

  useEffect(() => setError(null), [reauth]);

  async function confirm() {
    if (!reauth) return;
    if (!faceId) { reauth.resolve(false); reauthWithGoogle(); return; }
    setError(null);
    setBusy(true);
    try {
      await reauthWithFaceId();
      reauth.resolve(true);
    } catch (e) {
      if (!cancelled(e)) setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ConfessDialog open={!!reauth} icon="shield" title={S("bio_prompt_title")}
                   body={`This one needs a quick check${faceId ? ` with ${biometricName}` : ""} first.`}
                   primary={faceId ? `Use ${biometricName}` : "Sign in with Google again"} busy={busy} error={error}
                   onPrimary={() => void confirm()} onClose={() => reauth?.resolve(false)} />
  );
}
