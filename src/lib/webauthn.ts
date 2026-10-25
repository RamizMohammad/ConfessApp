/**
 * Face ID / Touch ID via passkeys. The UI never says "passkey".
 *
 * The server sends WebAuthn options as JSON (binary fields base64url); we turn them into
 * what navigator.credentials wants, and the result back into JSON for the server.
 * Done by hand rather than with parse*FromJSON()/toJSON() so older iOS versions work too.
 */
import { fromBase64url, toBase64url } from "./base64url.ts";

type Json = Record<string, unknown>;

export class FaceIdError extends Error {
  /** "cancelled" — user dismissed / timed out; "unsupported"; "failed". */
  readonly kind: "cancelled" | "unsupported" | "failed";
  constructor(kind: FaceIdError["kind"], message: string) {
    super(message);
    this.name = "FaceIdError";
    this.kind = kind;
  }
}

export function faceIdSupported(): boolean {
  return typeof window !== "undefined" && "PublicKeyCredential" in window && !!navigator.credentials;
}

/** True when this device has Face ID / Touch ID / fingerprint that websites can use. */
export async function faceIdAvailable(): Promise<boolean> {
  if (!faceIdSupported()) return false;
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch {
    return false;
  }
}

function descriptors(list: unknown): PublicKeyCredentialDescriptor[] | undefined {
  if (!Array.isArray(list)) return undefined;
  return list.map((d: Json) => ({
    type: "public-key" as const,
    id: fromBase64url(String(d.id)),
    transports: Array.isArray(d.transports) ? (d.transports as AuthenticatorTransport[]) : undefined,
  }));
}

export function creationOptions(o: Json): PublicKeyCredentialCreationOptions {
  const user = o.user as Json;
  return {
    ...(o as unknown as PublicKeyCredentialCreationOptions),
    challenge: fromBase64url(String(o.challenge)),
    user: { id: fromBase64url(String(user.id)), name: String(user.name), displayName: String(user.displayName) },
    excludeCredentials: descriptors(o.excludeCredentials),
  };
}

export function requestOptions(o: Json): PublicKeyCredentialRequestOptions {
  return {
    ...(o as unknown as PublicKeyCredentialRequestOptions),
    challenge: fromBase64url(String(o.challenge)),
    allowCredentials: descriptors(o.allowCredentials),
  };
}

export function credentialJson(cred: PublicKeyCredential): Json {
  const r = cred.response as AuthenticatorAttestationResponse & AuthenticatorAssertionResponse;
  const response: Json = { clientDataJSON: toBase64url(r.clientDataJSON) };
  if ("attestationObject" in r && r.attestationObject) {
    response.attestationObject = toBase64url(r.attestationObject);
    response.transports = typeof r.getTransports === "function" ? r.getTransports() : [];
  } else {
    response.authenticatorData = toBase64url(r.authenticatorData);
    response.signature = toBase64url(r.signature);
    if (r.userHandle) response.userHandle = toBase64url(r.userHandle);
  }
  return {
    id: cred.id,
    rawId: toBase64url(cred.rawId),
    type: cred.type,
    response,
    clientExtensionResults: cred.getClientExtensionResults?.() ?? {},
    authenticatorAttachment: cred.authenticatorAttachment ?? undefined,
  };
}

function translate(e: unknown): FaceIdError {
  const name = (e as DOMException)?.name;
  if (name === "NotAllowedError" || name === "AbortError") return new FaceIdError("cancelled", "Face ID was cancelled.");
  if (name === "InvalidStateError") return new FaceIdError("failed", "Face ID is already on for this device.");
  if (name === "NotSupportedError" || name === "SecurityError") {
    return new FaceIdError("unsupported", "Face ID isn't available here.");
  }
  return new FaceIdError("failed", "Face ID didn't work. Try again.");
}

export async function createCredential(options: Json): Promise<Json> {
  if (!faceIdSupported()) throw new FaceIdError("unsupported", "Face ID isn't available here.");
  try {
    const cred = (await navigator.credentials.create({ publicKey: creationOptions(options) })) as PublicKeyCredential | null;
    if (!cred) throw new FaceIdError("cancelled", "Face ID was cancelled.");
    return credentialJson(cred);
  } catch (e) {
    throw e instanceof FaceIdError ? e : translate(e);
  }
}

export async function getCredential(options: Json): Promise<Json> {
  if (!faceIdSupported()) throw new FaceIdError("unsupported", "Face ID isn't available here.");
  try {
    const cred = (await navigator.credentials.get({ publicKey: requestOptions(options) })) as PublicKeyCredential | null;
    if (!cred) throw new FaceIdError("cancelled", "Face ID was cancelled.");
    return credentialJson(cred);
  } catch (e) {
    throw e instanceof FaceIdError ? e : translate(e);
  }
}
