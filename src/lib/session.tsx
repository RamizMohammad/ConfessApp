/**
 * Who's signed in, and what the app should show. One provider, used by every screen.
 *
 *   booting     → checking the cookie session (or finishing a Google redirect)
 *   signedOut   → welcome screen ("Continue with Face ID" when this device has it)
 *   onboarding  → new Google account: guidelines → alias → aura (ID token kept in memory only)
 *   faceIdOffer → just signed up / in: "Unlock Confess with Face ID"
 *   locked      → session is fine but the app was closed / in the background > 5 min
 *   ready       → inside Confess
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { config } from "../config.ts";
import { ApiError, api, refreshSession, setApiHooks, type SessionInfo } from "./api.ts";
import { finishGoogleSignIn, isGoogleCallback, startGoogleSignIn } from "./google.ts";
import { prefs } from "./storage.ts";
import { turnstileToken } from "./turnstile.ts";
import { createCredential, faceIdAvailable, FaceIdError, getCredential } from "./webauthn.ts";

export interface Me {
  id: string;
  alias: string;
  about: string;
  avatar: string;
  banner: string;
  hasPassword: boolean;
  createdAt: string;
  postCount: number;
  followers: number;
  following: number;
}

export interface Draft {
  alias: string;
  avatar: string;
  about: string;
}

export type Phase =
  | { name: "booting" }
  | { name: "signedOut"; notice?: string }
  | { name: "onboarding"; step: "guidelines" | "alias" | "avatar" }
  | { name: "faceIdOffer" }
  | { name: "locked" }
  | { name: "ready" };

interface AuthPayload extends SessionInfo {
  user: Me;
}

interface SessionApi {
  phase: Phase;
  me: Me | null;
  draft: Draft;
  setDraft: (d: Partial<Draft>) => void;
  goTo: (step: "guidelines" | "alias" | "avatar") => void;
  /** Leaves the page for Google. */
  signInWithGoogle: () => void;
  signInWithFaceId: () => Promise<void>;
  register: () => Promise<void>;
  enableFaceId: () => Promise<void>;
  skipFaceId: () => void;
  unlockWithFaceId: () => Promise<void>;
  unlockWithPassword: (password: string) => Promise<boolean>;
  signOut: () => Promise<void>;
  cancelOnboarding: () => void;
  setMe: (m: Me) => void;
  /** Open while the server wants a fresh Face ID / Google check. */
  reauth: { open: boolean; resolve: (ok: boolean) => void } | null;
  canUseFaceId: boolean;
}

const Ctx = createContext<SessionApi | null>(null);

export function useSession(): SessionApi {
  const v = useContext(Ctx);
  if (!v) throw new Error("useSession outside SessionProvider");
  return v;
}

export function errorMessage(e: unknown): string {
  if (e instanceof FaceIdError) return e.message;
  if (e instanceof ApiError) {
    switch (e.code) {
      case "OFFLINE": return e.message;
      case "RATE_LIMITED": return "That's a lot of tries. Wait a minute and try again.";
      case "CHALLENGE_FAILED": return "We couldn't confirm you're a person. Try again.";
      case "BLOCKED": return "Access is paused for a while. Try again later.";
      case "ALIAS_TAKEN": return "Someone just took that alias. Pick another one.";
      case "ALIAS_NOT_ALLOWED": return "That alias isn't allowed. Pick another one.";
      case "ACCOUNT_EXISTS": return "You already have an account. Sign in instead.";
      case "CONTENT_FLAGGED": return "That line about you breaks our guidelines. Try something else.";
      default: return e.message;
    }
  }
  return "Something went wrong. Try again.";
}

const EMPTY_DRAFT: Draft = { alias: "", avatar: "aura_04", about: "" };

