/** What device / mode we're running in. Used for wording and the install guide only — never for security. */

const ua = typeof navigator !== "undefined" ? navigator.userAgent : "";

export const isIOS = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && typeof navigator !== "undefined" && navigator.maxTouchPoints > 1);
export const isAndroid = /Android/.test(ua);
export const isMac = /Macintosh/.test(ua) && !isIOS;

/** Opened from the home-screen icon (not a browser tab). */
export function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  return window.matchMedia?.("(display-mode: standalone)").matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

/** iOS only lets home-screen apps use push etc.; Safari's own UI needs the "Add to Home Screen" step. */
export const isIOSSafari = isIOS && /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS|OPiOS|GSA\//.test(ua);

/** What to call the biometric on this device. */
export const biometricName: string = /iPhone/.test(ua) || (isIOS && !/iPad/.test(ua))
  ? "Face ID"
  : isIOS || isMac
    ? "Touch ID"
    : isAndroid
      ? "fingerprint"
      : "Face ID";
