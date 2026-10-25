/**
 * Tiny local flags — never anything secret (no tokens, no alias, no email).
 * Browser storage can be cleared or blocked (private mode), so every access is guarded.
 */
const PREFIX = "confess.";

function get(key: string): string | null {
  try {
    return localStorage.getItem(PREFIX + key);
  } catch {
    return null;
  }
}

function set(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(PREFIX + key);
    else localStorage.setItem(PREFIX + key, value);
  } catch {
    /* storage unavailable */
  }
}

export const prefs = {
  /** This device has Face ID set up for Confess → offer "Continue with Face ID" when signed out. */
  get faceId(): boolean {
    return get("faceid") === "1";
  },
  set faceId(on: boolean) {
    set("faceid", on ? "1" : null);
  },
  /** User said "Not now" to the Face ID offer. */
  get faceIdDeclined(): boolean {
    return get("faceid_declined") === "1";
  },
  set faceIdDeclined(on: boolean) {
    set("faceid_declined", on ? "1" : null);
  },
  /** Dismissed the "Add to Home Screen" guide. */
  get installDismissed(): boolean {
    return get("install_dismissed") === "1";
  },
  set installDismissed(on: boolean) {
    set("install_dismissed", on ? "1" : null);
  },
};