export function SessionProvider({ children }: { children: ReactNode }) {
  const [phase, setPhase] = useState<Phase>({ name: "booting" });
  const [me, setMe] = useState<Me | null>(null);
  const [draft, setDraftState] = useState<Draft>(EMPTY_DRAFT);
  const [reauth, setReauth] = useState<SessionApi["reauth"]>(null);
  const [canUseFaceId, setCanUseFaceId] = useState(false);

  // The Google ID token for onboarding lives only in memory (lost on reload, by design).
  const idToken = useRef<string | null>(null);
  const refreshTimer = useRef<number | undefined>(undefined);
  const refreshDueAt = useRef<number>(0);
  const hiddenAt = useRef<number | null>(null);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;
  const meRef = useRef(me);
  meRef.current = me;

  const lockable = useCallback((m: Me | null) => prefs.faceId || !!m?.hasPassword, []);

  // ── Session upkeep ────────────────────────────────────────────────────────
  const scheduleRefresh = useCallback((info: SessionInfo) => {
    window.clearTimeout(refreshTimer.current);
    const ms = Math.max(30, info.expiresIn - 60) * 1000;
    refreshDueAt.current = Date.now() + ms;
    refreshTimer.current = window.setTimeout(() => void refreshSession(), ms);
  }, []);

  const toSignedOut = useCallback((notice?: string) => {
    window.clearTimeout(refreshTimer.current);
    idToken.current = null;
    setMe(null);
    setDraftState(EMPTY_DRAFT);
    setPhase({ name: "signedOut", notice });
  }, []);

  const afterSignIn = useCallback(async (p: AuthPayload) => {
    scheduleRefresh(p);
    setMe(p.user);
    idToken.current = null;
    const offer = !prefs.faceId && !prefs.faceIdDeclined && (await faceIdAvailable());
    setPhase(offer ? { name: "faceIdOffer" } : { name: "ready" });
  }, [scheduleRefresh]);

  useEffect(() => {
    setApiHooks({
      onSession: scheduleRefresh,
      onSignedOut: () => {
        if (phaseRef.current.name !== "signedOut" && phaseRef.current.name !== "onboarding") {
          toSignedOut("You were signed out. Sign in again to continue.");
        }
      },
      onReauthRequired: () => new Promise<boolean>((resolve) => {
        setReauth({ open: true, resolve: (ok) => { setReauth(null); resolve(ok); } });
      }),
    });
  }, [scheduleRefresh, toSignedOut]);

  // ── Boot ──────────────────────────────────────────────────────────────────
  const loginWithIdToken = useCallback(async (token: string) => {
    try {
      const ts = await turnstileToken().catch(() => undefined);
      const p = await api<AuthPayload>("/auth/web/login", { method: "POST", body: { idToken: token, turnstileToken: ts }, raw: true });
      await afterSignIn(p);
    } catch (e) {
      if (e instanceof ApiError && e.code === "NOT_REGISTERED") {
        idToken.current = token;
        setDraftState(EMPTY_DRAFT);
        setPhase({ name: "onboarding", step: "guidelines" });
        return;
      }
      toSignedOut(errorMessage(e));
    }
  }, [afterSignIn, toSignedOut]);

  useEffect(() => {
    let stop = false;
    void faceIdAvailable().then((ok) => !stop && setCanUseFaceId(ok));
    (async () => {
      if (isGoogleCallback()) {
        // Same path for first sign-in and for a Google re-check: either way a fresh session.
        const r = finishGoogleSignIn();
        if (r.kind === "token") return loginWithIdToken(r.idToken);
        return toSignedOut(r.kind === "cancelled" ? undefined : r.message);
      }
      try {
        const user = await api<Me>("/users/me");
        if (stop) return;
        setMe(user);
        void refreshSession();                         // fresh cookie + schedule from a known expiry
        setPhase(lockable(user) ? { name: "locked" } : { name: "ready" });
      } catch {
        if (!stop) toSignedOut();
      }
    })();
    return () => {
      stop = true;
    };
  }, [loginWithIdToken, lockable, toSignedOut]);

  // ── App lock + privacy veil ───────────────────────────────────────────────
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === "hidden") {
        hiddenAt.current = Date.now();
        document.documentElement.classList.add("veiled");      // app switcher shows no content
        return;
      }
      document.documentElement.classList.remove("veiled");
      const away = hiddenAt.current ? Date.now() - hiddenAt.current : 0;
      hiddenAt.current = null;
      if (phaseRef.current.name === "ready" && away >= config.relockAfterMs && lockable(meRef.current)) {
        setPhase({ name: "locked" });
      }
      if (meRef.current && Date.now() >= refreshDueAt.current) void refreshSession();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [lockable]);

  // ── Actions ───────────────────────────────────────────────────────────────
  const signInWithGoogle = useCallback(() => startGoogleSignIn("signin"), []);

  const signInWithFaceId = useCallback(async () => {
    const { publicKey } = await api<{ publicKey: Record<string, unknown> }>("/auth/passkey/login/options", { method: "POST", raw: true });
    const credential = await getCredential(publicKey);
    try {
      const p = await api<AuthPayload>("/auth/passkey/login", { method: "POST", body: { credential }, raw: true });
      prefs.faceId = true;
      scheduleRefresh(p);
      setMe(p.user);
      setPhase({ name: "ready" });
    } catch (e) {
      if (e instanceof ApiError && e.code === "PASSKEY_FAILED" && /Google/.test(e.message)) prefs.faceId = false;
      throw e;
    }
  }, [scheduleRefresh]);

  const register = useCallback(async () => {
    if (!idToken.current) {
      toSignedOut("Your sign-in timed out. Please continue with Google again.");
      return;
    }
    const ts = await turnstileToken().catch(() => undefined);
    const body = { idToken: idToken.current, turnstileToken: ts, alias: draft.alias.trim(), avatar: draft.avatar,
                   about: draft.about.trim() };
    try {
      const p = await api<AuthPayload>("/auth/web/register", { method: "POST", body, raw: true });
      setDraftState(EMPTY_DRAFT);
      await afterSignIn(p);
    } catch (e) {
      if (e instanceof ApiError && (e.code === "UNAUTHORIZED" || e.status === 401)) {
        toSignedOut("Your sign-in timed out. Please continue with Google again.");
        return;
      }
      if (e instanceof ApiError && (e.code === "ALIAS_TAKEN" || e.code === "ALIAS_NOT_ALLOWED")) {
        setPhase({ name: "onboarding", step: "alias" });
      }
      throw e;
    }
  }, [draft, afterSignIn, toSignedOut]);

  const enableFaceId = useCallback(async () => {
    const { publicKey } = await api<{ publicKey: Record<string, unknown> }>("/auth/passkey/register/options", { method: "POST" });
    const credential = await createCredential(publicKey);
    await api("/auth/passkey/register", { method: "POST", body: { credential } });
    prefs.faceId = true;
    prefs.faceIdDeclined = false;
    if (phaseRef.current.name === "faceIdOffer") setPhase({ name: "ready" });
  }, []);

  const skipFaceId = useCallback(() => {
    prefs.faceIdDeclined = true;
    setPhase({ name: "ready" });
  }, []);

  const unlockWithFaceId = useCallback(async () => {
    try {
      const { publicKey } = await api<{ publicKey: Record<string, unknown> }>("/auth/passkey/verify-me/options", { method: "POST" });
      const credential = await getCredential(publicKey);
      await api("/auth/passkey/verify-me", { method: "POST", body: { credential } });
      setPhase({ name: "ready" });
    } catch (e) {
      if (e instanceof ApiError && e.code === "NOT_FOUND") {
        // Face ID was turned off from another device.
        prefs.faceId = false;
        if (!meRef.current?.hasPassword) setPhase({ name: "ready" });
      }
      throw e;
    }
  }, []);

  const unlockWithPassword = useCallback(async (password: string) => {
    const { match } = await api<{ match: boolean }>("/users/me/password/verify", { method: "POST", body: { password } });
    if (match) setPhase({ name: "ready" });
    return match;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await api("/auth/web/logout", { method: "POST", raw: true });
    } catch {
      /* cookies are cleared server-side; signed out locally regardless */
    }
    toSignedOut();
  }, [toSignedOut]);

  const value = useMemo<SessionApi>(() => ({
    phase,
    me,
    draft,
    setDraft: (d) => setDraftState((cur) => ({ ...cur, ...d })),
    goTo: (step) => setPhase({ name: "onboarding", step }),
    signInWithGoogle,
    signInWithFaceId,
    register,
    enableFaceId,
    skipFaceId,
    unlockWithFaceId,
    unlockWithPassword,
    signOut,
    cancelOnboarding: () => toSignedOut(),
    setMe,
    reauth,
    canUseFaceId,
  }), [phase, me, draft, signInWithGoogle, signInWithFaceId, register, enableFaceId, skipFaceId, unlockWithFaceId,
       unlockWithPassword, signOut, toSignedOut, reauth, canUseFaceId]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

/** Re-check for sensitive actions when the server says REAUTH_REQUIRED. */
export async function reauthWithFaceId(): Promise<void> {
  const { publicKey } = await api<{ publicKey: Record<string, unknown> }>("/auth/passkey/verify-me/options", { method: "POST", raw: true });
  const credential = await getCredential(publicKey);
  await api("/auth/passkey/verify-me", { method: "POST", body: { credential }, raw: true });
}

export function reauthWithGoogle(): void {
  startGoogleSignIn("reauth");
}
