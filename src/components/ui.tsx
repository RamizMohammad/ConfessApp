import type { ButtonHTMLAttributes, ReactNode } from "react";
import { createPortal } from "react-dom";
import googleG from "../assets/google-g.svg";
import lockup from "../assets/lockup.svg";
import letty from "../assets/letty.svg";

export function Lockup({ className = "lockup" }: { className?: string }) {
  return <img className={className} src={lockup} alt="Confess" draggable={false} />;
}

export function Letty({ size = 64 }: { size?: number }) {
  return <img src={letty} width={size} height={(size * 58) / 74} alt="" aria-hidden draggable={false} />;
}

type Variant = "primary" | "paper" | "outline" | "ghost" | "danger";

export function Button({ variant = "primary", busy, block, children, className = "", ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean; block?: boolean }) {
  return (
    <button type="button" {...rest} disabled={rest.disabled || busy} aria-busy={busy || undefined}
            className={`btn btn-${variant}${block ? " block" : ""} ${className}`}>
      {busy ? <span className="spinner" aria-hidden /> : null}
      {children}
    </button>
  );
}

/** Google's own button mark (design rule: third-party sign-in uses the real logo). */
export function GoogleButton({ busy, onClick, label = "Continue with Google" }: { busy?: boolean; onClick: () => void; label?: string }) {
  return (
    <Button variant="paper" block busy={busy} onClick={onClick}>
      {busy ? null : <img src={googleG} alt="" aria-hidden />}
      {busy ? "Opening the door…" : label}
    </Button>
  );
}

export function FaceIdGlyph({ className = "faceid-glyph" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" aria-hidden>
      <path d="M8 20V14a6 6 0 0 1 6-6h6M44 8h6a6 6 0 0 1 6 6v6M56 44v6a6 6 0 0 1-6 6h-6M20 56h-6a6 6 0 0 1-6-6v-6" />
      <path d="M22 24v4M42 24v4M32 24v12h-3M24 42c4.5 4 11.5 4 16 0" />
    </svg>
  );
}

export function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M15 5l-7 7 7 7" />
    </svg>
  );
}

export function TopBar({ onBack, right, step, total }: { onBack?: () => void; right?: ReactNode; step?: number; total?: number }) {
  return (
    <div className="topbar">
      {onBack ? (
        <button className="icon-btn" onClick={onBack} aria-label="Back"><BackIcon /></button>
      ) : <span className="spacer" />}
      {step && total ? (
        <div className="steps" aria-label={`Step ${step} of ${total}`}>
          {Array.from({ length: total }, (_, i) => <i key={i} className={i < step ? "on" : ""} />)}
        </div>
      ) : <Lockup />}
      {right ?? <span className="spacer" />}
    </div>
  );
}

export function Notice({ kind = "error", children }: { kind?: "error" | "info" | "ok"; children: ReactNode }) {
  if (!children) return null;
  return <div className={`notice ${kind}`} role={kind === "error" ? "alert" : "status"}>{children}</div>;
}

/** Sheets and dialogs render at the top of the page, never inside a card or an animated screen. */
export function Portal({ children }: { children: ReactNode }) {
  return createPortal(children, document.body);
}
